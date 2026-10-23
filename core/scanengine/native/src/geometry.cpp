#include "cancel.hpp"
#include "params.hpp"
#include "stages.hpp"

#include <algorithm>
#include <cmath>
#include <opencv2/imgproc.hpp>

namespace pp {

Quad orderCorners(const Quad& quad) {
    // Sort by the sum (TL smallest, BR largest) and the difference (TR largest, BL smallest).
    Quad ordered;
    float minSum = 1e30f, maxSum = -1e30f, minDiff = 1e30f, maxDiff = -1e30f;
    for (const auto& p : quad) {
        const float sum = p.x + p.y;
        const float diff = p.x - p.y;
        if (sum < minSum) {
            minSum = sum;
            ordered[0] = p;
        }
        if (sum > maxSum) {
            maxSum = sum;
            ordered[2] = p;
        }
        if (diff > maxDiff) {
            maxDiff = diff;
            ordered[1] = p;
        }
        if (diff < minDiff) {
            minDiff = diff;
            ordered[3] = p;
        }
    }
    return ordered;
}

namespace {

double length(const cv::Point2f& a, const cv::Point2f& b) {
    return cv::norm(a - b);
}

} // namespace

namespace {

double sideRatio(const Quad& quad) {
    const double top = length(quad[0], quad[1]);
    const double bottom = length(quad[3], quad[2]);
    const double left = length(quad[0], quad[3]);
    const double right = length(quad[1], quad[2]);
    const double width = (top + bottom) / 2.0;
    const double height = (left + right) / 2.0;
    if (height <= 1.0) {
        return 1.0;
    }
    return width / height;
}

} // namespace

/**
 * Physical aspect ratio (width / height) of the page from its projected corners, following
 * Zhang & He ("Whiteboard scanning and image enhancement"): the focal length is recovered from
 * the two vanishing directions when the perspective is strong enough; otherwise the averaged
 * side lengths are used (frontal shots make the estimate ill-conditioned).
 */
double estimateAspect(const Quad& quad, int imageWidth, int imageHeight) {
    const double fallback = std::clamp(sideRatio(quad), 0.1, 10.0);
    const double u0 = imageWidth / 2.0;
    const double v0 = imageHeight / 2.0;
    // m1 = TL, m2 = TR, m3 = BL, m4 = BR as homogeneous vectors around the principal point.
    const cv::Vec3d m1(quad[0].x - u0, quad[0].y - v0, 1.0);
    const cv::Vec3d m2(quad[1].x - u0, quad[1].y - v0, 1.0);
    const cv::Vec3d m3(quad[3].x - u0, quad[3].y - v0, 1.0);
    const cv::Vec3d m4(quad[2].x - u0, quad[2].y - v0, 1.0);
    const double k2 =
        ((m1 - m4).cross(m3)).dot(m2.cross(m3)) / (((m2 - m4).cross(m3)).dot(m2.cross(m3)));
    const double k3 =
        ((m1 - m4).cross(m2)).dot(m3.cross(m2)) / (((m3 - m4).cross(m2)).dot(m3.cross(m2)));
    if (!std::isfinite(k2) || !std::isfinite(k3)) {
        return fallback;
    }
    const cv::Vec3d n2 = k2 * m2 - m1;
    const cv::Vec3d n3 = k3 * m3 - m1;
    // Near-frontal: the perspective carries no focal information; trust the side lengths.
    if (std::abs(k2 - 1.0) < 1e-3 || std::abs(k3 - 1.0) < 1e-3 || std::abs(n2[2]) < 1e-6 ||
        std::abs(n3[2]) < 1e-6) {
        return fallback;
    }
    const double f2 = -(n2[0] * n3[0] + n2[1] * n3[1]) / (n2[2] * n3[2]);
    if (!(f2 > 0.0)) {
        return fallback;
    }
    const double ratio = std::sqrt((n2[0] * n2[0] + n2[1] * n2[1]) / f2 + n2[2] * n2[2]) /
                         std::sqrt((n3[0] * n3[0] + n3[1] * n3[1]) / f2 + n3[2] * n3[2]);
    if (!std::isfinite(ratio) || ratio <= 0.0) {
        return fallback;
    }
    // Guard against wild focal estimates on nearly-rectangular quads: stay within 25% of the
    // side-length ratio, which is accurate when the perspective is weak.
    const double clamped = std::clamp(ratio, fallback * 0.75, fallback * 1.25);
    return std::clamp(clamped, 0.1, 10.0);
}

Geometry planGeometry(const AnalyzeContext& ctx, const Quad& source, int pageLongEdge) {
    Geometry geometry;
    // Shrink slightly towards the centre: a corner a pixel outside the page would otherwise
    // drag desk pixels onto the page border.
    cv::Point2f centre(0.f, 0.f);
    for (const auto& p : source) {
        centre += p * 0.25f;
    }
    Quad quad;
    for (int i = 0; i < 4; ++i) {
        quad[i] = source[i] + (centre - source[i]) * static_cast<float>(params::kQuadInset);
    }
    geometry.quad = quad;
    geometry.aspect = estimateAspect(quad, ctx.photo->width, ctx.photo->height);
    // Do not upscale beyond the source resolution of the quad.
    const double sourceWidth = (length(quad[0], quad[1]) + length(quad[3], quad[2])) / 2.0;
    const double sourceHeight = (length(quad[0], quad[3]) + length(quad[1], quad[2])) / 2.0;
    const double sourceLong = std::max(sourceWidth, sourceHeight);
    const double longEdge = std::min<double>(pageLongEdge, std::max(sourceLong, 640.0));
    if (geometry.aspect >= 1.0) {
        geometry.pageSize = cv::Size(static_cast<int>(std::lround(longEdge)),
                                     static_cast<int>(std::lround(longEdge / geometry.aspect)));
    } else {
        geometry.pageSize = cv::Size(static_cast<int>(std::lround(longEdge * geometry.aspect)),
                                     static_cast<int>(std::lround(longEdge)));
    }
    geometry.pageSize.width = std::max(geometry.pageSize.width, 16);
    geometry.pageSize.height = std::max(geometry.pageSize.height, 16);
    return geometry;
}

namespace {

cv::Mat homographyFor(const Geometry& geometry, const cv::Size& target) {
    cv::Point2f src[4] = {geometry.quad[0], geometry.quad[1], geometry.quad[2], geometry.quad[3]};
    const float w = static_cast<float>(target.width);
    const float h = static_cast<float>(target.height);
    cv::Point2f dst[4] = {cv::Point2f(0.f, 0.f), cv::Point2f(w, 0.f), cv::Point2f(w, h),
                          cv::Point2f(0.f, h)};
    cv::Mat homography = cv::getPerspectiveTransform(src, dst);
    if (std::abs(geometry.deskewDeg) > 0.0) {
        // Residual tilt: rotate about the page centre after the perspective map.
        cv::Mat rotation =
            cv::getRotationMatrix2D(cv::Point2f(w / 2.f, h / 2.f), geometry.deskewDeg, 1.0);
        cv::Mat rotation3 = cv::Mat::eye(3, 3, CV_64F);
        rotation.copyTo(rotation3(cv::Rect(0, 0, 3, 2)));
        homography = rotation3 * homography;
    }
    return homography;
}

} // namespace

cv::Mat warpPreview(const AnalyzeContext& ctx, const Geometry& geometry, int longEdge) {
    const double scale =
        static_cast<double>(longEdge) / std::max(geometry.pageSize.width, geometry.pageSize.height);
    cv::Size previewSize(std::max(16, static_cast<int>(geometry.pageSize.width * scale)),
                         std::max(16, static_cast<int>(geometry.pageSize.height * scale)));
    // Map from the working image: scale the quad into working coordinates.
    Geometry scaled = geometry;
    scaled.deskewDeg = 0.0;
    for (auto& p : scaled.quad) {
        p *= static_cast<float>(ctx.workingScale);
    }
    cv::Mat homography = homographyFor(scaled, previewSize);
    cv::Mat preview;
    cv::warpPerspective(ctx.working, preview, homography, previewSize, cv::INTER_AREA,
                        cv::BORDER_REPLICATE);
    return preview;
}

cv::Mat warpPage(const AnalyzeContext& ctx, const Geometry& geometry) {
    const cv::Size target = geometry.pageSize;
    cv::Mat homography = homographyFor(geometry, target);
    cv::Mat inverse = homography.inv();
    cv::Mat pageRgba(target, CV_8UC4);
    // Banded warp with the inverse map so cancellation is observed between bands (research R11).
    const int bands = params::kWarpBands;
    const int bandHeight = (target.height + bands - 1) / bands;
    const double sourceLong = std::max(cv::norm(geometry.quad[0] - geometry.quad[1]),
                                       cv::norm(geometry.quad[0] - geometry.quad[3]));
    const int interpolation =
        sourceLong > 2.0 * std::max(target.width, target.height) ? cv::INTER_AREA : cv::INTER_CUBIC;
    for (int band = 0; band < bands; ++band) {
        checkCancel(ctx.cancel);
        const int y0 = band * bandHeight;
        const int y1 = std::min(target.height, y0 + bandHeight);
        if (y0 >= y1) {
            break;
        }
        // With WARP_INVERSE_MAP the map goes dst -> src; translate the band to the origin first.
        cv::Mat shift = cv::Mat::eye(3, 3, CV_64F);
        shift.at<double>(1, 2) = y0;
        cv::Mat bandMap = inverse * shift;
        cv::Mat dst = pageRgba.rowRange(y0, y1);
        cv::warpPerspective(ctx.photoView, dst, bandMap, dst.size(),
                            interpolation | cv::WARP_INVERSE_MAP, cv::BORDER_REPLICATE);
    }
    cv::Mat page;
    cv::cvtColor(pageRgba, page, cv::COLOR_RGBA2BGR);
    return page;
}

} // namespace pp
