#include "cancel.hpp"
#include "params.hpp"
#include "stages.hpp"

#include <algorithm>
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

cv::Mat estimateBackground(const cv::Mat& pageBgr, int workingLongEdge) {
    const double scale =
        std::min(1.0, static_cast<double>(workingLongEdge) / std::max(pageBgr.cols, pageBgr.rows));
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
    cv::Mat background;
    cv::resize(backgroundSmall, background, page.size(), 0, 0, cv::INTER_LINEAR);
    checkCancel(cancel);
    cv::Mat pageF;
    page.convertTo(pageF, CV_32FC3);
    cv::Mat reflectance;
    cv::divide(pageF, background, reflectance, params::kWhitePoint);
    checkCancel(cancel);
    reflectance.convertTo(out, CV_8UC3);
}

bool hasSpecularHighlight(const cv::Mat& workingBgr) {
    cv::Mat gray;
    cv::cvtColor(workingBgr, gray, cv::COLOR_BGR2GRAY);
    cv::Mat clipped;
    cv::threshold(gray, clipped, params::kClippedLuminance, 255, cv::THRESH_BINARY);
    return cv::countNonZero(clipped) > params::kClippedFraction * gray.total();
}

} // namespace pp
