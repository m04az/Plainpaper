#pragma once

/** Tuned constants of the pipeline (research R5-R9). Every value is named so tuning is reviewable.
 */
namespace pp::params {

/** Smallest accepted long edge of an input photo (FR-001 validation). */
constexpr int kMinInputLongEdge = 640;
/** Largest accepted long edge of an input photo; decoders cap at 12 MP (research R12). */
constexpr int kMaxInputLongEdge = 6000;
/** Working resolution bounds accepted from the caller. */
constexpr int kMinWorkingLongEdge = 800;
constexpr int kMaxWorkingLongEdge = 3000;
/** Long edge of the corrected page kept in the session, per capability. */
constexpr int kPageLongEdgeFull = 3508;
constexpr int kPageLongEdgeSimplified = 2600;
/** Median luminance (0..1) below which the photo is reported as too dark (FR-014). */
constexpr double kTooDarkMedianLuminance = 0.12;
/** Number of horizontal bands used by the full-resolution warp so cancellation is prompt. */
constexpr int kWarpBands = 8;
/** OpenCV worker threads per capability (research R11). */
constexpr int kThreadsSimplified = 2;
constexpr int kThreadsFullMax = 4;

} // namespace pp::params
