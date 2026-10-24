#include "cancel.hpp"
#include "debug.hpp"
#include "model.hpp"
#include "params.hpp"
#include "stages.hpp"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <opencv2/imgproc.hpp>
#include <string>
#include <vector>

// Page detection (research R5 step 2): edge map from auto-Canny plus an Otsu gradient, convex
// quadrilaterals scored by area x edge support x rectangularity, then the bright-region hull
// and the page-fills-frame rule as fallbacks.
namespace pp {

namespace {

struct Candidate {
    Quad quad; // working coordinates
    double score = 0;
    double areaFraction = 0;
    double minSupport = 0;
    double meanSupport = 0;
    int borderSides = 0;
};

int medianOf(const cv::Mat& gray) {
    int hist[256] = {0};
    for (int y = 0; y < gray.rows; ++y) {
        const uint8_t* row = gray.ptr<uint8_t>(y);
        for (int x = 0; x < gray.cols; ++x) {
            ++hist[row[x]];
        }
    }
    const long total = static_cast<long>(gray.rows) * gray.cols;
    long seen = 0;
    for (int v = 0; v < 256; ++v) {
        seen += hist[v];
        if (seen * 2 >= total) {
            return v;
        }
    }
    return 128;
}

/** Otsu's between-class variance over the total variance (1 = perfectly bimodal). */
double otsuSeparability(const cv::Mat& gray) {
    int hist[256] = {0};
    for (int y = 0; y < gray.rows; ++y) {
        const uint8_t* row = gray.ptr<uint8_t>(y);
        for (int x = 0; x < gray.cols; ++x) {
            ++hist[row[x]];
        }
    }
    const double total = static_cast<double>(gray.rows) * gray.cols;
    double sum = 0;
    for (int i = 0; i < 256; ++i) {
        sum += i * hist[i];
    }
    const double mean = sum / total;
    double variance = 0;
    for (int i = 0; i < 256; ++i) {
        variance += hist[i] * (i - mean) * (i - mean);
    }
    variance /= total;
    if (variance < 1e-6) {
        return 0.0;
    }
    double best = 0;
    double wB = 0;
    double sumB = 0;
    for (int t = 0; t < 256; ++t) {
        wB += hist[t];
        if (wB == 0) {
            continue;
        }
        const double wF = total - wB;
        if (wF == 0) {
            break;
        }
        sumB += t * hist[t];
        const double mB = sumB / wB;
        const double mF = (sum - sumB) / wF;
        best = std::max(best, wB * wF * (mB - mF) * (mB - mF) / (total * total));
    }
    return best / variance;
}

cv::Mat edgeMap(const cv::Mat& gray) {
    cv::Mat blurred;
    cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 0);
    const int median = medianOf(blurred);
    const double lower = std::max(0.0, params::kCannyLowFactor * median);
    const double upper = std::min(255.0, params::kCannyHighFactor * median);
    cv::Mat canny;
    cv::Canny(blurred, canny, lower, upper);
    // Bright paper against a darker desk: the Otsu boundary, which survives low local contrast,
    // but only when the histogram really is bimodal (a page-less desk split at its median would
    // otherwise produce a dense noise boundary everywhere).
    cv::Mat edges = canny;
    if (otsuSeparability(blurred) >= params::kOtsuMinSeparability) {
        cv::Mat otsu;
        cv::threshold(blurred, otsu, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);
        cv::Mat gradient;
        cv::morphologyEx(otsu, gradient, cv::MORPH_GRADIENT,
                         cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3)));
        cv::bitwise_or(canny, gradient, edges);
    }
    cv::dilate(edges, edges, cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3)));
    return edges;
}

/**
 * True when the segment runs along the photo border: the page continues past the frame there, so
 * the border itself is the visible side (open books, pages cut off by the framing).
 */
bool onFrameBorder(const cv::Mat& edges, const cv::Point2f& a, const cv::Point2f& b) {
    const float mx = static_cast<float>(params::kBorderSideMargin * edges.cols);
    const float my = static_cast<float>(params::kBorderSideMargin * edges.rows);
    const float right = static_cast<float>(edges.cols - 1);
    const float bottom = static_cast<float>(edges.rows - 1);
    return (a.x <= mx && b.x <= mx) || (a.y <= my && b.y <= my) ||
           (a.x >= right - mx && b.x >= right - mx) || (a.y >= bottom - my && b.y >= bottom - my);
}

/** Fraction of sampled points along a segment that sit on an edge (within the dilated map). */
double sideSupport(const cv::Mat& edges, const cv::Point2f& a, const cv::Point2f& b) {
    const int samples = params::kEdgeSupportSamples;
    int hits = 0;
    for (int i = 0; i < samples; ++i) {
        const float t = (i + 0.5f) / samples;
        const int x = static_cast<int>(std::lround(a.x + (b.x - a.x) * t));
        const int y = static_cast<int>(std::lround(a.y + (b.y - a.y) * t));
        bool hit = false;
        for (int dy = -2; dy <= 2 && !hit; ++dy) {
            for (int dx = -2; dx <= 2 && !hit; ++dx) {
                const int xx = x + dx;
                const int yy = y + dy;
                if (xx >= 0 && yy >= 0 && xx < edges.cols && yy < edges.rows &&
                    edges.at<uint8_t>(yy, xx) != 0) {
                    hit = true;
                }
            }
        }
        hits += hit ? 1 : 0;
    }
    return static_cast<double>(hits) / samples;
}

double rectangularity(const Quad& q) {
    double deviation = 0;
    for (int i = 0; i < 4; ++i) {
        const cv::Point2f& p = q[i];
        const cv::Point2f& prev = q[(i + 3) % 4];
        const cv::Point2f& next = q[(i + 1) % 4];
        const cv::Point2f u = prev - p;
        const cv::Point2f v = next - p;
        const double cosine = (u.x * v.x + u.y * v.y) / (cv::norm(u) * cv::norm(v) + 1e-9);
        const double angle = std::acos(std::clamp(cosine, -1.0, 1.0)) * 180.0 / CV_PI;
        deviation += std::abs(angle - 90.0);
    }
    return std::max(0.0, 1.0 - deviation / (4.0 * 90.0));
}

