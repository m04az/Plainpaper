#include "cancel.hpp"
#include "params.hpp"
#include "stages.hpp"

#include <algorithm>
#include <cmath>
#include <opencv2/imgproc.hpp>

// Illumination normalisation (research R5 step 7): the paper level is estimated per channel by a
// morphological closing (removes text) followed by a median (removes residual specks) at
// working resolution; the page is divided by the upsampled estimate so shadows and colour casts
// vanish while local contrast (faint pencil, stamps) survives.
namespace pp {

namespace {

int oddKernel(double size) {
    int k = std::max(3, static_cast<int>(size));
    return (k % 2 == 0) ? k + 1 : k;
}

} // namespace

cv::Mat estimateBackground(const cv::Mat& pageBgr, int /*workingLongEdge*/) {
    // The paper level is low-frequency: estimating it at kBackgroundLongEdge keeps the
    // morphology cheap (the kernels shrink with the image) and the result is upsampled later.
    const double scale = std::min(1.0, static_cast<double>(params::kBackgroundLongEdge) /
                                           std::max(pageBgr.cols, pageBgr.rows));
    cv::Mat small;
    if (scale < 1.0) {
        cv::resize(pageBgr, small, cv::Size(), scale, scale, cv::INTER_AREA);
    } else {
        small = pageBgr.clone();
    }
    const int closing = oddKernel(small.cols * params::kBackgroundClosingFraction);
    const int median = std::min(oddKernel(small.cols * params::kBackgroundMedianFraction), 99);
    cv::Mat closed;
    cv::morphologyEx(small, closed, cv::MORPH_CLOSE,
                     cv::getStructuringElement(cv::MORPH_ELLIPSE, cv::Size(closing, closing)));
    cv::Mat smoothed;
    cv::medianBlur(closed, smoothed, median);
    // A light blur removes median blockiness without rounding hard shadow edges.
    cv::GaussianBlur(smoothed, smoothed, cv::Size(0, 0), params::kBackgroundBlurSigma);
    // Floor: a large dark region (photo, dark panel) is not paper; never brighten it more than
    // 1/kBackgroundFloor of the page's own paper level.
    cv::Mat gray;
    cv::cvtColor(smoothed, gray, cv::COLOR_BGR2GRAY);
    std::vector<uint8_t> values(gray.begin<uint8_t>(), gray.end<uint8_t>());
    std::nth_element(values.begin(), values.begin() + values.size() * 95 / 100, values.end());
    const double paperLevel = values[values.size() * 95 / 100];
    const double floorLevel = std::max(8.0, params::kBackgroundFloor * paperLevel);
    cv::Mat background;
    smoothed.convertTo(background, CV_32FC3);
    cv::max(background, cv::Scalar(floorLevel, floorLevel, floorLevel), background);
    return background;
}

void normalizeIllumination(const cv::Mat& page, const cv::Mat& backgroundSmall, cv::Mat& out,
                           volatile int32_t* cancel) {
    // Banded: the float temporaries cover kNormalizeBandRows rows at a time instead of the whole
    // page (a full-page float copy would be three times the page size, three times over).
    out.create(page.size(), CV_8UC3);
    const double scaleX = static_cast<double>(backgroundSmall.cols) / page.cols;
    const double scaleY = static_cast<double>(backgroundSmall.rows) / page.rows;
    for (int y0 = 0; y0 < page.rows; y0 += params::kNormalizeBandRows) {
        checkCancel(cancel);
        const int y1 = std::min(page.rows, y0 + params::kNormalizeBandRows);
        // Background rows for this band, upsampled with a one-row overlap for the interpolation.
        const int by0 = std::max(0, static_cast<int>(y0 * scaleY) - 1);
        const int by1 =
            std::min(backgroundSmall.rows, static_cast<int>(std::ceil(y1 * scaleY)) + 1);
        cv::Mat background;
        cv::resize(backgroundSmall.rowRange(by0, by1), background,
                   cv::Size(page.cols, static_cast<int>(std::lround((by1 - by0) / scaleY))), 0, 0,
                   cv::INTER_LINEAR);
        const int offset = y0 - static_cast<int>(std::lround(by0 / scaleY));
        cv::Mat bandBackground = background.rowRange(
            std::clamp(offset, 0, std::max(0, background.rows - (y1 - y0))),
            std::clamp(offset, 0, std::max(0, background.rows - (y1 - y0))) + (y1 - y0));
        cv::Mat bandF;
        page.rowRange(y0, y1).convertTo(bandF, CV_32FC3);
        cv::Mat reflectance;
        cv::divide(bandF, bandBackground, reflectance, params::kWhitePoint);
        reflectance.convertTo(out.rowRange(y0, y1), CV_8UC3);
    }
    (void)scaleX;
}

bool hasSpecularHighlight(const cv::Mat& workingBgr) {
    cv::Mat gray;
    cv::cvtColor(workingBgr, gray, cv::COLOR_BGR2GRAY);
    cv::Mat clipped;
    cv::threshold(gray, clipped, params::kClippedLuminance, 255, cv::THRESH_BINARY);
    return cv::countNonZero(clipped) > params::kClippedFraction * gray.total();
}

} // namespace pp

namespace pp {

bool isInvertedDocument(const cv::Mat& workingBgr, const cv::Mat& /*normalizedPreviewGray*/) {
    // The raw working image (not the normalised one, which forces paper to white): a page whose
    // median luminance is dark overall is inverted or unusable as black-on-white.
    cv::Mat gray;
    cv::cvtColor(workingBgr, gray, cv::COLOR_BGR2GRAY);
    cv::Mat small;
    cv::resize(gray, small, cv::Size(), 0.25, 0.25, cv::INTER_AREA);
    std::vector<uint8_t> values(small.begin<uint8_t>(), small.end<uint8_t>());
    std::nth_element(values.begin(), values.begin() + values.size() / 2, values.end());
    const int median = values[values.size() / 2];
    if (median >= params::kInvertedMedianLuminance) {
        return false;
    }
    // Light strokes on the dark page: a visible share of pixels is much brighter than the median.
    cv::Mat bright;
    cv::threshold(small, bright, std::min(254, median + params::kInvertedStrokeContrast), 255,
                  cv::THRESH_BINARY);
    const double brightFraction = static_cast<double>(cv::countNonZero(bright)) / small.total();
    return brightFraction > params::kInvertedMinStrokeFraction && brightFraction < 0.5;
}

} // namespace pp
