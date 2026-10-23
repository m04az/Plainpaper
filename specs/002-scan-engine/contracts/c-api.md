# Contract: C API `plainpaper_scan.h`

The single façade both JNI (`native/jni/plainpaper_jni.cpp`) and Kotlin/Native cinterop
consume. Plain C, `extern "C"`, no OpenCV types cross the boundary. All functions are
thread-safe for distinct handles; one session must not be used concurrently.

```c
#define PP_API_VERSION 1

typedef struct pp_engine  pp_engine;    /* immutable configuration */
typedef struct pp_session pp_session;   /* analysis of one photo; owns the normalised page */

typedef enum { PP_CAP_FULL = 0, PP_CAP_SIMPLIFIED = 1 } pp_capability;
typedef enum { PP_STYLE_SCAN = 0, PP_STYLE_GRAYSCALE = 1, PP_STYLE_COLOR_DOCUMENT = 2, PP_STYLE_ORIGINAL = 3 } pp_style;
typedef enum { PP_FIT_MARGINS = 0, PP_FIT_NATURAL = 1 } pp_fit;
typedef enum { PP_OK = 0, PP_CANCELLED = 1, PP_ERR_INVALID_INPUT = 2, PP_ERR_TOO_DARK = 3,
               PP_ERR_OUT_OF_MEMORY = 4, PP_ERR_INTERNAL = 5 } pp_status;
typedef enum { PP_CONF_HIGH = 0, PP_CONF_LOW = 1, PP_CONF_NOT_FOUND = 2 } pp_confidence;
typedef enum { PP_SRC_DETECTED = 0, PP_SRC_USER = 1, PP_SRC_FULL_FRAME = 2 } pp_corner_source;
typedef enum { PP_STEP_DECODE, PP_STEP_DETECT, PP_STEP_PERSPECTIVE, PP_STEP_DESKEW, PP_STEP_ORIENT,
               PP_STEP_FLATTEN, PP_STEP_ILLUMINATION, PP_STEP_RENDER, PP_STEP_RESAMPLE, PP_STEP_COUNT } pp_step;
typedef enum { PP_STEP_APPLIED = 0, PP_STEP_SKIPPED_CAPABILITY, PP_STEP_SKIPPED_INSUFFICIENT_LINES,
               PP_STEP_SKIPPED_NO_IMPROVEMENT, PP_STEP_SKIPPED_USER_CORNERS, PP_STEP_SKIPPED_NOT_NEEDED,
               PP_STEP_FAILED } pp_step_status;
enum { PP_FLAG_INVERTED_DOCUMENT = 1, PP_FLAG_SPECULAR_HIGHLIGHT = 2 };

typedef struct { float x, y; } pp_point;
typedef struct { pp_point c[4]; } pp_quad;                 /* TL, TR, BR, BL */
typedef struct { const uint8_t* pixels; int32_t width, height, stride_bytes; } pp_image_rgba; /* RGBA8888, premultiplied not required */
typedef struct { pp_step step; pp_step_status status; int64_t micros; } pp_step_timing;

typedef struct {
    pp_capability capability;
    int32_t working_long_edge;        /* 800..3000 */
    int32_t allow_flatten;            /* 0/1 */
    const pp_quad* user_corners;      /* nullable */
} pp_analyze_options;

typedef struct {
    pp_quad corners; pp_corner_source corner_source; pp_confidence confidence;
    int32_t rotation_applied;         /* 0, 90, 180, 270 */
    int32_t orientation_confidence;   /* 0 high, 1 low, 2 none */
    double detected_aspect;           /* width / height of the corrected page */
    uint32_t flags;                   /* PP_FLAG_* */
    pp_step_timing steps[PP_STEP_COUNT]; int32_t step_count;
    int64_t peak_bytes;               /* allocator high-water mark during analyze */
} pp_analysis_info;

typedef struct { uint8_t* pixels; int32_t width, height, channels, stride_bytes;
                 pp_style style_used; int32_t fallback_reason; /* 0 none, 1 inverted */
                 pp_step_timing steps[2]; } pp_output_image;

int          pp_api_version(void);
pp_engine*   pp_engine_create(void);
void         pp_engine_destroy(pp_engine*);

/* Analyse one photo. On PP_OK or PP_ERR_TOO_DARK *out_session is set (TOO_DARK still yields
   a usable session with the cleaned frame). On any other status *out_session is NULL. */
pp_status    pp_analyze(pp_engine*, const pp_image_rgba* photo, const pp_analyze_options*,
                        volatile int32_t* cancel_flag, pp_session** out_session);
void         pp_session_info(const pp_session*, pp_analysis_info* out);

/* Render the analysed page. out_width/out_height are computed by the caller (PageSize);
   with PP_FIT_NATURAL they are derived from detected_aspect by the caller too. */
pp_status    pp_render(pp_session*, pp_style, pp_fit, int32_t out_width, int32_t out_height,
                       volatile int32_t* cancel_flag, pp_output_image* out);
void         pp_output_free(pp_output_image*);
void         pp_session_destroy(pp_session*);

/* Last error detail for PP_ERR_INTERNAL / INVALID_INPUT (thread-local, ASCII, may be empty). */
const char*  pp_last_error(void);
```

Rules:
- `cancel_flag`: the caller sets `*cancel_flag = 1`; native checks it before every stage
  and between warp/render bands and returns `PP_CANCELLED` within 200 ms of the set.
- Memory: `pp_output_image.pixels` is allocated with `malloc` and freed by
  `pp_output_free`; the JNI bridge copies it into a `Bitmap` via `AndroidBitmap_lockPixels`
  / into a `BufferedImage` raster, the iOS bridge wraps it into a `CGImage` with a
  release callback.
- Determinism: given the same input and options the output is byte-identical on the same
  platform (no randomised algorithms; `cv::setRNGSeed(0)` if any seeded op is ever used).
- Versioning: `pp_api_version()` must equal `PP_API_VERSION` compiled into the Kotlin
  bridge; mismatch → `Outcome.Internal("api-version")` at engine creation.
- Threading: OpenCV's internal parallelism is capped (`cv::setNumThreads`) at 2 on
  Simplified and at min(4, cores) on Full to limit memory and thermal spikes.

JNI mapping (`app.plainpaper.core.scanengine.bridge.JniScanBridge`, class name fixed for
`RegisterNatives`): `nativeApiVersion()`, `nativeEngineCreate()`, `nativeEngineDestroy(h)`,
`nativeAnalyze(engine, bitmapOrRaster, options..., cancelFlagHandle)` →
`long session | negative status`, `nativeSessionInfo(session, FloatArray/LongArray out)`,
`nativeRender(session, style, fit, w, h, cancel, targetBitmapOrRaster)`,
`nativeSessionDestroy`, `nativeCancelFlagCreate/Set/Destroy`, `nativeLastError()`.