/** The four hull vertices spanning the largest area (n is small after approximation). */
bool maxAreaQuad(const std::vector<cv::Point>& poly, Quad& out) {
    const int n = static_cast<int>(poly.size());
    if (n < 4) {
        return false;
    }
    double bestArea = -1;
    std::array<int, 4> bestIdx{0, 1, 2, 3};
    for (int i = 0; i < n; ++i) {
        for (int j = i + 1; j < n; ++j) {
            for (int k = j + 1; k < n; ++k) {
                for (int l = k + 1; l < n; ++l) {
                    const std::vector<cv::Point> q{poly[i], poly[j], poly[k], poly[l]};
                    const double area = cv::contourArea(q);
                    if (area > bestArea) {
                        bestArea = area;
                        bestIdx = {i, j, k, l};
                    }
                }
            }
        }
    }
    Quad q;
    for (int i = 0; i < 4; ++i) {
        q[i] = cv::Point2f(static_cast<float>(poly[bestIdx[i]].x),
                           static_cast<float>(poly[bestIdx[i]].y));
    }
    out = orderCorners(q);
    return cv::isContourConvex(std::vector<cv::Point2f>(out.begin(), out.end()));
}

bool quadFromContour(const std::vector<cv::Point>& contour, Quad& out) {
    std::vector<cv::Point> hull;
    cv::convexHull(contour, hull);
    const double perimeter = cv::arcLength(hull, true);
    // Coarser and coarser approximations: a spine bump, a torn or occluded corner or a frame cut
    // adds vertices to an outline that is still essentially a four-sided page.
    std::vector<cv::Point> approx;
    for (double eps = params::kApproxEpsilon; eps <= params::kApproxEpsilonMax + 1e-9;
         eps += params::kApproxEpsilonStep) {
        cv::approxPolyDP(hull, approx, eps * perimeter, true);
        if (approx.size() <= 4) {
            break;
        }
        if (approx.size() <= params::kMaxQuadFitVertices && eps >= params::kApproxEpsilon * 2) {
            break;
        }
    }
    if (approx.size() == 4) {
        Quad q;
        for (int i = 0; i < 4; ++i) {
            q[i] = cv::Point2f(static_cast<float>(approx[i].x), static_cast<float>(approx[i].y));
        }
        out = orderCorners(q);
        return true;
    }
    if (approx.size() > 4 && approx.size() <= params::kMaxQuadFitVertices) {
        return maxAreaQuad(approx, out);
    }
    return false;
}

Candidate scoreQuad(const Quad& quad, const cv::Mat& edges, double frameArea) {
    Candidate c;
    c.quad = quad;
    const std::vector<cv::Point2f> pts(quad.begin(), quad.end());
    c.areaFraction = cv::contourArea(pts) / frameArea;
    double minSupport = 1.0;
    double meanSupport = 0.0;
    int borderSides = 0;
    for (int i = 0; i < 4; ++i) {
        const bool border = onFrameBorder(edges, quad[i], quad[(i + 1) % 4]);
        borderSides += border ? 1 : 0;
        const double s = border ? 1.0 : sideSupport(edges, quad[i], quad[(i + 1) % 4]);
        minSupport = std::min(minSupport, s);
        meanSupport += s / 4.0;
    }
    c.borderSides = borderSides;
    if (borderSides > params::kMaxBorderSides) {
        // Three or four sides on the border is the frame itself, not a page in it.
        minSupport = 0.0;
    }
    c.minSupport = minSupport;
    c.meanSupport = meanSupport;
    c.score = c.areaFraction * meanSupport * rectangularity(quad);
    if (std::getenv("PP_DEBUG_DIR") != nullptr) {
        cv::Mat overlay = edges / 2;
        for (int i = 0; i < 4; ++i) {
            cv::line(overlay, quad[i], quad[(i + 1) % 4], cv::Scalar(255), 3);
        }
        static int counter = 0;
        debugDump(("candidate-" + std::to_string(counter++)).c_str(), overlay);
    }
    return c;
}

bool intersect(const cv::Vec4f& a, const cv::Vec4f& b, cv::Point2f& out) {
    // Lines as point + direction: p = (a[2], a[3]) + t * (a[0], a[1]).
    const double cross = a[0] * b[1] - a[1] * b[0];
    if (std::abs(cross) < 1e-9) {
        return false;
    }
    const double dx = b[2] - a[2];
    const double dy = b[3] - a[3];
    const double t = (dx * b[1] - dy * b[0]) / cross;
    out = cv::Point2f(static_cast<float>(a[2] + t * a[0]), static_cast<float>(a[3] + t * a[1]));
    return true;
}

/** Robust line through the edge pixels near side a-b (ends skipped); false when too few. */
bool fitSideLine(const cv::Mat& edges, const cv::Point2f& a, const cv::Point2f& b, double band,
                 cv::Vec4f& line) {
    const cv::Point2f d = b - a;
    const double length = std::hypot(d.x, d.y);
    if (length < 1.0) {
        return false;
    }
    const cv::Point2f u(static_cast<float>(d.x / length), static_cast<float>(d.y / length));
    const cv::Point2f n(-u.y, u.x);
    const int x0 = std::max(0, static_cast<int>(std::floor(std::min(a.x, b.x) - band)));
    const int y0 = std::max(0, static_cast<int>(std::floor(std::min(a.y, b.y) - band)));
    const int x1 = std::min(edges.cols - 1, static_cast<int>(std::ceil(std::max(a.x, b.x) + band)));
    const int y1 = std::min(edges.rows - 1, static_cast<int>(std::ceil(std::max(a.y, b.y) + band)));
    std::vector<cv::Point2f> points;
    for (int y = y0; y <= y1; ++y) {
        const uint8_t* row = edges.ptr<uint8_t>(y);
        for (int x = x0; x <= x1; ++x) {
            if (row[x] == 0) {
                continue;
            }
            const cv::Point2f p(static_cast<float>(x) - a.x, static_cast<float>(y) - a.y);
            const double t = (p.x * u.x + p.y * u.y) / length;
            const double dist = std::abs(p.x * n.x + p.y * n.y);
            if (t > 0.1 && t < 0.9 && dist <= band) {
                points.emplace_back(static_cast<float>(x), static_cast<float>(y));
            }
        }
    }
    if (static_cast<int>(points.size()) < params::kRefineMinPoints) {
        return false;
    }
    cv::fitLine(points, line, cv::DIST_HUBER, 0, 0.01, 0.01);
    return true;
}

