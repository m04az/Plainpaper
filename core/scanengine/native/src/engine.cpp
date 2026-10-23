#include "cancel.hpp"
#include "errors.hpp"
#include "params.hpp"
#include "session.hpp"
#include "stages.hpp"

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <new>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <string>
#include <thread>

namespace pp {

namespace {

thread_local std::string gLastError;

bool validQuad(const pp_quad& q, int width, int height) {
    const float tolX = width * 0.02f;
    const float tolY = height * 0.02f;
    for (const auto& p : q.c) {
        if (p.x < -tolX || p.y < -tolY || p.x > width + tolX || p.y > height + tolY) {
            return false;
        }
    }
    // Convexity (consistent cross-product sign) and a minimum area of 5% of the photo.
    double area = 0;
    int sign = 0;
    for (int i = 0; i < 4; ++i) {
        const auto& a = q.c[i];
        const auto& b = q.c[(i + 1) % 4];
        const auto& c = q.c[(i + 2) % 4];
        double cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x);
        int s = cross > 0 ? 1 : (cross < 0 ? -1 : 0);
        if (s == 0) {
            return false;
        }
        if (sign == 0) {
            sign = s;
        } else if (s != sign) {
            return false;
        }
        area += a.x * b.y - b.x * a.y;
    }
    area = std::abs(area) / 2.0;
    return area >= 0.05 * width * height;
}

void validateInput(const pp_image_rgba* photo, const pp_analyze_options* options) {
    if (photo == nullptr || photo->pixels == nullptr || options == nullptr) {
        throw Error{PP_ERR_INVALID_INPUT, "null input"};
    }
    const int longEdge = std::max(photo->width, photo->height);
    if (photo->width <= 0 || photo->height <= 0 || longEdge < params::kMinInputLongEdge) {
        throw Error{PP_ERR_INVALID_INPUT, "image too small"};
    }
    if (longEdge > params::kMaxInputLongEdge) {
        throw Error{PP_ERR_INVALID_INPUT, "image too large"};
    }
    if (photo->stride_bytes < photo->width * 4) {
        throw Error{PP_ERR_INVALID_INPUT, "stride smaller than width"};
    }
    if (options->working_long_edge < params::kMinWorkingLongEdge ||
        options->working_long_edge > params::kMaxWorkingLongEdge) {
        throw Error{PP_ERR_INVALID_INPUT, "working_long_edge out of range"};
    }
    if (options->user_corners != nullptr &&
        !validQuad(*options->user_corners, photo->width, photo->height)) {
        throw Error{PP_ERR_INVALID_INPUT, "corners"};
    }
}

void configureThreads(pp_capability capability) {
    const int cores = static_cast<int>(std::thread::hardware_concurrency());
    const int threads = capability == PP_CAP_SIMPLIFIED
                            ? params::kThreadsSimplified
                            : std::min(params::kThreadsFullMax, std::max(1, cores));
    cv::setNumThreads(threads);
}

/** Builds the downscaled BGR working image and records the decode step. */
void prepareWorking(AnalyzeContext& ctx) {
    StepTimer timer(ctx.timings, PP_STEP_DECODE);
    const pp_image_rgba& photo = *ctx.photo;
    ctx.photoView = cv::Mat(photo.height, photo.width, CV_8UC4, const_cast<uint8_t*>(photo.pixels),
                            static_cast<size_t>(photo.stride_bytes));
    const int longEdge = std::max(photo.width, photo.height);
    ctx.workingScale =
        std::min(1.0, static_cast<double>(ctx.options->working_long_edge) / longEdge);
    cv::Mat workingRgba;
    if (ctx.workingScale < 1.0) {
        cv::resize(ctx.photoView, workingRgba, cv::Size(), ctx.workingScale, ctx.workingScale,
                   cv::INTER_AREA);
    } else {
        workingRgba = ctx.photoView;
    }
    cv::cvtColor(workingRgba, ctx.working, cv::COLOR_RGBA2BGR);
    ctx.memory.hold(ctx.working);
    checkCancel(ctx.cancel);
}

bool isTooDark(const cv::Mat& workingBgr) {
    cv::Mat gray;
    cv::cvtColor(workingBgr, gray, cv::COLOR_BGR2GRAY);
    // Median via histogram.
    int hist[256] = {0};
    for (int y = 0; y < gray.rows; ++y) {
        const uint8_t* row = gray.ptr<uint8_t>(y);
        for (int x = 0; x < gray.cols; ++x) {
            ++hist[row[x]];
        }
    }
    const long total = static_cast<long>(gray.rows) * gray.cols;
    long seen = 0;
    int median = 0;
    for (int v = 0; v < 256; ++v) {
        seen += hist[v];
        if (seen * 2 >= total) {
            median = v;
            break;
        }
    }
    return median < params::kTooDarkMedianLuminance * 255.0;
}

pp_status runAnalysis(pp_engine* /*engine*/, const pp_image_rgba* photo,
                      const pp_analyze_options* options, volatile int32_t* cancel,
                      pp_session** outSession) {
    validateInput(photo, options);
    configureThreads(options->capability);

    AnalyzeContext ctx;
    ctx.photo = photo;
    ctx.options = options;
    ctx.cancel = cancel;
    ctx.fullCapability = options->capability == PP_CAP_FULL;
    prepareWorking(ctx);

    const bool tooDark = isTooDark(ctx.working);
    if (hasSpecularHighlight(ctx.working)) {
        ctx.flags |= PP_FLAG_SPECULAR_HIGHLIGHT;
    }

    // 1. Corners.
    DetectResult detected;
    {
        StepTimer timer(ctx.timings, PP_STEP_DETECT);
        if (options->user_corners != nullptr) {
            for (int i = 0; i < 4; ++i) {
                detected.quad[i] =
                    cv::Point2f(options->user_corners->c[i].x, options->user_corners->c[i].y);
            }
            detected.quad = orderCorners(detected.quad);
            detected.confidence = PP_CONF_HIGH;
            detected.source = PP_SRC_USER;
            timer.setStatus(PP_STEP_SKIPPED_USER_CORNERS);
        } else {
            detected = detectPage(ctx);
        }
        checkCancel(cancel);
    }

    // 2. Geometry plan (aspect, page size) + deskew/orientation decided on a preview.
    const int pageLongEdge =
        ctx.fullCapability ? params::kPageLongEdgeFull : params::kPageLongEdgeSimplified;
    Geometry geometry = planGeometry(ctx, detected.quad, pageLongEdge);
    {
        StepTimer timer(ctx.timings, PP_STEP_DESKEW);
        cv::Mat preview = warpPreview(ctx, geometry, options->working_long_edge);
        cv::Mat previewGray;
        cv::cvtColor(preview, previewGray, cv::COLOR_BGR2GRAY);
        geometry.deskewDeg = estimateDeskewDegrees(previewGray);
        if (std::abs(geometry.deskewDeg) < 0.3) {
            geometry.deskewDeg = 0.0;
            timer.setStatus(PP_STEP_SKIPPED_NOT_NEEDED);
        }
        checkCancel(cancel);
        StepTimer orientTimer(ctx.timings, PP_STEP_ORIENT);
        pp_orientation_confidence orientationConfidence = PP_ORIENT_NONE;
        geometry.rotation = estimateOrientation(previewGray, orientationConfidence);
        geometry.orientationConfidence = orientationConfidence;
        if (geometry.rotation == 0) {
            orientTimer.setStatus(PP_STEP_SKIPPED_NOT_NEEDED);
        }
        checkCancel(cancel);
    }

    // 3. Full-resolution warp (banded, cancellable), then rotation to upright.
    auto session = std::make_unique<pp_session>();
    {
        StepTimer timer(ctx.timings, PP_STEP_PERSPECTIVE);
        session->page = warpPage(ctx, geometry);
        ctx.memory.hold(session->page);
        applyRotation(session->page, geometry.rotation);
        checkCancel(cancel);
    }

    // 4. Flattening (Full capability only).
    {
        StepTimer timer(ctx.timings, PP_STEP_FLATTEN);
        if (!ctx.fullCapability || options->allow_flatten == 0) {
            timer.setStatus(PP_STEP_SKIPPED_CAPABILITY);
        } else {
            timer.setStatus(flattenPage(session->page, cancel));
        }
        checkCancel(cancel);
    }

    // 5. Illumination estimate (applied at render time so Original keeps the raw page).
    {
        StepTimer timer(ctx.timings, PP_STEP_ILLUMINATION);
        session->backgroundSmall = estimateBackground(session->page, options->working_long_edge);
        checkCancel(cancel);
    }

    // Report.
    pp_analysis_info& info = session->info;
    std::memset(&info, 0, sizeof(info));
    const Quad finalQuad = rotateQuadOrder(detected.quad, geometry.rotation);
    for (int i = 0; i < 4; ++i) {
        info.corners.c[i] = pp_point{finalQuad[i].x, finalQuad[i].y};
    }
    info.corner_source = detected.source;
    info.confidence = detected.confidence;
    info.rotation_applied = geometry.rotation;
    info.orientation_confidence = geometry.orientationConfidence;
    info.detected_aspect =
        static_cast<double>(session->page.cols) / static_cast<double>(session->page.rows);
    info.flags = ctx.flags;
    info.step_count = ctx.timings.count;
    std::memcpy(info.steps, ctx.timings.steps, sizeof(info.steps));
    info.peak_bytes = ctx.memory.peak();
    session->timings = ctx.timings;

    *outSession = session.release();
    return tooDark ? PP_ERR_TOO_DARK : PP_OK;
}

template <typename Fn> pp_status guarded(Fn&& fn) {
    clearLastError();
    try {
        return fn();
    } catch (const Cancelled&) {
        return PP_CANCELLED;
    } catch (const Error& error) {
        setLastError(error.detail);
        return error.status;
    } catch (const std::bad_alloc&) {
        setLastError("bad_alloc");
        return PP_ERR_OUT_OF_MEMORY;
    } catch (const cv::Exception& error) {
        // OpenCV reports allocation failures as cv::Exception too.
        if (error.code == cv::Error::StsNoMem) {
            setLastError(error.err);
            return PP_ERR_OUT_OF_MEMORY;
        }
        setLastError(error.err);
        return PP_ERR_INTERNAL;
    } catch (const std::exception& error) {
        setLastError(error.what());
        return PP_ERR_INTERNAL;
    } catch (...) {
        setLastError("unknown");
        return PP_ERR_INTERNAL;
    }
}

} // namespace

