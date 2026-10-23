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
double upsideDownWeight(const std::vector<double>& profile, const std::vector<Line>& lines,
                        double& totalMass) {
    double flipped = 0;
    totalMass = 0;
    for (size_t i = 0; i < lines.size(); ++i) {
        const Line& line = lines[i];
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
        const double position =
            ((bandStart + bandEnd) / 2.0 - top + 0.5) / std::max(1, bottom - top);
        if (position > params::kBandLowerPosition) {
            vote -= params::kPositionCueWeight;
        } else if (position < 1.0 - params::kBandLowerPosition) {
            vote += params::kPositionCueWeight;
        }
        totalMass += line.mass;
        if (vote > 0) {
            flipped += line.mass;
        } else if (vote == 0) {
            flipped += 0.5 * line.mass;
        }
    }
    return totalMass > 0 ? flipped / totalMass : 0.0;
}

} // namespace

int estimateOrientation(const cv::Mat& grayUpright, pp_orientation_confidence& confidence) {
    confidence = PP_ORIENT_NONE;
    cv::Mat ink = inkMaskFor(grayUpright);
    if (cv::countNonZero(ink) < params::kMinInkFraction * ink.total()) {
        return 0;
    }
    std::vector<double> rows;
    const double rowVar = rowProfileVariance(ink, &rows);
    cv::Mat transposed;
    cv::rotate(ink, transposed, cv::ROTATE_90_CLOCKWISE);
    std::vector<double> cols;
    const double colVar = rowProfileVariance(transposed, &cols);

    int rotation = 0;
    const std::vector<double>* profile = &rows;
    if (colVar > params::kAxisRatio * rowVar) {
        rotation = 90;
        profile = &cols;
    } else if (rowVar <= 0.0) {
        return 0;
    }
    std::vector<Line> lines = segmentLines(*profile);
    if (static_cast<int>(lines.size()) < params::kMinLinesForVote) {
        return rotation;
    }
    double mass = 0;
    const double flipped = upsideDownWeight(*profile, lines, mass);
    if (flipped >= params::kFlipThreshold) {
        rotation = (rotation + 180) % 360;
    }
    const double agreement = std::max(flipped, 1.0 - flipped);
    confidence = (lines.size() >= static_cast<size_t>(params::kHighConfidenceLines) &&
                  agreement >= params::kHighConfidenceAgreement)
                     ? PP_ORIENT_HIGH
                     : PP_ORIENT_LOW;
    return rotation;
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