/** Line (direction, point) through a and b. */
cv::Vec4f lineThrough(const cv::Point2f& a, const cv::Point2f& b) {
    const cv::Point2f d = b - a;
    const float length = std::max(1e-3f, std::hypot(d.x, d.y));
    return cv::Vec4f(d.x / length, d.y / length, a.x, a.y);
}

/** Corners from four side lines (side i runs from corner i to corner i+1). */
bool cornersFromLines(const std::array<cv::Vec4f, 4>& lines, Quad& out) {
    for (int i = 0; i < 4; ++i) {
        if (!intersect(lines[(i + 3) % 4], lines[i], out[i])) {
            return false;
        }
    }
    return true;
}

/**
 * Slides each side along its outward normal to the offset with the best edge support; ties go
 * to the outermost line, because a document's border is its outermost edge (a card shows a
 * second, inner line from its thickness and print).
 */
std::array<cv::Vec4f, 4> snapSidesOutward(const cv::Mat& edges, const Quad& quad) {
    cv::Point2f centre(0.f, 0.f);
    for (const auto& p : quad) {
        centre += p * 0.25f;
    }
    std::array<cv::Vec4f, 4> lines;
    for (int i = 0; i < 4; ++i) {
        const cv::Point2f& a = quad[i];
        const cv::Point2f& b = quad[(i + 1) % 4];
        cv::Vec4f line = lineThrough(a, b);
        cv::Point2f normal(-line[1], line[0]);
        if ((a - centre).dot(normal) < 0) {
            normal = -normal;
        }
        double bestSupport = -1.0;
        float bestOffset = 0.f;
        const int reach = static_cast<int>(params::kRefineSnapPx);
        for (int step = -reach; step <= reach; ++step) {
            const cv::Point2f shift = normal * static_cast<float>(step);
            const double support = sideSupport(edges, a + shift, b + shift);
            if (support >= bestSupport) {
                bestSupport = support;
                bestOffset = static_cast<float>(step);
            }
        }
        lines[i] = cv::Vec4f(line[0], line[1], line[2] + normal.x * bestOffset,
                             line[3] + normal.y * bestOffset);
    }
    return lines;
}

/**
 * Snaps a coarse quad (hull fit, rounded card corners) onto the edge pixels along its sides:
 * one robust line per side, corners at the intersections, then each side slides out to the
 * outermost well-supported edge.
 */
bool refineQuad(const cv::Mat& edges, const Quad& coarse, Quad& out) {
    Quad quad = coarse;
    for (int pass = 0; pass < 2; ++pass) {
        std::array<cv::Vec4f, 4> lines;
        for (int i = 0; i < 4; ++i) {
            const cv::Point2f& a = quad[i];
            const cv::Point2f& b = quad[(i + 1) % 4];
            const double length = std::hypot(b.x - a.x, b.y - a.y);
            const double band = pass == 0
                                    ? std::clamp(params::kRefineBandFraction * length,
                                                 params::kRefineBandMinPx, params::kRefineBandMaxPx)
                                    : params::kRefineSecondBandPx;
            if (!fitSideLine(edges, a, b, band, lines[i])) {
                lines[i] = lineThrough(a, b); // keep the coarse side when it has too few pixels
            }
        }
        if (!cornersFromLines(lines, quad)) {
            return false;
        }
    }
    if (!cornersFromLines(snapSidesOutward(edges, quad), quad)) {
        return false;
    }
    const float tolX = edges.cols * 0.02f;
    const float tolY = edges.rows * 0.02f;
    for (const auto& p : quad) {
        if (p.x < -tolX || p.y < -tolY || p.x > edges.cols + tolX || p.y > edges.rows + tolY) {
            return false;
        }
    }
    const std::vector<cv::Point2f> pts(quad.begin(), quad.end());
    if (!cv::isContourConvex(pts)) {
        return false;
    }
    out = quad;
    return true;
}

double quadContrast(const cv::Mat& gray, const Quad& quad);

/** Mean luminance inside a quad minus a thin ring just outside it (local contrast). */
double ringContrast(const cv::Mat& gray, const Quad& quad) {
    cv::Mat mask = cv::Mat::zeros(gray.size(), CV_8U);
    std::vector<cv::Point> pts;
    for (const auto& p : quad) {
        pts.emplace_back(static_cast<int>(std::lround(p.x)), static_cast<int>(std::lround(p.y)));
    }
    cv::fillConvexPoly(mask, pts, cv::Scalar(255));
    const int ring = std::max(
        3, static_cast<int>(params::kSmallPageRingFraction * std::hypot(gray.cols, gray.rows)));
    cv::Mat grown;
    cv::dilate(mask, grown,
               cv::getStructuringElement(cv::MORPH_ELLIPSE, cv::Size(2 * ring + 1, 2 * ring + 1)));
    cv::Mat ringMask = grown - mask;
    if (cv::countNonZero(ringMask) == 0) {
        return 0.0;
    }
    return cv::mean(gray, mask)[0] - cv::mean(gray, ringMask)[0];
}

/**
 * A small quad (card, receipt) is only a document when it lies inside the frame, its outline is
 * mostly present and it is clearly brighter than its surroundings; a box printed on a page is not.
 */
bool smallDocumentEvidence(const Candidate& c, const cv::Mat& gray) {
    if (c.borderSides != 0 || c.minSupport < params::kSmallPageMinSupport ||
        c.meanSupport < params::kSmallPageMinMeanSupport) {
        return false;
    }
    const double contrast = ringContrast(gray, c.quad);
    debugLog("  small quad: area meanSupport contrast", c.areaFraction, c.meanSupport, contrast);
    return contrast >= params::kHullMinContrast;
}

