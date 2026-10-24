#include "cancel.hpp"
#include "debug.hpp"
#include "errors.hpp"
#include "memory.hpp"
#include "model.hpp"
#include "params.hpp"
#include "session.hpp"
#include "stages.hpp"

#include <algorithm>
#include <chrono>
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
    if (options->keep_orientation != 0 && options->user_corners == nullptr) {
        throw Error{PP_ERR_INVALID_INPUT, "keep_orientation needs user_corners"};
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
void prepareWorking(AnalyzeContext& ctx, bool bgra = false) {
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
    cv::cvtColor(workingRgba, ctx.working, bgra ? cv::COLOR_BGRA2BGR : cv::COLOR_RGBA2BGR);
    checkCancel(ctx.cancel);
}

void validateFrame(const pp_image_rgba* frame, const pp_detect_info* out) {
    if (frame == nullptr || frame->pixels == nullptr || out == nullptr) {
        throw Error{PP_ERR_INVALID_INPUT, "null input"};
    }
    const int longEdge = std::max(frame->width, frame->height);
    if (frame->width <= 0 || frame->height <= 0 || longEdge < params::kMinDetectLongEdge) {
        throw Error{PP_ERR_INVALID_INPUT, "frame too small"};
    }
    if (longEdge > params::kMaxInputLongEdge) {
        throw Error{PP_ERR_INVALID_INPUT, "frame too large"};
    }
    if (frame->stride_bytes < frame->width * 4) {
        throw Error{PP_ERR_INVALID_INPUT, "stride smaller than width"};
    }
}

double quadArea(const Quad& quad) {
    double sum = 0;
    for (int i = 0; i < 4; ++i) {
        const auto& a = quad[i];
        const auto& b = quad[(i + 1) % 4];
        sum += static_cast<double>(a.x) * b.y - static_cast<double>(b.x) * a.y;
    }
    return std::abs(sum) / 2.0;
}

/** Detection only (specs/003 contracts/c-api-detect.md): reuses detectPage on a small frame. */
pp_status runDetect(pp_engine* engine, const pp_image_rgba* frame, uint32_t flags,
                    volatile int32_t* cancel, pp_detect_info* out) {
    const auto start = std::chrono::steady_clock::now();
    validateFrame(frame, out);
    pp_analyze_options options{};
    options.capability = PP_CAP_FULL;
    options.working_long_edge =
        std::min(params::kDetectWorkingLongEdge, std::max(frame->width, frame->height));
    options.allow_flatten = 0;
    options.user_corners = nullptr;

    AnalyzeContext ctx;
    ctx.photo = frame;
    ctx.options = &options;
    ctx.cancel = cancel;
    ctx.fullCapability = true;
    ctx.detector = engine->detectorEnabled.load() ? engine->detector.get() : nullptr;
    prepareWorking(ctx, (flags & PP_FRAME_BGRA) != 0);

    cv::Mat gray;
    cv::cvtColor(ctx.working, gray, cv::COLOR_BGR2GRAY);
    const double meanLuma = cv::mean(gray)[0];
    checkCancel(cancel);

    const DetectResult detected = detectPage(ctx);
    *out = pp_detect_info{};
    for (int i = 0; i < 4; ++i) {
        out->corners.c[i] = pp_point{detected.quad[i].x, detected.quad[i].y};
    }
    out->confidence = detected.confidence;
    out->detector = detected.detector;
    out->presence = detected.presence;
    out->mean_luma = static_cast<float>(meanLuma);
    const double frameArea = static_cast<double>(frame->width) * frame->height;
    out->fill_fraction = detected.confidence == PP_CONF_NOT_FOUND
                             ? 0.f
                             : static_cast<float>(quadArea(detected.quad) / frameArea);
    out->micros = std::chrono::duration_cast<std::chrono::microseconds>(
                      std::chrono::steady_clock::now() - start)
                      .count();
    return PP_OK;
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

pp_status runAnalysis(pp_engine* engine, const pp_image_rgba* photo,
                      const pp_analyze_options* options, volatile int32_t* cancel,
                      pp_session** outSession) {
    validateInput(photo, options);
    configureThreads(options->capability);
    CountingAllocator::install();
    CountingAllocator::instance().resetPeak();

    AnalyzeContext ctx;
    ctx.photo = photo;
    ctx.options = options;
    ctx.cancel = cancel;
    ctx.fullCapability = options->capability == PP_CAP_FULL;
    ctx.detector = engine->detectorEnabled.load() ? engine->detector.get() : nullptr;
    prepareWorking(ctx);

    const bool tooDark = isTooDark(ctx.working);
    if (hasSpecularHighlight(ctx.working)) {
        ctx.flags |= PP_FLAG_SPECULAR_HIGHLIGHT;
    }

    // 1. Corners.
    DetectResult detected;
    {
        StepTimer timer(ctx.timings, PP_STEP_DETECT);
        maybeFailAllocation(PP_STEP_DETECT);
        if (options->user_corners != nullptr) {
            for (int i = 0; i < 4; ++i) {
                detected.quad[i] =
                    cv::Point2f(options->user_corners->c[i].x, options->user_corners->c[i].y);
            }
            if (options->keep_orientation == 0) {
                detected.quad = orderCorners(detected.quad);
            }
            detected.confidence = PP_CONF_HIGH;
            detected.source = PP_SRC_USER;
            timer.setStatus(PP_STEP_SKIPPED_USER_CORNERS);
        } else {
            detected = detectPage(ctx);
        }
        checkCancel(cancel);
    }

    debugLog("mem after decode+detect: live MB / peak MB",
             CountingAllocator::instance().liveBytes() / 1e6,
             CountingAllocator::instance().peakBytes() / 1e6, 0);
    // 2. Geometry plan (aspect, page size) + deskew/orientation decided on a preview.
    const int pageLongEdge =
        ctx.fullCapability ? params::kPageLongEdgeFull : params::kPageLongEdgeSimplified;
    Geometry geometry = planGeometry(ctx, detected.quad, pageLongEdge);
    {
        StepTimer timer(ctx.timings, PP_STEP_DESKEW);
        cv::Mat preview = warpPreview(ctx, geometry, options->working_long_edge);
        // Text analysis works on an illumination-normalised preview so shadows and casts do
        // not masquerade as ink in the projection profiles.
        cv::Mat previewBackground = estimateBackground(preview, options->working_long_edge);
        cv::Mat previewNormalized;
        normalizeIllumination(preview, previewBackground, previewNormalized, cancel);
        cv::Mat previewGray;
        cv::cvtColor(previewNormalized, previewGray, cv::COLOR_BGR2GRAY);
        if (isInvertedDocument(ctx.working, previewGray)) {
            ctx.flags |= PP_FLAG_INVERTED_DOCUMENT;
        }
        // Portrait/landscape is decided first (it only needs the line axis); the tilt is then
        // measured on the axis-corrected preview, where text lines run horizontally. Rotations
        // about the page centre commute, so the angle applies unchanged to the warp. Up/down
        // waits until the page is flattened, when the row profiles are clean.
        const bool keepOrientation = options->keep_orientation != 0;
        geometry.rotation = keepOrientation ? 0 : estimateAxisRotation(previewGray);
        cv::Mat axisGray = previewGray;
        if (geometry.rotation == 90) {
            cv::rotate(previewGray, axisGray, cv::ROTATE_90_CLOCKWISE);
        }
        geometry.deskewDeg = keepOrientation ? 0.0 : estimateDeskewDegrees(axisGray, cancel);
        debugLog("deskew: preview degrees", geometry.deskewDeg, geometry.rotation, 0);
        if (std::abs(geometry.deskewDeg) < 0.3) {
            geometry.deskewDeg = 0.0;
            timer.setStatus(PP_STEP_SKIPPED_NOT_NEEDED);
        }
        checkCancel(cancel);
    }

    debugLog("mem after deskew/axis: live MB / peak MB",
             CountingAllocator::instance().liveBytes() / 1e6,
             CountingAllocator::instance().peakBytes() / 1e6, 0);
    // 3. Full-resolution warp (banded, cancellable), then rotation to upright.
    auto session = std::make_unique<pp_session>();
    {
        StepTimer timer(ctx.timings, PP_STEP_PERSPECTIVE);
        maybeFailAllocation(PP_STEP_PERSPECTIVE);
        session->page = warpPage(ctx, geometry);
        applyRotation(session->page, geometry.rotation);
        checkCancel(cancel);
    }

    debugLog("mem after warp: live MB / peak MB", CountingAllocator::instance().liveBytes() / 1e6,
             CountingAllocator::instance().peakBytes() / 1e6, 0);
    // 4. Up/down vote on the crisp page (working resolution, illumination-normalised, strips
    //    tolerate curl); a flip rotates the page before flattening.
    {
        StepTimer timer(ctx.timings, PP_STEP_ORIENT);
        cv::Mat small;
        const double scale = std::min(1.0, static_cast<double>(options->working_long_edge) /
                                               std::max(session->page.cols, session->page.rows));
        cv::resize(session->page, small, cv::Size(), scale, scale, cv::INTER_AREA);
        cv::Mat normalized;
        normalizeIllumination(small, estimateBackground(small, options->working_long_edge),
                              normalized, cancel);
        cv::Mat gray;
        cv::cvtColor(normalized, gray, cv::COLOR_BGR2GRAY);
        pp_orientation_confidence orientationConfidence = PP_ORIENT_NONE;
        // Without a page outline the frame is mostly desk and clutter, whose "lines" make the
        // up/down vote unreliable (an upright card was turned over on a phone photo).
        const bool upsideDown =
            detected.source != PP_SRC_FULL_FRAME && options->keep_orientation == 0 &&
            estimateUpsideDown(gray, orientationConfidence, params::kOrientStrips, cancel);
        debugLog("orient: upside-down / confidence", upsideDown ? 1 : 0, orientationConfidence, 0);
        geometry.orientationConfidence = orientationConfidence;
        if (upsideDown) {
            geometry.rotation = (geometry.rotation + 180) % 360;
            cv::rotate(session->page, session->page, cv::ROTATE_180);
        }
        if (geometry.rotation == 0) {
            timer.setStatus(PP_STEP_SKIPPED_NOT_NEEDED);
        }
        checkCancel(cancel);
    }

    debugLog("mem after orient: live MB / peak MB", CountingAllocator::instance().liveBytes() / 1e6,
             CountingAllocator::instance().peakBytes() / 1e6, 0);
    // 5. Flattening (Full capability only). Bowed lines fool the pre-warp tilt estimate, so a
    //    flattened page gets a second, residual deskew.
    {
        StepTimer timer(ctx.timings, PP_STEP_FLATTEN);
        if (!ctx.fullCapability || options->allow_flatten == 0 || options->keep_orientation != 0) {
            timer.setStatus(PP_STEP_SKIPPED_CAPABILITY);
        } else {
            const pp_step_status status = flattenPage(session->page, cancel);
            timer.setStatus(status);
            if (status == PP_STEP_APPLIED) {
                checkCancel(cancel);
                cv::Mat small;
                const double scale =
                    std::min(1.0, static_cast<double>(options->working_long_edge) /
                                      std::max(session->page.cols, session->page.rows));
                cv::resize(session->page, small, cv::Size(), scale, scale, cv::INTER_AREA);
                cv::Mat smallGray;
                cv::cvtColor(small, smallGray, cv::COLOR_BGR2GRAY);
                const double residual = estimateDeskewDegrees(smallGray, cancel);
                debugLog("flatten: residual deskew degrees", residual, 0, 0);
                if (std::abs(residual) >= 0.3) {
                    cv::Mat rotation = cv::getRotationMatrix2D(
                        cv::Point2f(session->page.cols / 2.f, session->page.rows / 2.f), residual,
                        1.0);
                    cv::warpAffine(session->page, session->page, rotation, session->page.size(),
                                   cv::INTER_CUBIC, cv::BORDER_REPLICATE);
                }
                // Straight lines now: a confident whole-width vote may overturn the strip vote.
                checkCancel(cancel);
                cv::resize(session->page, small, cv::Size(), scale, scale, cv::INTER_AREA);
                cv::Mat normalizedSmall;
                normalizeIllumination(small, estimateBackground(small, options->working_long_edge),
                                      normalizedSmall, cancel);
                cv::cvtColor(normalizedSmall, smallGray, cv::COLOR_BGR2GRAY);
                pp_orientation_confidence revoteConfidence = PP_ORIENT_NONE;
                const bool stillUpsideDown =
                    estimateUpsideDown(smallGray, revoteConfidence, 1, cancel);
                debugLog("flatten: re-vote upside-down / confidence", stillUpsideDown ? 1 : 0,
                         revoteConfidence, 0);
                if (stillUpsideDown && revoteConfidence == PP_ORIENT_HIGH) {
                    geometry.rotation = (geometry.rotation + 180) % 360;
                    geometry.orientationConfidence = revoteConfidence;
                    cv::rotate(session->page, session->page, cv::ROTATE_180);
                }
            }
        }
        checkCancel(cancel);
    }

    debugLog("mem after flatten: live MB / peak MB",
             CountingAllocator::instance().liveBytes() / 1e6,
             CountingAllocator::instance().peakBytes() / 1e6, 0);
    // 6. Illumination estimate (applied at render time so Original keeps the raw page).
    {
        StepTimer timer(ctx.timings, PP_STEP_ILLUMINATION);
        session->backgroundSmall = estimateBackground(session->page, options->working_long_edge);
        checkCancel(cancel);
    }

    debugLog("mem after background: live MB / peak MB",
             CountingAllocator::instance().liveBytes() / 1e6,
             CountingAllocator::instance().peakBytes() / 1e6, 0);
    session->inverted = (ctx.flags & PP_FLAG_INVERTED_DOCUMENT) != 0;

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
    info.detector = detected.detector;
    info.presence = detected.presence;
    info.orientation_confidence = geometry.orientationConfidence;
    info.detected_aspect =
        static_cast<double>(session->page.cols) / static_cast<double>(session->page.rows);
    info.flags = ctx.flags;
    info.step_count = ctx.timings.count;
    std::memcpy(info.steps, ctx.timings.steps, sizeof(info.steps));
    info.peak_bytes = CountingAllocator::instance().peakBytes();
    for (int i = 0; i < info.step_count; ++i) {
        debugLog("step / status / ms", info.steps[i].step, info.steps[i].status,
                 info.steps[i].micros / 1000.0);
    }
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

PP_EXPORT pp_status pp_engine_load_detector(pp_engine* engine, const uint8_t* bytes,
                                            size_t length) {
    if (engine == nullptr) {
        pp::setLastError("null engine");
        return PP_ERR_INVALID_INPUT;
    }
    auto model = std::make_shared<pp::DetectorModel>();
    std::string error;
    if (!model->load(bytes, length, error)) {
        pp::setLastError(error.c_str());
        return PP_ERR_INVALID_INPUT;
    }
    engine->detector = model;
    return PP_OK;
}

PP_EXPORT void pp_engine_set_detector_enabled(pp_engine* engine, int32_t enabled) {
    if (engine != nullptr) {
        engine->detectorEnabled.store(enabled != 0);
    }
}

PP_EXPORT pp_status pp_detector_run(pp_engine* engine, const float* rgb_planar, float* mask_out,
                                    float* presence_out) {
    if (engine == nullptr || engine->detector == nullptr || rgb_planar == nullptr ||
        mask_out == nullptr || presence_out == nullptr) {
        pp::setLastError("detector not loaded");
        return PP_ERR_INVALID_INPUT;
    }
    cv::Mat mask;
    float presence = 0;
    if (!engine->detector->inferPlanar(rgb_planar, mask, presence)) {
        return PP_ERR_INVALID_INPUT;
    }
    std::memcpy(mask_out, mask.ptr<float>(), mask.total() * sizeof(float));
    *presence_out = presence;
    return PP_OK;
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

PP_EXPORT pp_status pp_detect(pp_engine* engine, const pp_image_rgba* frame, uint32_t flags,
                              volatile int32_t* cancel_flag, pp_detect_info* out) {
    if (engine == nullptr) {
        pp::setLastError("null engine");
        return PP_ERR_INVALID_INPUT;
    }
    return pp::guarded([&] { return pp::runDetect(engine, frame, flags, cancel_flag, out); });
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
