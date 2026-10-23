#include "cancel.hpp"
#include "params.hpp"
#include "stages.hpp"

#include <cmath>
#include <opencv2/imgproc.hpp>
#include <vector>

// Residual tilt (research R5 step 4): the angle whose ink projection profile has the highest
// variance, coarse then fine, over +-kMaxDeskewDegrees.
namespace pp {

namespace {

cv::Mat inkMask(const cv::Mat& gray, int maxWidth) {
    cv::Mat small;
    const double scale = std::min(1.0, static_cast<double>(maxWidth) / gray.cols);
    if (scale < 1.0) {
        cv::resize(gray, small, cv::Size(), scale, scale, cv::INTER_AREA);
    } else {
        small = gray;
    }
    cv::Mat ink;
    cv::adaptiveThreshold(small, ink, 255, cv::ADAPTIVE_THRESH_MEAN_C, cv::THRESH_BINARY_INV,
                          params::kInkBlockSize, params::kInkOffset);
    return ink;
}

double profileVariance(const cv::Mat& ink, double degrees) {
    const double slope = std::tan(degrees * CV_PI / 180.0);
    const double cx = ink.cols / 2.0;
    std::vector<double> profile(ink.rows, 0.0);
    for (int y = 0; y < ink.rows; ++y) {
        int sum = 0;
        for (int x = 0; x < ink.cols; ++x) {
            const int yy = static_cast<int>(y + (x - cx) * slope);
            if (yy >= 0 && yy < ink.rows && ink.at<uint8_t>(yy, x) != 0) {
                ++sum;
            }
        }
        profile[y] = sum;
    }
    double mean = 0;
    for (double v : profile) {
        mean += v;
    }
    mean /= std::max<size_t>(1, profile.size());
    double variance = 0;
    for (double v : profile) {
        variance += (v - mean) * (v - mean);
    }
    return variance / std::max<size_t>(1, profile.size());
}

} // namespace

double estimateDeskewDegrees(const cv::Mat& grayFull, volatile int32_t* cancel) {
    // Central crop: page borders, desk slivers and bowed edges would otherwise dominate the
    // projection profile; the tilt of the text block is what matters.
    const int marginX = static_cast<int>(grayFull.cols * params::kDeskewSideMargin);
    const int marginY = static_cast<int>(grayFull.rows * params::kDeskewMargin);
    cv::Mat gray = grayFull(
        cv::Rect(marginX, marginY, grayFull.cols - 2 * marginX, grayFull.rows - 2 * marginY));
    cv::Mat ink = inkMask(gray, params::kDeskewWidth);
    if (cv::countNonZero(ink) < params::kMinInkFraction * ink.total()) {
        return 0.0;
    }
    double best = 0.0;
    double bestVar = -1.0;
    for (double a = -params::kMaxDeskewDegrees; a <= params::kMaxDeskewDegrees + 1e-9;
         a += params::kDeskewCoarseStep) {
        checkCancel(cancel);
        const double v = profileVariance(ink, a);
        if (v > bestVar) {
            bestVar = v;
            best = a;
        }
    }
    double refined = best;
    for (double a = best - params::kDeskewCoarseStep; a <= best + params::kDeskewCoarseStep + 1e-9;
         a += params::kDeskewFineStep) {
        const double v = profileVariance(ink, a);
        if (v > bestVar) {
            bestVar = v;
            refined = a;
        }
    }
    return refined;
}

} // namespace pp