bool fromContours(const cv::Mat& mask, const cv::Mat& edges, const cv::Mat& gray, double frameArea,
                  int mode, Candidate& best) {
    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(mask.clone(), contours, mode, cv::CHAIN_APPROX_SIMPLE);
    bool found = false;
    for (const auto& contour : contours) {
        if (contour.size() < 4) {
            continue;
        }
        std::vector<cv::Point> hull;
        cv::convexHull(contour, hull);
        if (cv::contourArea(hull) < params::kMinSmallPageAreaFraction * frameArea) {
            continue;
        }
        Quad quad;
        if (!quadFromContour(contour, quad)) {
            debugLog("  contour rejected (no quad fit): hull area / points / -",
                     cv::contourArea(hull) / frameArea, static_cast<double>(hull.size()), 0.0);
            continue;
        }
        Candidate c = scoreQuad(quad, edges, frameArea);
        // Small quads (cards) are snapped to their edges; large pages keep the hull fit, whose
        // corners already sit on the (possibly curled) outline.
        Quad refined;
        if (c.areaFraction < params::kMinPageAreaFraction && refineQuad(edges, quad, refined)) {
            Candidate r = scoreQuad(refined, edges, frameArea);
            if (r.meanSupport > c.meanSupport) {
                c = r;
            }
        }
        debugLog("  candidate: area minSupport score", c.areaFraction, c.minSupport, c.score);
        // Curled pages bow their top and bottom edges away from the straight quad, so one or
        // two sides may have little support; the mean still has to be convincing.
        if (c.areaFraction < params::kMinSmallPageAreaFraction ||
            c.areaFraction > params::kMaxPageAreaFraction ||
            c.minSupport < params::kMinAcceptSupport ||
            c.meanSupport < params::kMinAcceptMeanSupport) {
            continue;
        }
        if (c.areaFraction < params::kMinPageAreaFraction && !smallDocumentEvidence(c, gray)) {
            continue;
        }
        if (!found || c.score > best.score) {
            best = c;
            found = true;
        }
    }
    return found;
}

/**
 * Text-dense documents (cards, printed pages) become solid blobs once the edge map is closed;
 * an opening then cuts thin attachments (cables, desk seams) that merge the outline with the
 * clutter around it. The outer contours of the blobs are fitted and scored like edge contours.
 */
bool fromFilledEdges(const cv::Mat& edges, const cv::Mat& gray, double frameArea, Candidate& best) {
    // Morphology runs at preview scale (cheap, and scale-independent in effect); the mask is
    // scaled back so the contours are fitted and scored at full working resolution.
    const double scale = std::min(1.0, static_cast<double>(params::kDetectWorkingLongEdge) /
                                           std::max(edges.cols, edges.rows));
    cv::Mat small;
    if (scale < 1.0) {
        cv::resize(edges, small, cv::Size(), scale, scale, cv::INTER_AREA);
        cv::threshold(small, small, 0, 255, cv::THRESH_BINARY);
    } else {
        small = edges;
    }
    const int shortSide = std::min(small.cols, small.rows);
    const int closeSize = std::max(3, static_cast<int>(params::kFillCloseFraction * shortSide));
    const int openSize = std::max(3, static_cast<int>(params::kFillOpenFraction * shortSide));
    cv::Mat closed;
    cv::morphologyEx(small, closed, cv::MORPH_CLOSE,
                     cv::getStructuringElement(cv::MORPH_RECT, cv::Size(closeSize, closeSize)));
    std::vector<std::vector<cv::Point>> outer;
    cv::findContours(closed, outer, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);
    cv::Mat filled = cv::Mat::zeros(small.size(), CV_8U);
    cv::drawContours(filled, outer, -1, cv::Scalar(255), cv::FILLED);
    cv::morphologyEx(filled, filled, cv::MORPH_OPEN,
                     cv::getStructuringElement(cv::MORPH_ELLIPSE, cv::Size(openSize, openSize)));
    if (scale < 1.0) {
        cv::resize(filled, filled, edges.size(), 0, 0, cv::INTER_NEAREST);
    }
    debugDump("filled", filled);
    return fromContours(filled, edges, gray, frameArea, cv::RETR_EXTERNAL, best);
}

/**
 * Long straight edges far from the axes are shadow boundaries or desk seams, never page sides
 * (users frame pages within about 20 degrees): they are erased so they cannot close a quad.
 */
void eraseObliqueLines(cv::Mat& edges) {
    std::vector<cv::Vec4i> segments;
    const int minLength =
        static_cast<int>(params::kObliqueMinLengthFraction * std::min(edges.cols, edges.rows));
    cv::HoughLinesP(edges, segments, 1, CV_PI / 180.0, params::kHoughThreshold, minLength,
                    params::kHoughMaxGap);
    for (const auto& seg : segments) {
        const double angle = std::abs(std::atan2(seg[3] - seg[1], seg[2] - seg[0])) * 180.0 / CV_PI;
        const bool axisAligned = angle < params::kHoughAxisTolerance ||
                                 angle > 180.0 - params::kHoughAxisTolerance ||
                                 std::abs(angle - 90.0) < params::kHoughAxisTolerance;
        if (!axisAligned) {
            cv::line(edges, cv::Point(seg[0], seg[1]), cv::Point(seg[2], seg[3]), cv::Scalar(0),
                     params::kObliqueEraseThickness);
        }
    }
}

/**
 * Low-contrast pass: paper on a desk of almost the same brightness (research R5). Heavy
 * smoothing removes sensor noise, then Canny with low fixed thresholds on each Lab channel
 * picks up the faint luminance and colour step at the page border.
 */
cv::Mat lowContrastEdgeMap(const cv::Mat& bgr) {
    // Text lines would flood the Hough space with long horizontal segments: a median filter
    // wider than the text height removes strokes and sensor noise while the page step survives.
    cv::Mat textless;
    cv::medianBlur(bgr, textless, params::kTextMedianKernel);
    cv::Mat lab;
    cv::cvtColor(textless, lab, cv::COLOR_BGR2Lab);
    cv::Mat channels[3];
    cv::split(lab, channels);
    cv::Mat edges = cv::Mat::zeros(bgr.size(), CV_8U);
    for (auto& channel : channels) {
        cv::Mat smooth, canny;
        cv::GaussianBlur(channel, smooth, cv::Size(0, 0), params::kLowContrastSigma);
        cv::Canny(smooth, canny, params::kLowContrastCannyLow, params::kLowContrastCannyHigh);
        cv::bitwise_or(edges, canny, edges);
    }
    eraseObliqueLines(edges);
    cv::dilate(edges, edges, cv::getStructuringElement(cv::MORPH_RECT, cv::Size(5, 5)));
    return edges;
}

