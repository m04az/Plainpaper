#include "cancel.hpp"
#include "errors.hpp"
#include "params.hpp"
#include "stages.hpp"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <opencv2/imgproc.hpp>

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

cv::Mat renderScan(const cv::Mat& normalizedBgr, volatile int32_t* cancel) {
    cv::Mat gray;
    cv::cvtColor(normalizedBgr, gray, cv::COLOR_BGR2GRAY);
    cv::Mat ink = sauvolaInk(gray, cancel);
    cv::medianBlur(ink, ink, 3);
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

cv::Mat renderGrayscale(const cv::Mat& normalizedBgr) {
    cv::Mat gray;
    cv::cvtColor(normalizedBgr, gray, cv::COLOR_BGR2GRAY);
    return gray;
}

} // namespace

pp_status renderPage(pp_session& session, pp_style style, pp_fit fit, int outWidth, int outHeight,
                     volatile int32_t* cancel, pp_output_image* out) {
    Timings timings;
    cv::Mat styled;
    pp_style styleUsed = style;
    pp_fallback_reason fallback = PP_FALLBACK_NONE;
    {
        StepTimer timer(timings, PP_STEP_RENDER);
        cv::Mat normalized;
        if (style != PP_STYLE_ORIGINAL) {
            normalizeIllumination(session.page, session.backgroundSmall, normalized, cancel);
        }
        switch (style) {
        case PP_STYLE_SCAN:
            styled = renderScan(normalized, cancel);
            break;
        case PP_STYLE_GRAYSCALE:
            styled = renderGrayscale(normalized);
            break;
        case PP_STYLE_COLOR_DOCUMENT:
            cv::cvtColor(normalized, styled, cv::COLOR_BGR2RGBA);
            break;
        case PP_STYLE_ORIGINAL:
            cv::cvtColor(session.page, styled, cv::COLOR_BGR2RGBA);
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
