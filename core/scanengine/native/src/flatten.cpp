#include "stages.hpp"

// Single-axis curl flattening (research R7). Phase 2 skeleton skips; the method lands with US3.
namespace pp {

pp_step_status flattenPage(cv::Mat& /*page*/, volatile int32_t* /*cancel*/) {
    return PP_STEP_SKIPPED_INSUFFICIENT_LINES;
}

} // namespace pp