/** Mean luminance inside minus outside a quad: a real page is brighter than its surroundings. */
double quadContrast(const cv::Mat& gray, const Quad& quad) {
    cv::Mat mask = cv::Mat::zeros(gray.size(), CV_8U);
    std::vector<cv::Point> pts;
    for (const auto& p : quad) {
        pts.emplace_back(
            cv::Point(static_cast<int>(std::lround(p.x)), static_cast<int>(std::lround(p.y))));
    }
    cv::fillConvexPoly(mask, pts, cv::Scalar(255));
    const double inside = cv::mean(gray, mask)[0];
    cv::Mat outsideMask;
    cv::bitwise_not(mask, outsideMask);
    if (cv::countNonZero(outsideMask) < static_cast<int>(0.02 * gray.total())) {
        return 255.0; // nothing outside to compare against (page fills the frame)
    }
    const double outside = cv::mean(gray, outsideMask)[0];
    return inside - outside;
}

/**
 * Hough fallback: the page border as four dominant straight lines. Robust to gaps in the edge
 * map (faint borders, corners hidden by a hand): near-horizontal and near-vertical segments are
 * clustered by position, the outermost clusters with enough total length give the four sides,
 * and their intersections the corners.
 */
struct LineCluster {
    double position = 0; // mean y for horizontal clusters, mean x for vertical ones
    double length = 0;
    std::vector<cv::Point2f> points;
};

void addToClusters(std::vector<LineCluster>& clusters, double position, double tolerance,
                   const cv::Vec4i& segment) {
    const double length = std::hypot(segment[2] - segment[0], segment[3] - segment[1]);
    for (auto& cluster : clusters) {
        if (std::abs(cluster.position - position) <= tolerance) {
            cluster.position =
                (cluster.position * cluster.length + position * length) / (cluster.length + length);
            cluster.length += length;
            cluster.points.emplace_back(segment[0], segment[1]);
            cluster.points.emplace_back(segment[2], segment[3]);
            return;
        }
    }
    LineCluster fresh;
    fresh.position = position;
    fresh.length = length;
    fresh.points.emplace_back(segment[0], segment[1]);
    fresh.points.emplace_back(segment[2], segment[3]);
    clusters.push_back(fresh);
}

bool fitClusterLine(const LineCluster& cluster, cv::Vec4f& line) {
    if (cluster.points.size() < 2) {
        return false;
    }
    cv::fitLine(cluster.points, line, cv::DIST_L2, 0, 0.01, 0.01);
    return true;
}

bool pickOuterCluster(const std::vector<LineCluster>& clusters, double minLength, bool low,
                      LineCluster& chosen) {
    bool found = false;
    for (const auto& c : clusters) {
        if (c.length < minLength) {
            continue;
        }
        if (!found || (low ? c.position < chosen.position : c.position > chosen.position)) {
            chosen = c;
            found = true;
        }
    }
    return found;
}

/** Sensitivity of the Hough side search: faint, gappy borders need the relaxed setting. */
struct HoughSettings {
    int threshold;
    double minLengthFraction;
    double maxGap;
    double sideFraction;
    double minMeanSupport;
};

constexpr HoughSettings kHoughStrict{params::kHoughThreshold, params::kHoughMinLengthFraction,
                                     params::kHoughMaxGap, params::kHoughSideFraction,
                                     params::kMinAcceptMeanSupport};
constexpr HoughSettings kHoughRelaxed{
    params::kHoughRelaxedThreshold, params::kHoughRelaxedMinLengthFraction,
    params::kHoughRelaxedMaxGap, params::kHoughRelaxedSideFraction,
    params::kHoughRelaxedMinMeanSupport};

bool houghQuad(const cv::Mat& edges, const HoughSettings& settings, Quad& out) {
    std::vector<cv::Vec4i> segments;
    const int minLength =
        static_cast<int>(settings.minLengthFraction * std::min(edges.cols, edges.rows));
    cv::HoughLinesP(edges, segments, 1, CV_PI / 180.0, settings.threshold, minLength,
                    settings.maxGap);
    std::vector<LineCluster> horizontal;
    std::vector<LineCluster> vertical;
    const double tolH = params::kHoughClusterTolerance * edges.rows;
    const double tolV = params::kHoughClusterTolerance * edges.cols;
    for (const auto& seg : segments) {
        const double dx = seg[2] - seg[0];
        const double dy = seg[3] - seg[1];
        const double angle = std::abs(std::atan2(dy, dx)) * 180.0 / CV_PI; // 0..180
        if (angle < params::kHoughAxisTolerance || angle > 180.0 - params::kHoughAxisTolerance) {
            addToClusters(horizontal, (seg[1] + seg[3]) / 2.0, tolH, seg);
        } else if (std::abs(angle - 90.0) < params::kHoughAxisTolerance) {
            addToClusters(vertical, (seg[0] + seg[2]) / 2.0, tolV, seg);
        }
    }
    LineCluster top, bottom, left, right;
    const double minH = settings.sideFraction * edges.cols;
    const double minV = settings.sideFraction * edges.rows;
    if (std::getenv("PP_DEBUG_DIR") != nullptr) {
        debugLog("hough: segments / horizontal clusters / vertical clusters", segments.size(),
                 horizontal.size(), vertical.size());
        for (const auto& c : horizontal) {
            debugLog("  horizontal cluster y / length / minH", c.position, c.length, minH);
        }
        for (const auto& c : vertical) {
            debugLog("  vertical cluster x / length / minV", c.position, c.length, minV);
        }
    }
    if (!pickOuterCluster(horizontal, minH, true, top) ||
        !pickOuterCluster(horizontal, minH, false, bottom) ||
        !pickOuterCluster(vertical, minV, true, left) ||
        !pickOuterCluster(vertical, minV, false, right)) {
        return false;
    }
    if (bottom.position - top.position < params::kHoughMinExtent * edges.rows ||
        right.position - left.position < params::kHoughMinExtent * edges.cols) {
        return false;
    }
    cv::Vec4f lt, lb, ll, lr;
    if (!fitClusterLine(top, lt) || !fitClusterLine(bottom, lb) || !fitClusterLine(left, ll) ||
        !fitClusterLine(right, lr)) {
        return false;
    }
    Quad q;
    if (!intersect(lt, ll, q[0]) || !intersect(lt, lr, q[1]) || !intersect(lb, lr, q[2]) ||
        !intersect(lb, ll, q[3])) {
        return false;
    }
    out = orderCorners(q);
    const std::vector<cv::Point2f> pts(out.begin(), out.end());
    return cv::isContourConvex(pts);
}

