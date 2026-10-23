#include "stages.hpp"

// Page detection (research R5 step 2). Phase 2 skeleton: full-frame fallback only; the real
// detector lands with User Story 1.
namespace pp {

Quad fullFrameQuad(const pp_image_rgba& photo) {
    const float w = static_cast<float>(photo.width);
    const float h = static_cast<float>(photo.height);
    return Quad{cv::Point2f(0.f, 0.f), cv::Point2f(w, 0.f), cv::Point2f(w, h), cv::Point2f(0.f, h)};
}

DetectResult detectPage(AnalyzeContext& ctx) {
    DetectResult result;
    result.quad = fullFrameQuad(*ctx.photo);
    result.confidence = PP_CONF_NOT_FOUND;
    result.source = PP_SRC_FULL_FRAME;
    return result;
}

} // namespace pp
