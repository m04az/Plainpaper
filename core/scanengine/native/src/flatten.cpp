#include "cancel.hpp"
#include "params.hpp"
#include "stages.hpp"

#include <algorithm>
#include <cmath>
#include <opencv2/imgproc.hpp>
#include <vector>

// Single-axis curl flattening (research R7): text lines are extracted, each centreline is fitted
// with a quadratic, the curves are turned into a vertical displacement field (interpolated between
// lines, flat beyond the first and last) and the page is remapped so every line becomes straight.
// Applied only when enough lines agree on the curl direction and the residual curvature drops.
namespace pp {

namespace {

struct LineFit {
    double a = 0; // curvature term of y = a x^2 + b x + c with x normalised to [-1, 1]
    double b = 0;
    double c = 0;
    double meanY = 0;
    double width = 0;
    std::vector<cv::Point2d> samples; // centreline samples, x relative to the page centre
};

cv::Mat inkMask(const cv::Mat& gray) {
    cv::Mat ink;
    cv::adaptiveThreshold(gray, ink, 255, cv::ADAPTIVE_THRESH_MEAN_C, cv::THRESH_BINARY_INV,
                          params::kInkBlockSize, params::kInkOffset);
    cv::morphologyEx(ink, ink, cv::MORPH_OPEN,
                     cv::getStructuringElement(cv::MORPH_RECT, cv::Size(2, 2)));
    return ink;
}

/** Least-squares quadratic through (x, y) samples with x normalised to [-1, 1]. */
bool fitQuadratic(const std::vector<cv::Point2d>& pts, double halfWidth, LineFit& fit) {
    if (pts.size() < 6) {
        return false;
    }
    cv::Mat A(static_cast<int>(pts.size()), 3, CV_64F);
    cv::Mat y(static_cast<int>(pts.size()), 1, CV_64F);
    for (size_t i = 0; i < pts.size(); ++i) {
        const double x = pts[i].x / halfWidth;
        A.at<double>(static_cast<int>(i), 0) = x * x;
        A.at<double>(static_cast<int>(i), 1) = x;
        A.at<double>(static_cast<int>(i), 2) = 1.0;
        y.at<double>(static_cast<int>(i), 0) = pts[i].y;
    }
    cv::Mat coeffs;
    if (!cv::solve(A, y, coeffs, cv::DECOMP_SVD)) {
        return false;
    }
    fit.a = coeffs.at<double>(0, 0);
    fit.b = coeffs.at<double>(1, 0);
    fit.c = coeffs.at<double>(2, 0);
    double sum = 0;
    for (const auto& p : pts) {
        sum += p.y;
    }
    fit.meanY = sum / pts.size();
    return true;
}

/** Text-line centrelines from a binary ink image (working resolution). */
std::vector<LineFit> fitLines(const cv::Mat& ink) {
    std::vector<LineFit> fits;
    const int kernelWidth = std::max(5, ink.cols / params::kLineCloseDivisor);
    cv::Mat smeared;
    cv::morphologyEx(ink, smeared, cv::MORPH_CLOSE,
                     cv::getStructuringElement(cv::MORPH_RECT, cv::Size(kernelWidth, 3)));
    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(smeared, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);
    const double halfWidth = ink.cols / 2.0;
    for (const auto& contour : contours) {
        const cv::Rect box = cv::boundingRect(contour);
        if (box.width < params::kMinLineWidthFraction * ink.cols || box.height < 4 ||
            box.height > ink.rows / 10) {
            continue;
        }
        std::vector<cv::Point2d> samples;
        const int columns = params::kLineSamples;
        for (int i = 0; i < columns; ++i) {
            const int x0 = box.x + box.width * i / columns;
            const int x1 = box.x + box.width * (i + 1) / columns;
            double sumY = 0;
            int count = 0;
            for (int y = box.y; y < box.y + box.height; ++y) {
                const uint8_t* row = ink.ptr<uint8_t>(y);
                for (int x = x0; x < x1; ++x) {
                    if (row[x] != 0) {
                        sumY += y;
                        ++count;
                    }
                }
            }
            if (count > 0) {
                samples.emplace_back((x0 + x1) / 2.0 - halfWidth, sumY / count);
            }
        }
        LineFit fit;
        if (fitQuadratic(samples, halfWidth, fit)) {
            fit.width = box.width;
            fit.samples = samples;
            fits.push_back(fit);
        }
    }
    std::sort(fits.begin(), fits.end(),
              [](const LineFit& l, const LineFit& r) { return l.meanY < r.meanY; });
    return fits;
}

double meanCurvature(const std::vector<LineFit>& fits) {
    if (fits.empty()) {
        return 0.0;
    }
    double sum = 0;
    for (const auto& f : fits) {
        sum += std::abs(f.a);
    }
    return sum / fits.size();
}

bool consistentSign(const std::vector<LineFit>& fits) {
    int positive = 0;
    int negative = 0;
    for (const auto& f : fits) {
        if (std::abs(f.a) < params::kMinCurvaturePx) {
            continue;
        }
        (f.a > 0 ? positive : negative)++;
    }
    const int total = positive + negative;
    if (total == 0) {
        return false;
    }
    return std::max(positive, negative) >= params::kCurlAgreement * total;
}

/**
 * Shared curl shape g(x) = p2 x^2 + p3 x^3 (x normalised to [-1, 1]) fitted to every line's
 * samples at once (each line keeps its own offset and slope), then one amplitude per line.
 * Single-axis curl bends every line the same way; pooling makes short lines usable.
 */
struct CurlModel {
    double p2 = 0;
    double p3 = 0;
    std::vector<double> amplitude; // per line, 1 = the pooled shape
    std::vector<double> lineY;

