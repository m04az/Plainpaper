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
/** Detected quads are shrunk towards their centre by this fraction so desk pixels never reach the
 * page edge. */
constexpr double kQuadInset = 0.01;
/** Number of horizontal bands used by the full-resolution warp so cancellation is prompt. */
constexpr int kWarpBands = 8;
/** OpenCV worker threads per capability (research R11). */
constexpr int kThreadsSimplified = 2;
constexpr int kThreadsFullMax = 4;

} // namespace pp::params

namespace pp::params {

// ---- detection (research R5 step 2) ----
/** Auto-Canny thresholds as factors of the median luminance. */
constexpr double kCannyLowFactor = 0.66;
constexpr double kCannyHighFactor = 1.33;
/** approxPolyDP epsilon as a fraction of the hull perimeter. */
constexpr double kApproxEpsilon = 0.02;
/** Accepted page area as a fraction of the frame. */
constexpr double kMinPageAreaFraction = 0.20;
constexpr double kMaxPageAreaFraction = 0.985;
/** Points sampled per side when measuring edge support. */
constexpr int kEdgeSupportSamples = 32;
/** High confidence needs this edge support on every side and this area fraction. */
constexpr double kHighConfidenceSupport = 0.80;
constexpr double kHighConfidenceArea = 0.30;
/** Page-fills-frame rule: bright (>= this luminance, 0..1), flat (variance below) pixels. */
constexpr double kPaperLuminance = 0.60;
constexpr double kPaperVariance = 64.0;
constexpr double kFillsFrameFraction = 0.95;

} // namespace pp::params

namespace pp::params {

// ---- deskew and text analysis ----
/** Working width of the ink mask used by deskew and orientation. */
constexpr int kDeskewWidth = 800;
constexpr int kInkBlockSize = 31;
constexpr double kInkOffset = 15.0;
/** Minimum ink coverage for text-based decisions. */
constexpr double kMinInkFraction = 0.002;
constexpr double kMaxDeskewDegrees = 5.0;
constexpr double kDeskewCoarseStep = 0.5;
constexpr double kDeskewFineStep = 0.1;

} // namespace pp::params

namespace pp::params {

// ---- orientation (research R6) ----
/** Column-profile variance must exceed the row-profile variance by this ratio to call lines
 * vertical. */
constexpr double kAxisRatio = 1.3;
/** Rows above this fraction of the profile maximum belong to a text line. */
constexpr double kLineRowFraction = 0.10;
constexpr int kMinLineHeight = 6;
constexpr int kMinLinesForVote = 3;
/** Fraction of line mass that must vote "upside down" to flip. */
constexpr double kFlipThreshold = 0.60;
constexpr int kHighConfidenceLines = 6;
constexpr double kHighConfidenceAgreement = 0.75;
/** Rows at or above this fraction of a line's densest row form its dense band. */
constexpr double kBandFraction = 0.40;
/** Band centre below this fraction of the line box (from the top) reads as upright. */
constexpr double kBandLowerPosition = 0.60;
constexpr double kPositionCueWeight = 0.5;

} // namespace pp::params

namespace pp::params {

// ---- illumination (research R5 step 7) ----
/** Closing kernel as a fraction of the working page width (must exceed text stroke/line size). */
constexpr double kBackgroundClosingFraction = 1.0 / 25.0;
constexpr double kBackgroundMedianFraction = 1.0 / 40.0;
/** Final smoothing of the background estimate (px at working resolution). */
constexpr double kBackgroundBlurSigma = 1.5;
/** Background floor relative to the page's paper level (limits brightening of dark regions). */
constexpr double kBackgroundFloor = 0.45;
/** Reflectance scale: paper maps to this value (slightly above 255 so paper clips to white). */
constexpr double kWhitePoint = 262.0;
/** Specular highlight: more than this fraction of working pixels above this luminance. */
constexpr double kClippedLuminance = 253.0;
constexpr double kClippedFraction = 0.03;

} // namespace pp::params

namespace pp::params {

// ---- Scan style (research R9) ----
/** Sauvola window at the reference page width; scaled with the actual page width. */
constexpr int kSauvolaWindow = 31;
constexpr double kSauvolaReferenceWidth = 2000.0;
constexpr double kSauvolaK = 0.2;
constexpr double kSauvolaR = 128.0;
/** Regions with local std-dev below this are blank paper, whatever their mean. */
constexpr double kInkFloorStdDev = 4.0;
/** Pixels brighter than this (0..255) are never ink. */
constexpr double kInkMaxLuminance = 200.0;
/** Gaussian sigma (px) that softens the binary ink edge. */
constexpr double kInkEdgeSigma = 0.7;

} // namespace pp::params
