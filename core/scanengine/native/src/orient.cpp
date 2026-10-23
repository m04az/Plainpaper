#include "stages.hpp"

#include <opencv2/core.hpp>

// Upright orientation (research R6). Phase 2 skeleton keeps the page; the vote lands with US1.
namespace pp {

int estimateOrientation(const cv::Mat& /*grayUpright*/, pp_orientation_confidence& confidence) {
    confidence = PP_ORIENT_NONE;
    return 0;
}

void applyRotation(cv::Mat& image, int rotation) {
    switch (rotation) {
    case 90:
        cv::rotate(image, image, cv::ROTATE_90_CLOCKWISE);
        break;
    case 180:
        cv::rotate(image, image, cv::ROTATE_180);
        break;
    case 270:
        cv::rotate(image, image, cv::ROTATE_90_COUNTERCLOCKWISE);
        break;
    default:
        break;
    }
}

Quad rotateQuadOrder(const Quad& quad, int rotation) {
    // Corners stay in photo coordinates; only the role (TL/TR/BR/BL) shifts with the rotation.
    const int shift = ((rotation / 90) % 4 + 4) % 4;
    Quad out;
    for (int i = 0; i < 4; ++i) {
        out[i] = quad[(i + 4 - shift) % 4];
    }
    return out;
}

} // namespace pp