    double shape(double xn) const { return p2 * xn * xn + p3 * xn * xn * xn; }
};

bool fitCurlModel(const std::vector<std::vector<cv::Point2d>>& lines, double halfWidth,
                  CurlModel& model) {
    int rows = 0;
    for (const auto& line : lines) {
        rows += static_cast<int>(line.size());
    }
    const int n = static_cast<int>(lines.size());
    if (rows < 6 * n || n == 0) {
        return false;
    }
    cv::Mat A = cv::Mat::zeros(rows, 2 * n + 2, CV_64F);
    cv::Mat y(rows, 1, CV_64F);
    int r = 0;
    for (int i = 0; i < n; ++i) {
        for (const auto& p : lines[i]) {
            const double xn = p.x / halfWidth;
            A.at<double>(r, 0) = xn * xn;
            A.at<double>(r, 1) = xn * xn * xn;
            A.at<double>(r, 2 + 2 * i) = 1.0;
            A.at<double>(r, 3 + 2 * i) = xn;
            y.at<double>(r, 0) = p.y;
            ++r;
        }
    }
    cv::Mat coeffs;
    if (!cv::solve(A, y, coeffs, cv::DECOMP_SVD)) {
        return false;
    }
    model.p2 = coeffs.at<double>(0, 0);
    model.p3 = coeffs.at<double>(1, 0);
    model.amplitude.assign(n, 1.0);
    model.lineY.assign(n, 0.0);
    for (int i = 0; i < n; ++i) {
        const double c = coeffs.at<double>(2 + 2 * i, 0);
        const double b = coeffs.at<double>(3 + 2 * i, 0);
        model.lineY[i] = c;
        // Amplitude: least squares of this line's residual (after its offset and slope) on g(x).
        double num = 0;
        double den = 0;
        for (const auto& p : lines[i]) {
            const double xn = p.x / halfWidth;
            const double g = model.shape(xn);
            num += (p.y - c - b * xn) * g;
            den += g * g;
        }
        model.amplitude[i] = den > 1e-9 ? std::clamp(num / den, 0.0, 2.0) : 1.0;
    }
    return true;
}

/** Vertical displacement dy(x, y) (small resolution): shared shape, amplitude interpolated in y. */
cv::Mat displacementField(const CurlModel& model, const cv::Size& size) {
    cv::Mat field(size, CV_32F, cv::Scalar(0.f));
    const double halfWidth = size.width / 2.0;
    const size_t n = model.lineY.size();
    std::vector<double> shape(size.width);
    for (int x = 0; x < size.width; ++x) {
        shape[x] = model.shape((x - halfWidth) / halfWidth);
    }
    for (int y = 0; y < size.height; ++y) {
        size_t lower = 0;
        while (lower < n && model.lineY[lower] < y) {
            ++lower;
        }
        const size_t upper = lower == 0 ? 0 : lower - 1;
        const size_t lo = std::min(lower, n - 1);
        double t = 0.0;
        if (lo != upper && model.lineY[lo] > model.lineY[upper]) {
            t = std::clamp((y - model.lineY[upper]) / (model.lineY[lo] - model.lineY[upper]), 0.0,
                           1.0);
        }
        const double amplitude = model.amplitude[upper] * (1.0 - t) + model.amplitude[lo] * t;
        float* row = field.ptr<float>(y);
        for (int x = 0; x < size.width; ++x) {
            row[x] = static_cast<float>(amplitude * shape[x]);
        }
    }
    return field;
}

} // namespace

pp_step_status flattenPage(cv::Mat& page, volatile int32_t* cancel) {
    const double scale =
        std::min(1.0, static_cast<double>(params::kFlattenWidth) / std::max(page.cols, page.rows));
    cv::Mat gray, small;
    cv::cvtColor(page, gray, cv::COLOR_BGR2GRAY);
    if (scale < 1.0) {
        cv::resize(gray, small, cv::Size(), scale, scale, cv::INTER_AREA);
    } else {
        small = gray;
    }
    checkCancel(cancel);
    cv::Mat ink = inkMask(small);
    std::vector<LineFit> fits = fitLines(ink);
    if (static_cast<int>(fits.size()) < params::kMinCurlLines || !consistentSign(fits)) {
        return PP_STEP_SKIPPED_INSUFFICIENT_LINES;
    }
    const double before = meanCurvature(fits);
    if (before < params::kMinCurvaturePx) {
        return PP_STEP_SKIPPED_NOT_NEEDED;
    }
    checkCancel(cancel);

    std::vector<std::vector<cv::Point2d>> samples;
    for (const auto& f : fits) {
        samples.push_back(f.samples);
    }
    CurlModel model;
    if (!fitCurlModel(samples, small.cols / 2.0, model)) {
        return PP_STEP_SKIPPED_INSUFFICIENT_LINES;
    }
    cv::Mat fieldSmall = displacementField(model, small.size());
    cv::Mat field;
    cv::resize(fieldSmall, field, page.size(), 0, 0, cv::INTER_LINEAR);
    field *= static_cast<float>(1.0 / scale);
    cv::Mat mapX(page.size(), CV_32F), mapY(page.size(), CV_32F);
    for (int y = 0; y < page.rows; ++y) {
        float* mx = mapX.ptr<float>(y);
        float* my = mapY.ptr<float>(y);
        const float* d = field.ptr<float>(y);
        for (int x = 0; x < page.cols; ++x) {
            mx[x] = static_cast<float>(x);
            my[x] = static_cast<float>(y) + d[x];
        }
        if ((y & 255) == 0) {
            checkCancel(cancel);
        }
    }
    cv::Mat remapped;
    cv::remap(page, remapped, mapX, mapY, cv::INTER_CUBIC, cv::BORDER_REPLICATE);
    checkCancel(cancel);

    // Accept only when the curvature really dropped.
    cv::Mat grayAfter, smallAfter;
    cv::cvtColor(remapped, grayAfter, cv::COLOR_BGR2GRAY);
    if (scale < 1.0) {
        cv::resize(grayAfter, smallAfter, small.size(), 0, 0, cv::INTER_AREA);
    } else {
        smallAfter = grayAfter;
    }
    const double after = meanCurvature(fitLines(inkMask(smallAfter)));
    if (after > params::kCurlImprovement * before) {
        return PP_STEP_SKIPPED_NO_IMPROVEMENT;
    }
    page = remapped;
    return PP_STEP_APPLIED;
}

} // namespace pp
