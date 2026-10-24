/*
 * Plainpaper scan engine: C façade over the OpenCV pipeline
 * (specs/002-scan-engine/contracts/c-api.md). Consumed by the JNI bridge (Android, host JVM) and by
 * Kotlin/Native cinterop (iOS).
 */
#ifndef PLAINPAPER_SCAN_H
#define PLAINPAPER_SCAN_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32)
#define PP_EXPORT __declspec(dllexport)
#else
#define PP_EXPORT __attribute__((visibility("default")))
#endif

#define PP_API_VERSION 1

typedef struct pp_engine pp_engine;   /* immutable configuration */
typedef struct pp_session pp_session; /* analysis of one photo; owns the normalised page */

typedef enum { PP_CAP_FULL = 0, PP_CAP_SIMPLIFIED = 1 } pp_capability;

typedef enum {
    PP_STYLE_SCAN = 0,
    PP_STYLE_GRAYSCALE = 1,
    PP_STYLE_COLOR_DOCUMENT = 2,
    PP_STYLE_ORIGINAL = 3
} pp_style;

typedef enum { PP_FIT_MARGINS = 0, PP_FIT_NATURAL = 1 } pp_fit;

typedef enum {
    PP_OK = 0,
    PP_CANCELLED = 1,
    PP_ERR_INVALID_INPUT = 2,
    PP_ERR_TOO_DARK = 3,
    PP_ERR_OUT_OF_MEMORY = 4,
    PP_ERR_INTERNAL = 5
} pp_status;

typedef enum { PP_CONF_HIGH = 0, PP_CONF_LOW = 1, PP_CONF_NOT_FOUND = 2 } pp_confidence;

typedef enum { PP_SRC_DETECTED = 0, PP_SRC_USER = 1, PP_SRC_FULL_FRAME = 2 } pp_corner_source;

typedef enum {
    PP_STEP_DECODE = 0,
    PP_STEP_DETECT = 1,
    PP_STEP_PERSPECTIVE = 2,
    PP_STEP_DESKEW = 3,
    PP_STEP_ORIENT = 4,
    PP_STEP_FLATTEN = 5,
    PP_STEP_ILLUMINATION = 6,
    PP_STEP_RENDER = 7,
    PP_STEP_RESAMPLE = 8,
    PP_STEP_COUNT = 9
} pp_step;

typedef enum {
    PP_STEP_APPLIED = 0,
    PP_STEP_SKIPPED_CAPABILITY = 1,
    PP_STEP_SKIPPED_INSUFFICIENT_LINES = 2,
    PP_STEP_SKIPPED_NO_IMPROVEMENT = 3,
    PP_STEP_SKIPPED_USER_CORNERS = 4,
    PP_STEP_SKIPPED_NOT_NEEDED = 5,
    PP_STEP_FAILED = 6
} pp_step_status;

enum { PP_FLAG_INVERTED_DOCUMENT = 1, PP_FLAG_SPECULAR_HIGHLIGHT = 2 };

typedef enum {
    PP_ORIENT_HIGH = 0,
    PP_ORIENT_LOW = 1,
    PP_ORIENT_NONE = 2
} pp_orientation_confidence;

typedef enum { PP_FALLBACK_NONE = 0, PP_FALLBACK_INVERTED = 1 } pp_fallback_reason;

/* Which detector produced the corners (specs/004). */
typedef enum { PP_DETECTOR_CLASSIC = 0, PP_DETECTOR_MODEL = 1 } pp_detector;

typedef struct {
    float x, y;
} pp_point;

typedef struct {
    pp_point c[4]; /* TL, TR, BR, BL in input-photo pixels */
} pp_quad;

/* RGBA8888, row-major, stride in bytes (>= width * 4). Not modified by the engine. */
typedef struct {
    const uint8_t* pixels;
    int32_t width, height, stride_bytes;
} pp_image_rgba;

typedef struct {
    pp_step step;
    pp_step_status status;
    int64_t micros;
} pp_step_timing;

typedef struct {
    pp_capability capability;
    int32_t working_long_edge;   /* 800..3000 */
    int32_t allow_flatten;       /* 0/1 */
    const pp_quad* user_corners; /* nullable */
    int32_t keep_orientation;    /* 1: user_corners used in the given TL,TR,BR,BL order; no
                                    rotation, deskew, up/down vote or flattening (specs/004) */
} pp_analyze_options;