void setLastError(const std::string& detail) {
    gLastError = detail;
}
void clearLastError() {
    gLastError.clear();
}

} // namespace pp

extern "C" {

PP_EXPORT int pp_api_version(void) {
    return PP_API_VERSION;
}

PP_EXPORT pp_engine* pp_engine_create(void) {
    return new (std::nothrow) pp_engine();
}

PP_EXPORT void pp_engine_destroy(pp_engine* engine) {
    delete engine;
}

PP_EXPORT pp_status pp_analyze(pp_engine* engine, const pp_image_rgba* photo,
                               const pp_analyze_options* options, volatile int32_t* cancel_flag,
                               pp_session** out_session) {
    if (out_session == nullptr) {
        return PP_ERR_INVALID_INPUT;
    }
    *out_session = nullptr;
    if (engine == nullptr) {
        pp::setLastError("null engine");
        return PP_ERR_INVALID_INPUT;
    }
    return pp::guarded(
        [&] { return pp::runAnalysis(engine, photo, options, cancel_flag, out_session); });
}

PP_EXPORT void pp_session_info(const pp_session* session, pp_analysis_info* out) {
    if (session == nullptr || out == nullptr) {
        return;
    }
    *out = session->info;
}

PP_EXPORT pp_status pp_render(pp_session* session, pp_style style, pp_fit fit, int32_t out_width,
                              int32_t out_height, volatile int32_t* cancel_flag,
                              pp_output_image* out) {
    if (session == nullptr || out == nullptr) {
        return PP_ERR_INVALID_INPUT;
    }
    std::memset(out, 0, sizeof(*out));
    if (out_width <= 0 || out_height <= 0 || out_width > 8000 || out_height > 8000) {
        pp::setLastError("output size");
        return PP_ERR_INVALID_INPUT;
    }
    return pp::guarded([&] {
        return pp::renderPage(*session, style, fit, out_width, out_height, cancel_flag, out);
    });
}

PP_EXPORT void pp_output_free(pp_output_image* image) {
    if (image != nullptr && image->pixels != nullptr) {
        std::free(image->pixels);
        image->pixels = nullptr;
    }
}

PP_EXPORT void pp_session_destroy(pp_session* session) {
    delete session;
}

PP_EXPORT const char* pp_last_error(void) {
    return pp::gLastError.c_str();
}

} // extern "C"
