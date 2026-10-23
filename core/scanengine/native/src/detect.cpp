#include "params.hpp"
#include "stages.hpp"

#include <algorithm>
#include <cmath>
#include <opencv2/imgproc.hpp>
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

cv::Mat edgeMap(const cv::Mat& gray) {
    cv::Mat blurred;
    cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 0);
    const int median = medianOf(blurred);
    const double lower = std::max(0.0, params::kCannyLowFactor * median);
    const double upper = std::min(255.0, params::kCannyHighFactor * median);
    cv::Mat canny;
    cv::Canny(blurred, canny, lower, upper);
    // Bright paper against a darker desk: the Otsu boundary, which survives low local contrast.
    cv::Mat otsu;
    cv::threshold(blurred, otsu, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);
    cv::Mat gradient;
    cv::morphologyEx(otsu, gradient, cv::MORPH_GRADIENT,
                     cv::getStructuringElement(cv::MORPH_RECT, cv::Size(3, 3)));
    cv::Mat edges;
    cv::bitwise_or(canny, gradient, edges);
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
    c.score = c.areaFraction * meanSupport * rectangularity(quad);
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
        if (c.areaFraction < params::kMinPageAreaFraction ||
            c.areaFraction > params::kMaxPageAreaFraction) {
            continue;
        }
        if (!found || c.score > best.score) {
            best = c;
            found = true;
        }
    }
    return found;
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
    cv::Mat edges = edgeMap(gray);

    Candidate best;
    bool found = fromContours(edges, edges, frameArea, cv::RETR_LIST, best);

    if (!found) {
        // Bright-region hull: the largest Otsu-bright blob, for pages whose outline is broken.
        cv::Mat blurred, bright;
        cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 0);
        cv::threshold(blurred, bright, 0, 255, cv::THRESH_BINARY | cv::THRESH_OTSU);
        cv::morphologyEx(bright, bright, cv::MORPH_OPEN,
                         cv::getStructuringElement(cv::MORPH_ELLIPSE, cv::Size(9, 9)));
        Candidate hullCandidate;
        if (fromContours(bright, edges, frameArea, cv::RETR_EXTERNAL, hullCandidate)) {
            best = hullCandidate;
            best.minSupport = std::min(best.minSupport, params::kHighConfidenceSupport - 0.01);
            found = true;
        }
    }

    if (found) {
        for (int i = 0; i < 4; ++i) {
            result.quad[i] = best.quad[i] * static_cast<float>(1.0 / ctx.workingScale);
        }
        const bool high = best.minSupport >= params::kHighConfidenceSupport &&
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
