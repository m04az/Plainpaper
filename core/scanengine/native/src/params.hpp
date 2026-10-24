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
/** pp_detect (003): preview frames are analysed at most at this long edge; smaller as given. */
constexpr int kDetectWorkingLongEdge = 800;
/** Smallest preview frame pp_detect accepts. */
constexpr int kMinDetectLongEdge = 240;
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

// ---- detection model (specs/004 research R2) ----
/** Model input side (frame resized, aspect not kept) and output mask side. */
constexpr int kModelInput = 256;
constexpr int kModelMask = 128;

// ---- detection (research R5 step 2) ----
/** Auto-Canny thresholds as factors of the median luminance. */
constexpr double kCannyLowFactor = 0.66;
constexpr double kCannyHighFactor = 1.33;
/** approxPolyDP epsilon as a fraction of the hull perimeter. */
constexpr double kApproxEpsilon = 0.02;
/** Coarsest approximation tried before fitting the largest four-vertex quad to the hull. */
constexpr double kApproxEpsilonMax = 0.06;
constexpr double kApproxEpsilonStep = 0.01;
/** Hulls with at most this many vertices after approximation get a max-area quad fit. */
constexpr size_t kMaxQuadFitVertices = 12;
/** A side within this fraction of the frame size from a border lies on the border. */
constexpr double kBorderSideMargin = 0.015;
/** More sides than this on the frame border means the quad is the frame, not a page. */
constexpr int kMaxBorderSides = 2;
/** Bright-region fallback: opening width (fraction of the short side) and minimum quad IoU. */
constexpr double kRegionOpenFraction = 0.10;
constexpr double kRegionMinIoU = 0.85;
constexpr int kRegionMaxBorderSides = 1;
/** Accepted page area as a fraction of the frame. */
constexpr double kMinPageAreaFraction = 0.20;
/** Small documents (ID cards, receipts held at a distance) down to this area are accepted when
 * no side lies on the frame border, the outline is mostly present and the quad is clearly
 * brighter than a thin ring around it (kHullMinContrast). */
constexpr double kMinSmallPageAreaFraction = 0.05;
constexpr double kSmallPageMinSupport = 0.3;
constexpr double kSmallPageMinMeanSupport = 0.6;
/** Side refinement: edge pixels within this band of a fitted side (fraction of its length,
 * clamped in pixels) are fitted with a robust line; the band then narrows for a second pass. */
constexpr double kRefineBandFraction = 0.04;
constexpr double kRefineBandMinPx = 6.0;
constexpr double kRefineBandMaxPx = 20.0;
constexpr double kRefineSecondBandPx = 4.0;
constexpr int kRefineMinPoints = 20;
/** After the fit each side may slide this far (px at working resolution) to the outermost edge. */
constexpr double kRefineSnapPx = 8.0;
/** Filled-edge candidates: closing that fills text, opening that cuts thin attachments
 * (fractions of the short side). */
constexpr double kFillCloseFraction = 0.025;
constexpr double kFillOpenFraction = 0.12;
/** Width of the ring outside a small quad used for its contrast (fraction of the diagonal). */
constexpr double kSmallPageRingFraction = 0.02;
constexpr double kMaxPageAreaFraction = 0.985;
/** Points sampled per side when measuring edge support. */
constexpr int kEdgeSupportSamples = 32;
/** High confidence needs this edge support on every side and this area fraction. */
constexpr double kHighConfidenceSupport = 0.80;
constexpr double kHighConfidenceArea = 0.08;
/** Page-fills-frame rule: bright (>= this luminance, 0..1), flat (variance below) pixels. */
constexpr double kPaperLuminance = 0.60;
constexpr double kPaperVariance = 64.0;
constexpr double kFillsFrameFraction = 0.95;
/** Low-contrast pass: smoothing sigma (px) and fixed Canny thresholds on Lab channels. */
constexpr double kLowContrastSigma = 1.5;
/** Median kernel (px at working resolution, odd) that removes text before the faint edge pass. */
constexpr int kTextMedianKernel = 31;
/** Oblique long edges (shadow boundaries) are erased before quads are formed. */
constexpr double kObliqueMinLengthFraction = 0.15;
constexpr int kObliqueEraseThickness = 11;
constexpr double kLowContrastCannyLow = 3.0;
constexpr double kLowContrastCannyHigh = 9.0;
/** Minimum inside-minus-outside luminance (0..255) for a faint quad / bright hull to count. */
constexpr double kLowContrastMinContrast = 6.0;
constexpr double kHullMinContrast = 20.0;
/** Otsu between-class/total variance needed before its boundary is used as an edge source. */
constexpr double kOtsuMinSeparability = 0.75;
/** Every side of an accepted quad needs at least this edge support, and the four-side mean this
 * much. */
constexpr double kMinAcceptSupport = 0.1;
constexpr double kMinAcceptMeanSupport = 0.5;
/** Hough fallback (straight page sides from fragmented edges). */
constexpr int kHoughThreshold = 50;
constexpr double kHoughMinLengthFraction = 0.08;
constexpr double kHoughMaxGap = 25.0;
constexpr double kHoughAxisTolerance = 20.0;    // degrees from horizontal / vertical
constexpr double kHoughClusterTolerance = 0.03; // of the frame height / width
constexpr double kHoughSideFraction = 0.30;     // total segment length per side, of the dimension
constexpr double kHoughMinExtent = 0.40;
/** Relaxed Hough settings for the faint (low-contrast) pass. */
constexpr int kHoughRelaxedThreshold = 25;
constexpr double kHoughRelaxedMinLengthFraction = 0.04;
constexpr double kHoughRelaxedMaxGap = 60.0;
constexpr double kHoughRelaxedSideFraction =
    0.15; // page must span this fraction between opposite sides
