#include "debug.hpp"
#include "params.hpp"
#include "stages.hpp"

#include <algorithm>
#include <cmath>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <vector>

// Upright orientation without OCR (research R6): text-line axis from projection variance,
// then an up/down vote from the ink distribution around each line's densest row.
namespace pp {

namespace {

struct Line {
    int top;
    int bottom;
    double mass;
};

cv::Mat inkMaskFor(const cv::Mat& gray) {
    cv::Mat small;
    const double scale =
        std::min(1.0, static_cast<double>(params::kDeskewWidth) / std::max(gray.cols, gray.rows));
    if (scale < 1.0) {
        cv::resize(gray, small, cv::Size(), scale, scale, cv::INTER_AREA);
    } else {
        small = gray;
    }
    cv::Mat ink;
    cv::adaptiveThreshold(small, ink, 255, cv::ADAPTIVE_THRESH_MEAN_C, cv::THRESH_BINARY_INV,
                          params::kInkBlockSize, params::kInkOffset);
    // Drop isolated specks (noise) that would blur the profiles.
    cv::morphologyEx(ink, ink, cv::MORPH_OPEN,
                     cv::getStructuringElement(cv::MORPH_RECT, cv::Size(2, 2)));
    return ink;
}

/** Normalised variance (variance / mean^2) of the row projection of `ink`. */
double rowProfileVariance(const cv::Mat& ink, std::vector<double>* profileOut) {
    std::vector<double> profile(ink.rows, 0.0);
    for (int y = 0; y < ink.rows; ++y) {
        profile[y] = cv::countNonZero(ink.row(y));
    }
    double mean = 0;
    for (double v : profile) {
        mean += v;
    }
    mean /= std::max<size_t>(1, profile.size());
    double variance = 0;
    for (double v : profile) {
        variance += (v - mean) * (v - mean);
    }
    variance /= std::max<size_t>(1, profile.size());
    if (profileOut != nullptr) {
        *profileOut = profile;
    }
    return mean > 0 ? variance / (mean * mean) : 0.0;
}

/** Number of columns with any ink in rows [rowStart, rowEnd). */
double columnsWithInk(const cv::Mat& ink, int rowStart, int rowEnd) {
    if (rowEnd <= rowStart) {
        return 0.0;
    }
    cv::Mat band = ink.rowRange(std::max(0, rowStart), std::min(ink.rows, rowEnd));
    cv::Mat columns;
    cv::reduce(band, columns, 0, cv::REDUCE_MAX);
    return cv::countNonZero(columns);
}

double profileMax(const std::vector<double>& profile) {
    double maxValue = 0;
    for (double v : profile) {
        maxValue = std::max(maxValue, v);
    }
    return maxValue;
}

std::vector<Line> segmentLines(const std::vector<double>& profile) {
    std::vector<Line> lines;
    double maxValue = 0;
    for (double v : profile) {
        maxValue = std::max(maxValue, v);
    }
    const double threshold = params::kLineRowFraction * maxValue;
    int start = -1;
    for (int y = 0; y <= static_cast<int>(profile.size()); ++y) {
        const bool text = y < static_cast<int>(profile.size()) && profile[y] > threshold;
        if (text && start < 0) {
            start = y;
        } else if (!text && start >= 0) {
            if (y - start >= params::kMinLineHeight) {
                double mass = 0;
                for (int r = start; r < y; ++r) {
                    mass += profile[r];
                }
                lines.push_back(Line{start, y, mass});
            }
            start = -1;
        }
    }
    return lines;
}

/**
 * Up/down vote. In upright text of either script the dense band of a line (the Latin x-height
 * band, the Arabic baseline stroke) carries more ink above it (ascenders, capitals, letter
 * bodies, dots) than below it (descenders, tails); flipping the page swaps the two. A second,
 * weaker cue is the band's position inside the line box (lower half when upright), used only
 * when it is clearly off-centre. Returns the fraction of line mass voting "upside down".
 */
double upsideDownWeight(const cv::Mat& ink, const std::vector<double>& profile,
                        const std::vector<Line>& lines, double& totalMass) {
    double flipped = 0;
    totalMass = 0;
    // Weights are capped at twice the median line mass so one solid bar (a desk sliver, a
    // table rule, a photo) cannot outvote the text; such bars are also skipped outright.
    std::vector<double> masses;
    for (const Line& line : lines) {
        masses.push_back(line.mass);
    }
    std::nth_element(masses.begin(), masses.begin() + masses.size() / 2, masses.end());
    const double medianMass = masses.empty() ? 0.0 : masses[masses.size() / 2];
    const double massCap = params::kLineMassCap * medianMass;
    const int rows = static_cast<int>(profile.size());
    for (size_t i = 0; i < lines.size(); ++i) {
        const Line& line = lines[i];
        const int lineHeight = line.bottom - line.top;
        const double meanFill = line.mass / std::max(1, lineHeight);
        const bool solidBar = meanFill > params::kSolidBarFill * profileMax(profile);
        const bool inMargin = line.top < params::kOrientMargin * rows ||
                              line.bottom > (1.0 - params::kOrientMargin) * rows;
        if (solidBar || inMargin) {
            continue;
        }
        const int height = line.bottom - line.top;
        // Expand towards the neighbours (ascenders/descenders fall below the row threshold)
        // but never past the midpoint of the gap.
        const int gapAbove = i == 0 ? height : line.top - lines[i - 1].bottom;
        const int gapBelow = i + 1 == lines.size() ? height : lines[i + 1].top - line.bottom;
        const int top = std::max(0, line.top - std::min(height / 2, gapAbove / 2));
        const int bottom = std::min(static_cast<int>(profile.size()),
                                    line.bottom + std::min(height / 2, gapBelow / 2));
        int densest = top;
        for (int r = top; r < bottom; ++r) {
            if (profile[r] > profile[densest]) {
                densest = r;
            }
        }
        const double bandLevel = params::kBandFraction * profile[densest];
        int bandStart = densest;
        while (bandStart > top && profile[bandStart - 1] >= bandLevel) {
            --bandStart;
        }
        int bandEnd = densest;
        while (bandEnd + 1 < bottom && profile[bandEnd + 1] >= bandLevel) {
            ++bandEnd;
        }
        double above = 0;
        double below = 0;
        for (int r = top; r < bandStart; ++r) {
            above += profile[r];
        }
        for (int r = bandEnd + 1; r < bottom; ++r) {
            below += profile[r];
        }
        double vote = 0; // > 0 means upside down
        if (above + below > 0) {
            vote += (below - above) / (above + below);
        }
        // Column coverage: how many columns carry ink above the dense band versus below it.
        // Ascenders, capitals, Arabic tall letters and dots outnumber descenders and tails.
        const double columnsAbove = columnsWithInk(ink, top, bandStart);
        const double columnsBelow = columnsWithInk(ink, bandEnd + 1, bottom);
        if (columnsAbove + columnsBelow > 0) {
            vote += (columnsBelow - columnsAbove) / (columnsAbove + columnsBelow);
        }
        const double position =
            ((bandStart + bandEnd) / 2.0 - top + 0.5) / std::max(1, bottom - top);
        if (position > params::kBandLowerPosition) {
            vote -= params::kPositionCueWeight;
        } else if (position < 1.0 - params::kBandLowerPosition) {
            vote += params::kPositionCueWeight;
        }
        // Confident lines (large |vote|) weigh more than ambiguous ones.
        const double weight = std::min(line.mass, massCap) * std::min(1.0, std::abs(vote));
        debugLog("  line: position / above-below ratio / vote", position,
                 (above + below) > 0 ? (above - below) / (above + below) : 0.0, vote);
        totalMass += weight;
        if (vote > 0) {
            flipped += weight;
        }
    }
    return totalMass > 0 ? flipped / totalMass : 0.0;
}

} // namespace

int estimateAxisRotation(const cv::Mat& gray) {
    cv::Mat ink = inkMaskFor(gray);
    if (cv::countNonZero(ink) < params::kMinInkFraction * ink.total()) {
        return 0;
    }
    const double rowVar = rowProfileVariance(ink, nullptr);
    cv::Mat transposed;
    cv::rotate(ink, transposed, cv::ROTATE_90_CLOCKWISE);
    const double colVar = rowProfileVariance(transposed, nullptr);
    return colVar > params::kAxisRatio * rowVar ? 90 : 0;
}

bool estimateUpsideDown(const cv::Mat& gray, pp_orientation_confidence& confidence, int strips) {
    confidence = PP_ORIENT_NONE;
    cv::Mat ink = inkMaskFor(gray);
    if (cv::countNonZero(ink) < params::kMinInkFraction * ink.total()) {
        return false;
    }
    // Vote per vertical strip: within a strip the lines of a curled page are nearly straight,
    // so the row profile still separates them; the strips' votes are pooled by line mass.
    double flipped = 0;
    double totalMass = 0;
    int lineCount = 0;
    for (int k = 0; k < strips; ++k) {
        const int x0 = ink.cols * k / strips;
        const int x1 = ink.cols * (k + 1) / strips;
        cv::Mat strip = ink.colRange(x0, x1);
        std::vector<double> rows;
        rowProfileVariance(strip, &rows);
        std::vector<Line> lines = segmentLines(rows);
        if (static_cast<int>(lines.size()) < params::kMinLinesForVote) {
            continue;
        }
        double mass = 0;
        const double stripFlipped = upsideDownWeight(strip, rows, lines, mass);
        flipped += stripFlipped * mass;
        totalMass += mass;
        lineCount += static_cast<int>(lines.size());
    }
    if (totalMass <= 0 || lineCount < params::kMinLinesForVote) {
        return false;
    }
    const double fraction = flipped / totalMass;
    debugLog("orient: lines / flipped fraction", lineCount, fraction, 0);
    const double agreement = std::max(fraction, 1.0 - fraction);
    confidence =
        (lineCount >= params::kHighConfidenceLines && agreement >= params::kHighConfidenceAgreement)
            ? PP_ORIENT_HIGH
            : PP_ORIENT_LOW;
    return fraction >= params::kFlipThreshold;
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