bool houghCandidate(const cv::Mat& edges, double frameArea, const HoughSettings& settings,
                    Candidate& best) {
    Quad quad;
    if (!houghQuad(edges, settings, quad)) {
        return false;
    }
    Candidate c = scoreQuad(quad, edges, frameArea);
    debugLog("hough quad: area minSupport meanSupport", c.areaFraction, c.minSupport,
             c.meanSupport);
    if (c.areaFraction < params::kMinPageAreaFraction ||
        c.areaFraction > params::kMaxPageAreaFraction || c.minSupport < params::kMinAcceptSupport ||
        c.meanSupport < settings.minMeanSupport) {
        return false;
    }
    best = c;
    return true;
}

/** Bright, low-texture region covering almost the whole frame (page fills the photo). */
bool pageFillsFrame(const cv::Mat& gray) {
    cv::Mat small;
    cv::resize(gray, small, cv::Size(), 0.25, 0.25, cv::INTER_AREA);
    cv::Mat mean, sq, var;
    cv::blur(small, mean, cv::Size(7, 7));
    cv::Mat smallF, meanF;
    small.convertTo(smallF, CV_32F);
    mean.convertTo(meanF, CV_32F);
    cv::Mat diff = smallF - meanF;
    cv::Mat diffSq = diff.mul(diff);
    cv::blur(diffSq, var, cv::Size(7, 7));
    long paperLike = 0;
    for (int y = 0; y < small.rows; ++y) {
        for (int x = 0; x < small.cols; ++x) {
            const bool bright = small.at<uint8_t>(y, x) > params::kPaperLuminance * 255.0;
            const bool flat = var.at<float>(y, x) < params::kPaperVariance;
            paperLike += (bright && flat) ? 1 : 0;
        }
    }
    return paperLike >= params::kFillsFrameFraction * small.rows * small.cols;
}

/** Fraction of the union covered by both the quad and the component mask. */
double quadIoU(const Quad& quad, const cv::Mat& component) {
    cv::Mat quadMask = cv::Mat::zeros(component.size(), CV_8U);
    std::vector<cv::Point> pts;
    for (const auto& p : quad) {
        pts.emplace_back(static_cast<int>(std::lround(p.x)), static_cast<int>(std::lround(p.y)));
    }
    cv::fillConvexPoly(quadMask, pts, cv::Scalar(255));
    cv::Mat inter, uni;
    cv::bitwise_and(quadMask, component, inter);
    cv::bitwise_or(quadMask, component, uni);
    const int u = cv::countNonZero(uni);
    return u == 0 ? 0.0 : static_cast<double>(cv::countNonZero(inter)) / u;
}

bool brightRegionCandidate(const cv::Mat& gray, double frameArea, Candidate& out) {
    cv::Mat blurred, bright;
    cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 0);
    cv::threshold(blurred, bright, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);
    // An opening about a tenth of the frame wide removes things touching the page (another
    // notebook, a hand, a phone) without moving the page's own straight sides.
    const int k = std::max(9, static_cast<int>(params::kRegionOpenFraction *
                                               std::min(gray.cols, gray.rows))) |
                  1;
    cv::morphologyEx(bright, bright, cv::MORPH_OPEN,
                     cv::getStructuringElement(cv::MORPH_ELLIPSE, cv::Size(k, k)));
    debugDump("bright", bright);
    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(bright.clone(), contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);
    if (contours.empty()) {
        return false;
    }
    const auto largest =
        std::max_element(contours.begin(), contours.end(), [](const auto& a, const auto& b) {
            return cv::contourArea(a) < cv::contourArea(b);
        });
    if (cv::contourArea(*largest) < params::kMinPageAreaFraction * frameArea) {
        return false;
    }
    Quad quad;
    if (!quadFromContour(*largest, quad)) {
        return false;
    }
    cv::Mat component = cv::Mat::zeros(gray.size(), CV_8U);
    cv::drawContours(component, contours, static_cast<int>(largest - contours.begin()),
                     cv::Scalar(255), cv::FILLED);
    const double iou = quadIoU(quad, component);
    const double contrast = quadContrast(gray, quad);
    const std::vector<cv::Point2f> pts(quad.begin(), quad.end());
    const double area = cv::contourArea(pts) / frameArea;
    int borderSides = 0;
    for (int i = 0; i < 4; ++i) {
        borderSides += onFrameBorder(gray, quad[i], quad[(i + 1) % 4]) ? 1 : 0;
    }
    debugLog("region quad: area IoU contrast", area, iou, contrast);
    // A page cut by the framing leaves the frame on one side; a lit patch of desk usually spans
    // two or more borders, so the region path accepts at most one border side.
    if (iou < params::kRegionMinIoU || contrast < params::kHullMinContrast ||
        area > params::kMaxPageAreaFraction || borderSides > params::kRegionMaxBorderSides) {
        return false;
    }
    out.quad = quad;
    out.areaFraction = area;
    out.minSupport = iou;
    out.meanSupport = iou;
    out.score = area * iou;
    return true;
}

/** Fraction of the quad's area that lies inside `region` (8-bit mask). */
double quadCoverage(const Quad& quad, const cv::Mat& region) {
    cv::Mat quadMask = cv::Mat::zeros(region.size(), CV_8U);
    std::vector<cv::Point> pts;
    for (const auto& p : quad) {
        pts.emplace_back(static_cast<int>(std::lround(p.x)), static_cast<int>(std::lround(p.y)));
    }
    cv::fillConvexPoly(quadMask, pts, cv::Scalar(255));
    const int total = cv::countNonZero(quadMask);
    if (total == 0) {
        return 0.0;
    }
    cv::Mat inside;
    cv::bitwise_and(quadMask, region, inside);
    return static_cast<double>(cv::countNonZero(inside)) / total;
}