typedef struct {
    pp_quad corners;
    pp_corner_source corner_source;
    pp_confidence confidence;
    int32_t rotation_applied;                         /* 0, 90, 180, 270 */
    pp_orientation_confidence orientation_confidence; /* high, low, none */
    double detected_aspect;                           /* width / height of the corrected page */
    uint32_t flags;                                   /* PP_FLAG_* */
    pp_step_timing steps[PP_STEP_COUNT];
    int32_t step_count;
    int64_t peak_bytes;   /* allocator high-water mark during analyze */
    pp_detector detector; /* model or classic */
    float presence;       /* model's document-present probability; 0 for classic */
} pp_analysis_info;

typedef struct {
    uint8_t* pixels; /* malloc'ed; free with pp_output_free */
    int32_t width, height, channels, stride_bytes;
    pp_style style_used;
    pp_fallback_reason fallback_reason;
    pp_step_timing steps[2]; /* render, resample */
} pp_output_image;

/* pp_detect flags */
#define PP_FRAME_BGRA 0x1u /* pixel order is B,G,R,A instead of R,G,B,A */

/* Detection-only result for live preview frames (specs/003 contracts/c-api-detect.md). */
typedef struct {
    pp_quad corners;          /* frame pixels, TL TR BR BL; full frame when not found */
    pp_confidence confidence; /* same classes as pp_analyze */
    float mean_luma;          /* 0..255 mean luminance of the frame */
    float fill_fraction;      /* quad area / frame area, 0 when not found */
    int64_t micros;           /* wall time of the call */
    pp_detector detector;     /* model or classic */
    float presence;           /* model's document-present probability; 0 for classic */
} pp_detect_info;

PP_EXPORT int pp_api_version(void);
PP_EXPORT pp_engine* pp_engine_create(void);
PP_EXPORT void pp_engine_destroy(pp_engine* engine);

/*
 * Loads the document-detection model (.ppdm bytes, copied; specs/004 contracts/c-api-detector.md).
 * Call before analysing; returns PP_OK or PP_ERR_INVALID_INPUT (unusable model, see
 * pp_last_error). Without a model the classic detector is used.
 */
PP_EXPORT pp_status pp_engine_load_detector(pp_engine* engine, const uint8_t* bytes, size_t length);

/*
 * Enables (1, default) or disables (0) use of the loaded model by pp_detect / pp_analyze; the
 * classic detector is used while disabled. Used by the engine's performance policy on slow
 * devices (specs/004 data-model.md "Detector policy"). Safe between calls.
 */
PP_EXPORT void pp_engine_set_detector_enabled(pp_engine* engine, int32_t enabled);

/*
 * Diagnostics for the parity test: runs the loaded model on a planar RGB input (3 x 256 x 256
 * floats in 0..1) and writes the mask probabilities (128 x 128) and the presence probability.
 */
PP_EXPORT pp_status pp_detector_run(pp_engine* engine, const float* rgb_planar, float* mask_out,
                                    float* presence_out);

/*
 * Analyse one photo. On PP_OK or PP_ERR_TOO_DARK *out_session is set (TOO_DARK still yields
 * a usable session with the cleaned frame). On any other status *out_session is NULL.
 * cancel_flag may be NULL; when non-NULL the engine returns PP_CANCELLED soon after it is set.
 */
PP_EXPORT pp_status pp_analyze(pp_engine* engine, const pp_image_rgba* photo,
                               const pp_analyze_options* options, volatile int32_t* cancel_flag,
                               pp_session** out_session);
PP_EXPORT void pp_session_info(const pp_session* session, pp_analysis_info* out);

/*
 * Detection only, for live preview frames (long edge 240..6000 px; frames above 800 px are
 * downscaled). Creates no session. Returns PP_OK, PP_CANCELLED, PP_ERR_INVALID_INPUT or
 * PP_ERR_INTERNAL. Safe to call while pp_analyze runs on another thread.
 */
PP_EXPORT pp_status pp_detect(pp_engine* engine, const pp_image_rgba* frame, uint32_t flags,
                              volatile int32_t* cancel_flag, pp_detect_info* out);

/*
 * Render the analysed page. out_width/out_height are computed by the caller (PageSize);
 * with PP_FIT_NATURAL the caller derives them from detected_aspect.
 */
PP_EXPORT pp_status pp_render(pp_session* session, pp_style style, pp_fit fit, int32_t out_width,
                              int32_t out_height, volatile int32_t* cancel_flag,
                              pp_output_image* out);
PP_EXPORT void pp_output_free(pp_output_image* image);
PP_EXPORT void pp_session_destroy(pp_session* session);

/* Last error detail for PP_ERR_INTERNAL / PP_ERR_INVALID_INPUT (thread-local, ASCII, may be empty).
 */
PP_EXPORT const char* pp_last_error(void);

#ifdef __cplusplus
}
#endif

#endif /* PLAINPAPER_SCAN_H */
