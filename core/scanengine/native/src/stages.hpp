#pragma once

#include "memory.hpp"
#include "plainpaper_scan.h"
#include "session.hpp"
#include "timing.hpp"

#include <array>
#include <cstdint>
#include <opencv2/core.hpp>

namespace pp {

using Quad = std::array<cv::Point2f, 4>; // TL, TR, BR, BL

/** Everything a stage needs during pp_analyze. */
struct AnalyzeContext {
    const pp_image_rgba* photo = nullptr;
    const pp_analyze_options* options = nullptr;
    volatile int32_t* cancel = nullptr;
    /** Photo as a zero-copy RGBA view. */
    cv::Mat photoView;
    /** Downscaled BGR working image (long edge = options->working_long_edge). */
    cv::Mat working;
    /** working pixels per photo pixel. */
    double workingScale = 1.0;
    Timings timings;
    MemoryTracker memory;
    uint32_t flags = 0;
    bool fullCapability = true;
};

struct DetectResult {
    Quad quad; // photo coordinates
    pp_confidence confidence = PP_CONF_NOT_FOUND;
    pp_corner_source source = PP_SRC_FULL_FRAME;
};

struct Geometry {
    Quad quad;          // photo coordinates after orientation reordering
    cv::Size pageSize;  // corrected page size in pixels
    double aspect = 1.; // width / height of the physical page
    double deskewDeg = 0.;
    int rotation = 0; // 0/90/180/270 applied so the text reads upright
    pp_orientation_confidence orientationConfidence = PP_ORIENT_NONE;
};

// detect.cpp
DetectResult detectPage(AnalyzeContext& ctx);
Quad fullFrameQuad(const pp_image_rgba& photo);
// geometry.cpp
Quad orderCorners(const Quad& quad);
double estimateAspect(const Quad& quad, int imageWidth, int imageHeight);
Geometry planGeometry(const AnalyzeContext& ctx, const Quad& quad, int pageLongEdge);
cv::Mat warpPage(const AnalyzeContext& ctx, const Geometry& geometry);
cv::Mat warpPreview(const AnalyzeContext& ctx, const Geometry& geometry, int longEdge);
// deskew.cpp
double estimateDeskewDegrees(const cv::Mat& gray);
// orient.cpp
int estimateOrientation(const cv::Mat& grayUpright, pp_orientation_confidence& confidence);
void applyRotation(cv::Mat& image, int rotation);
Quad rotateQuadOrder(const Quad& quad, int rotation);
// flatten.cpp
pp_step_status flattenPage(cv::Mat& page, volatile int32_t* cancel);
// illumination.cpp
cv::Mat estimateBackground(const cv::Mat& pageBgr, int workingLongEdge);
void normalizeIllumination(const cv::Mat& page, const cv::Mat& backgroundSmall, cv::Mat& out,
                           volatile int32_t* cancel);
bool hasSpecularHighlight(const cv::Mat& workingBgr);
// render.cpp
pp_status renderPage(pp_session& session, pp_style style, pp_fit fit, int outWidth, int outHeight,
                     volatile int32_t* cancel, pp_output_image* out);

} // namespace pp
