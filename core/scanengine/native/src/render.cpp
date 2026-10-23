#include "cancel.hpp"
#include "errors.hpp"
#include "stages.hpp"

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <opencv2/imgproc.hpp>

// Style rendering and page framing (research R8, R9). Phase 2 skeleton: every style returns the
// illumination-normalised page fitted into the output frame (grey for 1-channel styles).
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

} // namespace

pp_status renderPage(pp_session& session, pp_style style, pp_fit fit, int outWidth, int outHeight,
                     volatile int32_t* cancel, pp_output_image* out) {
    Timings timings;
    cv::Mat styled;
    {
        StepTimer timer(timings, PP_STEP_RENDER);
        cv::Mat normalized;
        normalizeIllumination(session.page, session.backgroundSmall, normalized, cancel);
        switch (style) {
        case PP_STYLE_SCAN:
        case PP_STYLE_GRAYSCALE:
            cv::cvtColor(normalized, styled, cv::COLOR_BGR2GRAY);
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
    out->style_used = style;
    out->fallback_reason = PP_FALLBACK_NONE;
    out->steps[0] = timings.steps[0];
    out->steps[1] = timings.steps[1];
    return PP_OK;
}

} // namespace pp
