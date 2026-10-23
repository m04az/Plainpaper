#include "cancel.hpp"
#include "errors.hpp"
#include "params.hpp"
#include "stages.hpp"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <opencv2/imgproc.hpp>
#include <vector>

// Style rendering and page framing (research R8, R9).
namespace pp {

namespace {

/** Fits `page` into an outWidth x outHeight white frame, centred, preserving aspect (FR-008). */
cv::Mat frameOnWhite(const cv::Mat& page, int outWidth, int outHeight, pp_fit fit) {
    const int channels = page.channels();
    cv::Mat canvas(outHeight, outWidth, page.type(),
                   channels == 1 ? cv::Scalar(255) : cv::Scalar(255, 255, 255, 255));
    const double scale = fit == PP_FIT_NATURAL
                             ? static_cast<double>(outWidth) / page.cols
                             : std::min(static_cast<double>(outWidth) / page.cols,
                                        static_cast<double>(outHeight) / page.rows);
    cv::Size fitted(std::max(1, static_cast<int>(page.cols * scale)),
                    std::max(1, static_cast<int>(page.rows * scale)));
    fitted.width = std::min(fitted.width, outWidth);
    fitted.height = std::min(fitted.height, outHeight);
    cv::Mat resized;
    cv::resize(page, resized, fitted, 0, 0, scale < 1.0 ? cv::INTER_AREA : cv::INTER_CUBIC);
    const int x = (outWidth - fitted.width) / 2;
    const int y = (outHeight - fitted.height) / 2;
    resized.copyTo(canvas(cv::Rect(x, y, fitted.width, fitted.height)));
    return canvas;
}

void exportImage(const cv::Mat& image, pp_output_image* out) {
    const int channels = image.channels();
    const size_t stride = static_cast<size_t>(image.cols) * channels;
    const size_t bytes = stride * image.rows;
    auto* pixels = static_cast<uint8_t*>(std::malloc(bytes));
    if (pixels == nullptr) {
        throw Error{PP_ERR_OUT_OF_MEMORY, "output buffer"};
    }
    cv::Mat target(image.rows, image.cols, image.type(), pixels, stride);
    image.copyTo(target);
    out->pixels = pixels;
    out->width = image.cols;
    out->height = image.rows;
    out->channels = channels;
    out->stride_bytes = static_cast<int32_t>(stride);
}

/**
 * Sauvola threshold on the normalised luminance with an "ink floor": flat regions (local
 * std-dev below kInkFloorStdDev) are paper whatever their mean, so blank areas stay clean.
 * Returns an 8-bit mask where ink = 255.
 */
cv::Mat sauvolaInk(const cv::Mat& gray, volatile int32_t* cancel) {
    const int window = std::max(
        15, static_cast<int>(params::kSauvolaWindow * gray.cols / params::kSauvolaReferenceWidth));
    const int w = (window % 2 == 0) ? window + 1 : window;
    cv::Mat mean, sq, meanF, sqF;
    cv::boxFilter(gray, mean, CV_32F, cv::Size(w, w), cv::Point(-1, -1), true, cv::BORDER_REFLECT);
    checkCancel(cancel);
    cv::Mat grayF;
    gray.convertTo(grayF, CV_32F);
    cv::boxFilter(grayF.mul(grayF), sq, CV_32F, cv::Size(w, w), cv::Point(-1, -1), true,
                  cv::BORDER_REFLECT);
    checkCancel(cancel);
    cv::Mat variance = sq - mean.mul(mean);
    cv::Mat stddev;
    cv::sqrt(cv::max(variance, 0.0f), stddev);
    // T = m * (1 + k * (s / R - 1))
    cv::Mat threshold =
        mean.mul(1.0f + static_cast<float>(params::kSauvolaK) *
                            (stddev / static_cast<float>(params::kSauvolaR) - 1.0f));
    cv::Mat ink = (grayF < threshold) & (stddev > static_cast<float>(params::kInkFloorStdDev));
    // Also require a minimum absolute darkness so JPEG ringing on white paper never becomes ink.
    cv::Mat darkEnough = grayF < static_cast<float>(params::kInkMaxLuminance);
    cv::bitwise_and(ink, darkEnough, ink);
    return ink;
}

/**
 * Removes ink components that touch the page border and are both long and thick: desk
 * slivers left beside a curled or slightly mis-detected edge. Ruled lines and table borders
 * are thin and survive; text never spans a border that way.
 */
void dropBorderSlivers(cv::Mat& ink) {
    cv::Mat labels, stats, centroids;
    const int count = cv::connectedComponentsWithStats(ink, labels, stats, centroids, 8, CV_32S);
    const int longEdge = std::max(ink.cols, ink.rows);
    const int minThickness = static_cast<int>(params::kSliverMinThickness * longEdge);
    std::vector<uint8_t> drop(static_cast<size_t>(count), 0);
    for (int i = 1; i < count; ++i) {
        const int x = stats.at<int>(i, cv::CC_STAT_LEFT);
        const int y = stats.at<int>(i, cv::CC_STAT_TOP);
        const int w = stats.at<int>(i, cv::CC_STAT_WIDTH);
        const int h = stats.at<int>(i, cv::CC_STAT_HEIGHT);
        const bool touchesBorder = x == 0 || y == 0 || x + w == ink.cols || y + h == ink.rows;
        const bool horizontalBand = w >= params::kSliverMinExtent * ink.cols && h >= minThickness;
        const bool verticalBand = h >= params::kSliverMinExtent * ink.rows && w >= minThickness;
        if (touchesBorder && (horizontalBand || verticalBand)) {
            drop[static_cast<size_t>(i)] = 1;
        }
    }
    for (int y = 0; y < ink.rows; ++y) {
        const int* label = labels.ptr<int>(y);
        uint8_t* row = ink.ptr<uint8_t>(y);
        for (int x = 0; x < ink.cols; ++x) {
            if (drop[static_cast<size_t>(label[x])] != 0) {
                row[x] = 0;
            }
        }
    }
}

cv::Mat renderScan(const cv::Mat& normalizedBgr, volatile int32_t* cancel) {
    cv::Mat gray;
    cv::cvtColor(normalizedBgr, gray, cv::COLOR_BGR2GRAY);
    cv::Mat ink = sauvolaInk(gray, cancel);
    cv::medianBlur(ink, ink, 3);
    dropBorderSlivers(ink);
    checkCancel(cancel);
    // Anti-aliased composite: soften the mask edge so strokes stay crisp but not jagged.
    cv::Mat alpha;
    ink.convertTo(alpha, CV_32F, 1.0 / 255.0);
    cv::GaussianBlur(alpha, alpha, cv::Size(0, 0), params::kInkEdgeSigma);
    cv::Mat out;
    cv::Mat white = cv::Mat::ones(alpha.size(), CV_32F) * 255.0f;
    cv::Mat composite = white.mul(1.0f - alpha);
    composite.convertTo(out, CV_8U);
    return out;
}

cv::Mat toneCurve(const cv::Mat& gray) {
    cv::Mat lut(1, 256, CV_8U);
    for (int i = 0; i < 256; ++i) {
        lut.at<uint8_t>(i) =
            cv::saturate_cast<uint8_t>(255.0 * std::pow(i / 255.0, params::kToneGamma));
    }
    cv::Mat out;
    cv::LUT(gray, lut, out);
    return out;
}

cv::Mat renderGrayscale(const cv::Mat& normalizedBgr) {
    cv::Mat gray;
    cv::cvtColor(normalizedBgr, gray, cv::COLOR_BGR2GRAY);
    return toneCurve(gray);
}

/** White paper; stamps, signatures and logos keep their hue with a little extra saturation. */
cv::Mat renderColorDocument(const cv::Mat& normalizedBgr, volatile int32_t* cancel) {
    cv::Mat hsv;
    cv::cvtColor(normalizedBgr, hsv, cv::COLOR_BGR2HSV);
    checkCancel(cancel);
    const int paperValue = static_cast<int>(params::kPaperLuminanceFraction * 255.0);
    for (int y = 0; y < hsv.rows; ++y) {
        cv::Vec3b* row = hsv.ptr<cv::Vec3b>(y);
        const cv::Vec3b* src = normalizedBgr.ptr<cv::Vec3b>(y);
        for (int x = 0; x < hsv.cols; ++x) {
            const int chroma = std::max({src[x][0], src[x][1], src[x][2]}) -
                               std::min({src[x][0], src[x][1], src[x][2]});
            if (chroma < params::kPaperChroma && row[x][2] >= paperValue) {
                row[x][1] = 0;
                row[x][2] = 255;
            } else {
                row[x][1] = cv::saturate_cast<uint8_t>(row[x][1] * params::kSaturationBoost);
                row[x][2] = cv::saturate_cast<uint8_t>(
                    255.0 * std::pow(row[x][2] / 255.0, params::kToneGamma));
            }
        }
    }
    cv::Mat bgr, rgba;
    cv::cvtColor(hsv, bgr, cv::COLOR_HSV2BGR);
    cv::cvtColor(bgr, rgba, cv::COLOR_BGR2RGBA);
    return rgba;
}

/** Straightened photo with a brightness lift and a half-strength grey-world white balance. */
cv::Mat renderOriginal(const pp_session& session) {
    cv::Scalar mean = cv::mean(session.backgroundSmall);
    const double target = (mean[0] + mean[1] + mean[2]) / 3.0;
    cv::Scalar gain;
    for (int c = 0; c < 3; ++c) {
        const double full = mean[c] > 1e-3 ? target / mean[c] : 1.0;
        gain[c] =
            (1.0 + params::kOriginalWhiteBalance * (full - 1.0)) * params::kOriginalBrightness;
    }
    cv::Mat lifted;
    cv::multiply(session.page, gain, lifted);
    cv::Mat rgba;
    cv::cvtColor(lifted, rgba, cv::COLOR_BGR2RGBA);
    return rgba;
}

} // namespace

pp_status renderPage(pp_session& session, pp_style style, pp_fit fit, int outWidth, int outHeight,
                     volatile int32_t* cancel, pp_output_image* out) {
    Timings timings;
    cv::Mat styled;
    pp_style styleUsed = style;
    pp_fallback_reason fallback = PP_FALLBACK_NONE;
    if (style == PP_STYLE_SCAN && session.inverted) {
        styleUsed = PP_STYLE_GRAYSCALE;
        fallback = PP_FALLBACK_INVERTED;
    }
    {
        StepTimer timer(timings, PP_STEP_RENDER);
        cv::Mat normalized;
        if (styleUsed != PP_STYLE_ORIGINAL) {
            normalizeIllumination(session.page, session.backgroundSmall, normalized, cancel);
        }
        switch (styleUsed) {
        case PP_STYLE_SCAN:
            styled = renderScan(normalized, cancel);
            break;
        case PP_STYLE_GRAYSCALE:
            styled = renderGrayscale(normalized);
            break;
        case PP_STYLE_COLOR_DOCUMENT:
            styled = renderColorDocument(normalized, cancel);
            break;
        case PP_STYLE_ORIGINAL:
            styled = renderOriginal(session);
            break;
        }
        checkCancel(cancel);
    }
    cv::Mat framed;
    {
        StepTimer timer(timings, PP_STEP_RESAMPLE);
        framed = frameOnWhite(styled, outWidth, outHeight, fit);
        checkCancel(cancel);
    }
    exportImage(framed, out);
    out->style_used = styleUsed;
    out->fallback_reason = fallback;
    out->steps[0] = timings.steps[0];
    out->steps[1] = timings.steps[1];
    return PP_OK;
}

} // namespace pp
