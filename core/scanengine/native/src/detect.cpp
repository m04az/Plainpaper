#include "cancel.hpp"
#include "debug.hpp"
#include "params.hpp"
#include "stages.hpp"

#include <algorithm>
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

bool quadFromContour(const std::vector<cv::Point>& contour, Quad& out) {
    std::vector<cv::Point> hull;
    cv::convexHull(contour, hull);
    const double perimeter = cv::arcLength(hull, true);
    std::vector<cv::Point> approx;
    cv::approxPolyDP(hull, approx, params::kApproxEpsilon * perimeter, true);
    if (approx.size() == 4) {
        Quad q;
        for (int i = 0; i < 4; ++i) {
            q[i] = cv::Point2f(static_cast<float>(approx[i].x), static_cast<float>(approx[i].y));
        }
        out = orderCorners(q);
        return true;
    }
    if (approx.size() > 4 && approx.size() <= 8) {
        // Rounded or clipped corners: take the four extreme hull points instead.
        Quad q;
        for (int i = 0; i < 4; ++i) {
            q[i] = cv::Point2f(static_cast<float>(approx[0].x), static_cast<float>(approx[0].y));
        }
        for (const auto& p : approx) {
            const cv::Point2f pf(static_cast<float>(p.x), static_cast<float>(p.y));
            if (pf.x + pf.y < q[0].x + q[0].y)
                q[0] = pf;
            if (pf.x - pf.y > q[1].x - q[1].y)
                q[1] = pf;
            if (pf.x + pf.y > q[2].x + q[2].y)
                q[2] = pf;
            if (pf.x - pf.y < q[3].x - q[3].y)
                q[3] = pf;
        }
        out = orderCorners(q);
        return cv::isContourConvex(std::vector<cv::Point2f>(q.begin(), q.end()));
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
    for (int i = 0; i < 4; ++i) {
        const double s = sideSupport(edges, quad[i], quad[(i + 1) % 4]);
        minSupport = std::min(minSupport, s);
        meanSupport += s / 4.0;
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

bool fromContours(const cv::Mat& mask, const cv::Mat& edges, double frameArea, int mode,
                  Candidate& best) {
    std::vector<std::vector<cv::Point>> contours;
    cv::findContours(mask.clone(), contours, mode, cv::CHAIN_APPROX_SIMPLE);
    bool found = false;
    for (const auto& contour : contours) {
        if (contour.size() < 4) {
            continue;
        }
        std::vector<cv::Point> hull;
        cv::convexHull(contour, hull);
        if (cv::contourArea(hull) < params::kMinPageAreaFraction * frameArea) {
            continue;
        }
        Quad quad;
        if (!quadFromContour(contour, quad)) {
            continue;
        }
        Candidate c = scoreQuad(quad, edges, frameArea);
        debugLog("  candidate: area minSupport score", c.areaFraction, c.minSupport, c.score);
        // Curled pages bow their top and bottom edges away from the straight quad, so one or
        // two sides may have little support; the mean still has to be convincing.
        if (c.areaFraction < params::kMinPageAreaFraction ||
            c.areaFraction > params::kMaxPageAreaFraction ||
            c.minSupport < params::kMinAcceptSupport ||
            c.meanSupport < params::kMinAcceptMeanSupport) {
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

} // namespace

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

    cv::Mat gray;
    cv::cvtColor(ctx.working, gray, cv::COLOR_BGR2GRAY);
    const double frameArea = static_cast<double>(gray.rows) * gray.cols;
    checkCancel(ctx.cancel);
    cv::Mat edges = edgeMap(gray);
    debugDump("working", ctx.working);
    debugDump("edges-primary", edges);
    checkCancel(ctx.cancel);

    Candidate best;
    bool found = fromContours(edges, edges, frameArea, cv::RETR_LIST, best);
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
            fromContours(faint, faint, frameArea, cv::RETR_LIST, faintCandidate) ||
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
        // Bright-region hull: the largest Otsu-bright blob, for pages whose outline is broken.
        // It must be clearly brighter than its surroundings, or it is just noise split in two.
        cv::Mat blurred, bright;
        cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 0);
        cv::threshold(blurred, bright, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);
        cv::morphologyEx(bright, bright, cv::MORPH_OPEN,
                         cv::getStructuringElement(cv::MORPH_ELLIPSE, cv::Size(9, 9)));
        Candidate hullCandidate;
        if (fromContours(bright, edges, frameArea, cv::RETR_EXTERNAL, hullCandidate) &&
            quadContrast(gray, hullCandidate.quad) >= params::kHullMinContrast) {
            best = hullCandidate;
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
        return result;
    }

    if (pageFillsFrame(gray)) {
        result.confidence = PP_CONF_LOW;
        result.source = PP_SRC_DETECTED;
    }
    return result;
}

} // namespace pp
