#include "cancel.hpp"
#include "stages.hpp"

#include <algorithm>
#include <opencv2/imgproc.hpp>

// Illumination normalisation (research R5 step 7). Phase 2 skeleton: flat background so the
// division is the identity; the estimator lands with US1.
namespace pp {

cv::Mat estimateBackground(const cv::Mat& pageBgr, int workingLongEdge) {
    const double scale =
        std::min(1.0, static_cast<double>(workingLongEdge) / std::max(pageBgr.cols, pageBgr.rows));
    cv::Mat background(std::max(1, static_cast<int>(pageBgr.rows * scale)),
                       std::max(1, static_cast<int>(pageBgr.cols * scale)), CV_32FC3,
                       cv::Scalar(255.f, 255.f, 255.f));
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
    cv::divide(pageF, background, reflectance, 255.0);
    checkCancel(cancel);
    reflectance.convertTo(out, CV_8UC3);
}

bool hasSpecularHighlight(const cv::Mat& /*workingBgr*/) {
    return false;
}

} // namespace pp