/** The faint pass also checks inside/outside contrast, so a weaker edge support suffices. */
constexpr double kHoughRelaxedMinMeanSupport = 0.35;

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
/** Fractions of each page edge ignored by the tilt estimate: wide at the sides, where a curled
    page bows its lines the most, so the central text decides the tilt. */
constexpr double kDeskewMargin = 0.08;
constexpr double kDeskewSideMargin = 0.25;
constexpr double kDeskewCoarseStep = 0.5;
constexpr double kDeskewFineStep = 0.1;

} // namespace pp::params

namespace pp::params {

// ---- orientation (research R6) ----
/** Column-profile variance must exceed the row-profile variance by this ratio to call lines
 * vertical. */
constexpr double kAxisRatio = 1.3;
/** Rows above this fraction of the profile maximum belong to a text line. */
constexpr double kLineRowFraction = 0.15;
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
/** Vote weight cap as a multiple of the median line mass; solid bars (mean fill above this
    fraction of the profile maximum) and lines inside the outer margin are ignored. */
constexpr double kLineMassCap = 2.0;
constexpr double kSolidBarFill = 0.6;
constexpr double kOrientMargin = 0.04;
/** Vertical strips for the up/down vote (curl-tolerant line segmentation). */
constexpr int kOrientStrips = 3;

} // namespace pp::params

namespace pp::params {

// ---- illumination (research R5 step 7) ----
/** Long edge at which the paper level is estimated. */
constexpr int kBackgroundLongEdge = 500;
/** Closing kernel as a fraction of the background image width (must exceed text line height). */
constexpr double kBackgroundClosingFraction = 1.0 / 20.0;
constexpr double kBackgroundMedianFraction = 1.0 / 40.0;
/** Final smoothing of the background estimate (px at working resolution). */
constexpr double kBackgroundBlurSigma = 1.5;
/** Background floor relative to the page's paper level (limits brightening of dark regions). */
constexpr double kBackgroundFloor = 0.45;
/** Rows per band for the illumination division (bounds the float temporaries). */
constexpr int kNormalizeBandRows = 256;
/** Reflectance scale: paper maps to this value (slightly above 255 so paper clips to white). */
constexpr double kWhitePoint = 262.0;
/** Specular highlight: more than this fraction of working pixels above this luminance. */
constexpr double kClippedLuminance = 253.0;
constexpr double kClippedFraction = 0.03;
/** Inverted document: median luminance below this (0..255) with light strokes present. */
constexpr int kInvertedMedianLuminance = 90;
constexpr int kInvertedStrokeContrast = 60;
constexpr double kInvertedMinStrokeFraction = 0.005;

// ---- Grayscale / Color document / Original (research R9) ----
/** Tone curve exponent applied to normalised luminance (above 1 darkens mid-tones and ink). */
constexpr double kToneGamma = 1.1;
/** Color document: pixels with chroma below this and luminance above this fraction are paper. */
constexpr int kPaperChroma = 12;
constexpr double kPaperLuminanceFraction = 0.85;
constexpr double kSaturationBoost = 1.35;
/** Color document: tone exponent for ink (above 1 darkens strokes more than near-white tones). */
constexpr double kColorInkGamma = 1.6;
/** Grayscale: weight of the darkest channel (coloured ink) against luminance. */
constexpr double kGrayInkWeight = 0.5;
/** Unsharp mask at output resolution (300 DPI): sigma in pixels and strength. */
constexpr double kSharpenSigma = 1.2;
constexpr double kSharpenAmount = 0.8;
/** Original: brightness lift and white-balance strength (0 = none, 1 = full grey-world). */
constexpr double kOriginalBrightness = 1.03;
constexpr double kOriginalWhiteBalance = 0.5;

} // namespace pp::params

namespace pp::params {

// ---- Scan style (research R9) ----
/** Sauvola window at the reference page width; scaled with the actual page width. */
constexpr int kSauvolaWindow = 31;
constexpr double kSauvolaReferenceWidth = 2000.0;
constexpr double kSauvolaK = 0.2;
constexpr double kSauvolaR = 128.0;
/** Rows per band for the Sauvola threshold (bounds the float temporaries). */
constexpr int kSauvolaBandRows = 256;
/** Regions with local std-dev below this are blank paper, whatever their mean. */
constexpr double kInkFloorStdDev = 4.0;
/** Pixels brighter than this (0..255) are never ink. */
constexpr double kInkMaxLuminance = 200.0;
/** Border slivers: ink components touching the border, spanning this fraction of the page and at
    least this fraction of the long edge thick, are desk, not content. */
constexpr double kSliverMinExtent = 0.20;
constexpr double kSliverMinThickness = 0.006;
/** Gaussian sigma (px) that softens the binary ink edge. */
constexpr double kInkEdgeSigma = 0.7;

} // namespace pp::params

namespace pp::params {

// ---- flattening (research R7) ----
/** Working long edge for line fitting. */
constexpr int kFlattenWidth = 1000;
/** Horizontal closing kernel = page width / divisor (joins words into lines). */
constexpr int kLineCloseDivisor = 25;
/** A text line must span this fraction of the page width. */
constexpr double kMinLineWidthFraction = 0.30;
constexpr int kLineSamples = 32;
constexpr int kMinCurlLines = 6;
/** Fraction of curved lines that must share the curl direction. */
constexpr double kCurlAgreement = 0.80;
/** Curvature term (px at working resolution) below which a line counts as straight. */
constexpr double kMinCurvaturePx = 1.5;
/** Flattening is kept only if the mean curvature drops to this fraction or less. */
constexpr double kCurlImprovement = 0.5;

} // namespace pp::params