/**
 * Hybrid: the best edge quad (contours or filled edges) that lies inside the model's region,
 * covers at least kHybridMinAreaRatio of `referenceArea` and stands out from its surroundings.
 * The mask alone may spill onto cloth or a hand next to the document; edges give the border.
 */
bool edgeQuadInside(const cv::Mat& edges, const cv::Mat& gray, double frameArea,
                    const cv::Mat& region, double referenceArea, Candidate& out) {
    Candidate edgeBest;
    bool edgeFound = fromContours(edges, edges, gray, frameArea, cv::RETR_LIST, edgeBest);
    Candidate filled;
    if (fromFilledEdges(edges, gray, frameArea, filled) &&
        (!edgeFound || filled.score > edgeBest.score)) {
        edgeBest = filled;
        edgeFound = true;
    }
    if (!edgeFound || edgeBest.minSupport < params::kHybridMinSupport ||
        edgeBest.meanSupport < params::kHybridMinMeanSupport) {
        return false;
    }
    const double coverage = quadCoverage(edgeBest.quad, region);
    const double ratio = edgeBest.areaFraction / std::max(1e-6, referenceArea);
    // A box printed on the page has the page's own white around it; a real card or sheet
    // differs clearly from its surroundings.
    const double contrast = std::abs(ringContrast(gray, edgeBest.quad));
    debugLog("hybrid: coverage / area ratio / contrast", coverage, ratio, contrast);
    if (coverage < params::kHybridMinCoverage || ratio < params::kHybridMinAreaRatio ||
        contrast < params::kHullMinContrast) {
        return false;
    }
    out = edgeBest;
    return true;
}

/** Outcome of the model path (specs/004 data-model.md, detector policy). */
enum class ModelVerdict { Found, Absent, Unsure };

/**
 * Model path: the document mask's largest region is fitted with the hull quad, then snapped to
 * the image edges when that improves their support. "Absent" (low presence) skips the classic
 * detector so clutter never produces an outline; "Unsure" falls back to it.
 */
ModelVerdict modelCandidate(DetectorModel& model, const cv::Mat& working, const cv::Mat& edges,
                            double frameArea, Candidate& out, pp_confidence& confidence,
                            float& presence, cv::Mat& regionOut) {
    cv::Mat probability;
    if (!model.infer(working, probability, presence)) {
        return ModelVerdict::Unsure;
    }
    debugLog("model: presence", presence, 0, 0);
    if (presence < params::kModelAbsentPresence) {
        return ModelVerdict::Absent;
    }
    cv::Mat upscaled;
    cv::resize(probability, upscaled, working.size(), 0, 0, cv::INTER_LINEAR);
    cv::Mat region = upscaled > params::kModelMaskThreshold;
    debugDump("model-mask", region);
    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(region, contours, cv::RETR_EXTERNAL, cv::CHAIN_APPROX_SIMPLE);
    const std::vector<cv::Point>* largest = nullptr;
    double largestArea = 0;
    for (const auto& c : contours) {
        const double a = cv::contourArea(c);
        if (a > largestArea) {
            largestArea = a;
            largest = &c;
        }
    }
    if (largest == nullptr || largestArea < params::kMinSmallPageAreaFraction * frameArea) {
        return ModelVerdict::Unsure;
    }
    cv::Mat component = cv::Mat::zeros(region.size(), CV_8U);
    cv::drawContours(component, std::vector<std::vector<cv::Point>>{*largest}, 0, cv::Scalar(255),
                     cv::FILLED);
    regionOut = component;
    Quad quad;
    if (!quadFromContour(*largest, quad)) {
        return ModelVerdict::Unsure;
    }
    Candidate c = scoreQuad(quad, edges, frameArea);
    Quad refined;
    if (refineQuad(edges, quad, refined)) {
        double shift = 0;
        for (int i = 0; i < 4; ++i) {
            shift = std::max(shift, static_cast<double>(cv::norm(refined[i] - quad[i])));
        }
        Candidate r = scoreQuad(refined, edges, frameArea);
        const double diagonal = std::hypot(working.cols, working.rows);
        if (r.meanSupport > c.meanSupport && shift <= params::kModelSnapMaxShift * diagonal) {
            c = r;
        }
    }
    const double iou = quadIoU(c.quad, component);
    debugLog("model quad: area iou support", c.areaFraction, iou, c.meanSupport);
    if (c.areaFraction > params::kMaxPageAreaFraction + 0.01) {
        return ModelVerdict::Unsure;
    }
    if (presence >= params::kModelHighPresence && iou >= params::kModelHighIoU &&
        c.areaFraction >= params::kMinSmallPageAreaFraction) {
        confidence = PP_CONF_HIGH;
    } else if (presence >= params::kModelLowPresence && iou >= params::kModelLowIoU) {
        confidence = PP_CONF_LOW;
    } else {
        return ModelVerdict::Unsure;
    }
    out = c;
    return ModelVerdict::Found;
}
} // namespace

Quad fullFrameQuad(const pp_image_rgba& photo) {
    const float w = static_cast<float>(photo.width);
    const float h = static_cast<float>(photo.height);
    return Quad{cv::Point2f(0.f, 0.f), cv::Point2f(w, 0.f), cv::Point2f(w, h), cv::Point2f(0.f, h)};
}

