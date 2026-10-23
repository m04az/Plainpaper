#include "stages.hpp"

// Residual tilt (research R5 step 4). Phase 2 skeleton returns 0; the estimator lands with US1.
namespace pp {

double estimateDeskewDegrees(const cv::Mat& /*gray*/) {
    return 0.0;
}

} // namespace pp
