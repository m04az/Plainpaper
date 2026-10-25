# C API: detector model (plainpaper_scan.h, API version + 1)

```c
/* Load the document-detection model (.ppdm bytes, copied). May be called once per engine, before
 * or after other calls; safe to call while no pp_analyze/pp_detect runs on this engine.
 * Returns PP_OK, PP_ERR_INVALID_INPUT (not a usable model: wrong input/output shapes) or
 * PP_ERR_INTERNAL. On failure the engine keeps using the classic detector. */
PP_EXPORT pp_status pp_engine_load_detector(pp_engine* engine, const uint8_t* bytes, size_t length);

/* Performance policy switch: 0 makes pp_detect/pp_analyze ignore the loaded model (classic detector),
 * 1 (default) uses it again. Safe between calls; the model stays loaded. Used by DefaultScanEngine
 * when the first 5 model passes on a Simplified device have a median above 120 ms. */
PP_EXPORT void pp_engine_set_detector_enabled(pp_engine* engine, int32_t enabled);

/* Which detector produced the last result (diagnostics). */
typedef enum { PP_DETECTOR_CLASSIC = 0, PP_DETECTOR_MODEL = 1 } pp_detector;
```

Additions to existing structs (appended at the end; callers zero-initialise):

```c
typedef struct {
    ...existing fields...
    int32_t keep_orientation; /* 1: user_corners are used in the given TL,TR,BR,BL order; no
                                 axis/up-down/deskew estimation (Quick scan pages, re-renders) */
} pp_analyze_options;

typedef struct {
    ...existing fields...
    pp_detector detector; /* model or classic */
    float presence;       /* model presence score 0..1; 0 for classic */
} pp_detect_info;

/* pp_analysis_info gains the same two fields. */
```

Behaviour:
- `pp_detect` and the detect step of `pp_analyze` run the model first when loaded and enabled by
  policy (data-model.md); on "no document" (presence < 0.2) they return NotFound without the
  classic pass; on presence ≥ 0.2 without a valid quad they fall back to the classic detector.
- `keep_orientation = 1` requires `user_corners`; otherwise `PP_ERR_INVALID_INPUT`.
- Forward passes are serialised per engine; `cancel_flag` is checked before and after the pass.
- Determinism: same bytes, same input → same corners on the host (scansuite gate).