namespace {

/** The edge-based detector (002/003): contours, filled edges, Hough, faint and bright passes. */
DetectResult classicDetect(AnalyzeContext& ctx, const cv::Mat& gray, const cv::Mat& edges,
                           double frameArea, Quad& workingQuad, bool& quadFound) {
    DetectResult result;
    result.quad = fullFrameQuad(*ctx.photo);
    result.confidence = PP_CONF_NOT_FOUND;
    result.source = PP_SRC_FULL_FRAME;
    quadFound = false;
    Candidate best;
    bool found = fromContours(edges, edges, gray, frameArea, cv::RETR_LIST, best);
    {
        Candidate filledCandidate;
        if (fromFilledEdges(edges, gray, frameArea, filledCandidate) &&
            (!found || filledCandidate.score > best.score)) {
            debugLog("filled quad: area support score", filledCandidate.areaFraction,
                     filledCandidate.minSupport, filledCandidate.score);
            best = filledCandidate;
            found = true;
        }
    }
    bool lowConfidenceOnly = false;
    checkCancel(ctx.cancel);
    if (!found) {
        found = houghCandidate(edges, frameArea, kHoughStrict, best);
        checkCancel(ctx.cancel);
    }
    if (found) {
        debugLog("primary quad: area support score", best.areaFraction, best.minSupport,
                 best.score);
    }

    if (!found) {
        // Low-contrast pass (paper on a pale desk): smoothed, low-threshold edges in Lab.
        cv::Mat faint = lowContrastEdgeMap(ctx.working);
        checkCancel(ctx.cancel);
        debugDump("edges-faint", faint);
        Candidate faintCandidate;
        const bool faintFound =
            fromContours(faint, faint, gray, frameArea, cv::RETR_LIST, faintCandidate) ||
            houghCandidate(faint, frameArea, kHoughRelaxed, faintCandidate);
        if (faintFound) {
            debugLog("faint quad: area support contrast", faintCandidate.areaFraction,
                     faintCandidate.minSupport, quadContrast(gray, faintCandidate.quad));
        }
        if (faintFound &&
            quadContrast(gray, faintCandidate.quad) >= params::kLowContrastMinContrast) {
            best = faintCandidate;
            found = true;
            lowConfidenceOnly = true;
        }
    }

    checkCancel(ctx.cancel);
    if (!found) {
        // Bright-region fit: the largest Otsu-bright blob, for pages whose outline is broken,
        // cut by the frame or joined to an open book. Evidence is the region itself: the quad
        // must cover it closely (IoU) and be clearly brighter than its surroundings.
        Candidate regionCandidate;
        if (brightRegionCandidate(gray, frameArea, regionCandidate)) {
            best = regionCandidate;
            found = true;
            lowConfidenceOnly = true;
        }
    }

    if (found) {
        if (std::getenv("PP_DEBUG_DIR") != nullptr) {
            cv::Mat overlay = gray / 2;
            for (int i = 0; i < 4; ++i) {
                cv::line(overlay, best.quad[i], best.quad[(i + 1) % 4], cv::Scalar(255), 4);
            }
            debugDump("chosen-quad", overlay);
        }
        for (int i = 0; i < 4; ++i) {
            result.quad[i] = best.quad[i] * static_cast<float>(1.0 / ctx.workingScale);
        }
        const bool high = !lowConfidenceOnly && best.minSupport >= params::kHighConfidenceSupport &&
                          best.areaFraction >= params::kHighConfidenceArea;
        result.confidence = high ? PP_CONF_HIGH : PP_CONF_LOW;
        result.source = PP_SRC_DETECTED;
        workingQuad = best.quad;
        quadFound = true;
        return result;
    }

    if (pageFillsFrame(gray)) {
        result.confidence = PP_CONF_LOW;
        result.source = PP_SRC_DETECTED;
    }
    return result;
}

/** IoU of two quads rasterised at `size`. */
double quadPairIoU(const Quad& a, const Quad& b, cv::Size size) {
    cv::Mat mask = cv::Mat::zeros(size, CV_8U);
    std::vector<cv::Point> pts;
    for (const auto& p : b) {
        pts.emplace_back(static_cast<int>(std::lround(p.x)), static_cast<int>(std::lround(p.y)));
    }
    cv::fillConvexPoly(mask, pts, cv::Scalar(255));
    return quadIoU(a, mask);
}

} // namespace

DetectResult detectPage(AnalyzeContext& ctx) {
    cv::Mat gray;
    cv::cvtColor(ctx.working, gray, cv::COLOR_BGR2GRAY);
    const double frameArea = static_cast<double>(gray.rows) * gray.cols;
    checkCancel(ctx.cancel);
    cv::Mat edges = edgeMap(gray);
    debugDump("working", ctx.working);
    debugDump("edges-primary", edges);
    checkCancel(ctx.cancel);

    Quad classicQuad;
    bool classicFound = false;
    DetectResult classic = classicDetect(ctx, gray, edges, frameArea, classicQuad, classicFound);
    if (ctx.detector == nullptr) {
        return classic;
    }
    checkCancel(ctx.cancel);

    // Model (specs/004): decides whether a document is there and where; the classic detector's
    // verified edges win when it is confident or agrees with the model.
    Candidate modelBest;
    pp_confidence modelConfidence = PP_CONF_NOT_FOUND;
    cv::Mat region;
    float presence = 0.f;
    ModelVerdict verdict = modelCandidate(*ctx.detector, ctx.working, edges, frameArea, modelBest,
                                          modelConfidence, presence, region);
    checkCancel(ctx.cancel);
    classic.presence = presence;
    if (classic.confidence == PP_CONF_HIGH) {
        return classic;
    }
    DetectResult result;
    result.quad = fullFrameQuad(*ctx.photo);
    result.presence = presence;
    result.detector = PP_DETECTOR_MODEL;
    if (verdict == ModelVerdict::Absent) {
        return result; // no document: no outline, whatever the clutter looks like
    }
    if (verdict == ModelVerdict::Found) {
        Candidate edgeQuad;
        if (edgeQuadInside(edges, gray, frameArea, region, modelBest.areaFraction, edgeQuad)) {
            modelBest = edgeQuad;
        }
    } else if (!region.empty()) {
        // Unsure (the region is not quad-shaped, e.g. a hand or cloth merged with the card):
        // a clean edge quad inside the region still gives the document.
        Candidate edgeQuad;
        if (!edgeQuadInside(edges, gray, frameArea, region, cv::countNonZero(region) / frameArea,
                            edgeQuad)) {
            return classic;
        }
        modelBest = edgeQuad;
        modelConfidence = PP_CONF_LOW;
    } else {
        return classic;
    }
    const Quad* chosen = &modelBest.quad;
    if (classicFound &&
        quadPairIoU(classicQuad, modelBest.quad, gray.size()) >= params::kModelAgreeIoU) {
        chosen = &classicQuad; // same document: keep the classic detector's exact edges
    }
    for (int i = 0; i < 4; ++i) {
        result.quad[i] = (*chosen)[i] * static_cast<float>(1.0 / ctx.workingScale);
    }
    result.confidence = modelConfidence;
    result.source = PP_SRC_DETECTED;
    return result;
}

} // namespace pp
