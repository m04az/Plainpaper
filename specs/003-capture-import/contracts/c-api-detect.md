# Contract: `pp_detect` (addition to `plainpaper_scan.h`, API version 1, additive)

```c
#define PP_FRAME_BGRA 0x1u   /* pixel order is B,G,R,A instead of R,G,B,A */

typedef struct {
    pp_quad corners;          /* frame pixels, TL TR BR BL; full frame when not found */
    pp_confidence confidence; /* PP_CONF_HIGH / LOW / NOT_FOUND, same classes as pp_analyze */
    float mean_luma;          /* 0..255 mean of the frame's luminance */
    float fill_fraction;      /* quad area / frame area, 0 when not found */
    int64_t micros;           /* wall time of the call */
} pp_detect_info;

/*
 * Detection only. frame long edge should be 480..800 px; larger frames are downscaled to
 * 800 px internally. No session is created, nothing is allocated beyond the working mats.
 * Returns PP_OK, PP_CANCELLED, PP_ERR_INVALID_INPUT (null/zero-size frame) or PP_ERR_INTERNAL.
 */
PP_EXPORT pp_status pp_detect(pp_engine* engine, const pp_image_rgba* frame, uint32_t flags,
                              volatile int32_t* cancel_flag, pp_detect_info* out);
```

Rules:
- Reuses `detectPage` (detect.cpp) on a BGR working image built from the frame; the
  `PP_FRAME_BGRA` flag selects `COLOR_BGRA2BGR` instead of `COLOR_RGBA2BGR`.
- Budget: < 40 ms Full / < 120 ms Simplified on a 640 × 480 frame; measured by the
  `scansuite` `DetectTimingTest` on the host (informational) and the Android benchmark.
- Thread-safe for concurrent calls with `pp_analyze` on another thread (no shared mutable
  state; `pp_last_error` stays thread-local).
- JNI: `nativeDetect(engineHandle, pixelsBuffer/Bitmap, width, height, stride, flags,
  cancelHandle, outDoubles[11])`; cinterop: direct call with a pinned byte array.
