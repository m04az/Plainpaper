#pragma once

#include "plainpaper_scan.h"
#include "timing.hpp"

#include <atomic>
#include <memory>
#include <opencv2/core.hpp>

namespace pp {
class DetectorModel;
}

/** Analysis of one photo. Owns the corrected page; rendering reuses it (FR-009). */
struct pp_session {
    /** Corrected (perspective, deskew, orientation, flattening) page, BGR 8-bit, before
     * illumination. */
    cv::Mat page;
    /** Illumination estimate at working resolution, BGR 32F, same aspect as page. */
    cv::Mat backgroundSmall;
    /** Grey working copy of the normalised page used by renderers for ink analysis. */
    cv::Mat workingGray;
    pp_analysis_info info{};
    pp::Timings timings;
    bool inverted = false;
};

/** Immutable engine configuration. */
struct pp_engine {
    int apiVersion = PP_API_VERSION;
    /** Document-detection model; null or not loaded means the classic detector (specs/004). */
    std::shared_ptr<pp::DetectorModel> detector;
    /** Cleared by the performance policy: the loaded model stays but is not used. */
    std::atomic<bool> detectorEnabled{true};
};
