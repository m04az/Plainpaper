/*
 * JNI bridge for Android and the host JVM (specs/002-scan-engine/contracts/c-api.md).
 * Registered explicitly from JNI_OnLoad against
 * app.plainpaper.core.scanengine.bridge.JniScanBridge.
 */
#include "plainpaper_scan.h"

#include <atomic>
#include <cstdint>
#include <cstring>
#include <jni.h>
#include <new>
#include <vector>

#if defined(PP_ANDROID)
#include <android/bitmap.h>
#endif

namespace {

constexpr const char* kBridgeClass = "app/plainpaper/core/scanengine/bridge/JniScanBridge";

struct CancelFlag {
    std::atomic<int32_t> value{0};
};

jlong toHandle(void* pointer) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(pointer));
}

template <typename T> T* fromHandle(jlong handle) {
    return reinterpret_cast<T*>(static_cast<intptr_t>(handle));
}

volatile int32_t* cancelPointer(jlong handle) {
    auto* flag = fromHandle<CancelFlag>(handle);
    // std::atomic<int32_t> is layout-compatible with int32_t on every supported ABI.
    return flag == nullptr ? nullptr : reinterpret_cast<volatile int32_t*>(&flag->value);
}

void setStatus(JNIEnv* env, jintArray outStatus, pp_status status) {
    if (outStatus != nullptr && env->GetArrayLength(outStatus) > 0) {
        jint value = static_cast<jint>(status);
        env->SetIntArrayRegion(outStatus, 0, 1, &value);
    }
}

pp_status analyzePixels(JNIEnv* env, jlong engine, const uint8_t* pixels, jint width, jint height,
                        jint strideBytes, jint capability, jint workingLongEdge,
                        jboolean allowFlatten, jfloatArray userCorners, jlong cancel,
                        pp_session** outSession) {
    pp_image_rgba photo{pixels, width, height, strideBytes};
    pp_quad quad{};
    pp_analyze_options options{};
    options.capability = capability == 0 ? PP_CAP_FULL : PP_CAP_SIMPLIFIED;
    options.working_long_edge = workingLongEdge;
    options.allow_flatten = allowFlatten == JNI_TRUE ? 1 : 0;
    options.user_corners = nullptr;
    if (userCorners != nullptr && env->GetArrayLength(userCorners) >= 8) {
        jfloat values[8];
        env->GetFloatArrayRegion(userCorners, 0, 8, values);
        for (int i = 0; i < 4; ++i) {
            quad.c[i] = pp_point{values[i * 2], values[i * 2 + 1]};
        }
        options.user_corners = &quad;
    }
    return pp_analyze(fromHandle<pp_engine>(engine), &photo, &options, cancelPointer(cancel),
                      outSession);
}

// ---- natives -------------------------------------------------------------------------------

jint nativeApiVersion(JNIEnv*, jclass) {
    return pp_api_version();
}

jlong nativeEngineCreate(JNIEnv*, jclass) {
    return toHandle(pp_engine_create());
}

void nativeEngineDestroy(JNIEnv*, jclass, jlong engine) {
    pp_engine_destroy(fromHandle<pp_engine>(engine));
}

jlong nativeCancelFlagCreate(JNIEnv*, jclass) {
    return toHandle(new (std::nothrow) CancelFlag());
}

void nativeCancelFlagSet(JNIEnv*, jclass, jlong handle) {
    auto* flag = fromHandle<CancelFlag>(handle);
    if (flag != nullptr) {
        flag->value.store(1);
    }
}

void nativeCancelFlagDestroy(JNIEnv*, jclass, jlong handle) {
    delete fromHandle<CancelFlag>(handle);
}

jlong nativeAnalyzeBytes(JNIEnv* env, jclass, jlong engine, jbyteArray pixels, jint width,
                         jint height, jint strideBytes, jint capability, jint workingLongEdge,
                         jboolean allowFlatten, jfloatArray userCorners, jlong cancel,
                         jintArray outStatus) {
    if (pixels == nullptr) {
        setStatus(env, outStatus, PP_ERR_INVALID_INPUT);
        return 0;
    }
    const jsize length = env->GetArrayLength(pixels);
    if (static_cast<int64_t>(length) < static_cast<int64_t>(strideBytes) * height) {
        setStatus(env, outStatus, PP_ERR_INVALID_INPUT);
        return 0;
    }
    jbyte* data = env->GetByteArrayElements(pixels, nullptr);
    if (data == nullptr) {
        setStatus(env, outStatus, PP_ERR_OUT_OF_MEMORY);
        return 0;
    }
    pp_session* session = nullptr;
    pp_status status = analyzePixels(env, engine, reinterpret_cast<const uint8_t*>(data), width,
                                     height, strideBytes, capability, workingLongEdge, allowFlatten,
                                     userCorners, cancel, &session);
    env->ReleaseByteArrayElements(pixels, data, JNI_ABORT);
    setStatus(env, outStatus, status);
    return toHandle(session);
}

#if defined(PP_ANDROID)
jlong nativeAnalyzeBitmap(JNIEnv* env, jclass, jlong engine, jobject bitmap, jint capability,
                          jint workingLongEdge, jboolean allowFlatten, jfloatArray userCorners,
                          jlong cancel, jintArray outStatus) {
    AndroidBitmapInfo info{};
    if (bitmap == nullptr ||
        AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        setStatus(env, outStatus, PP_ERR_INVALID_INPUT);
        return 0;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        setStatus(env, outStatus, PP_ERR_INTERNAL);
        return 0;
    }
    pp_session* session = nullptr;
    pp_status status = analyzePixels(env, engine, static_cast<const uint8_t*>(pixels),
                                     static_cast<jint>(info.width), static_cast<jint>(info.height),
                                     static_cast<jint>(info.stride), capability, workingLongEdge,
                                     allowFlatten, userCorners, cancel, &session);
    AndroidBitmap_unlockPixels(env, bitmap);
    setStatus(env, outStatus, status);
    return toHandle(session);
}
#endif

void nativeSessionInfo(JNIEnv* env, jclass, jlong session, jlongArray longs, jdoubleArray doubles) {
    auto* handle = fromHandle<pp_session>(session);
    if (handle == nullptr || longs == nullptr || doubles == nullptr) {
        return;
    }
    pp_analysis_info info{};
    pp_session_info(handle, &info);
    // longs: source, confidence, rotation, orientation confidence, flags, step count, peak bytes,
    //        then (step, status, micros) x PP_STEP_COUNT
    std::vector<jlong> packedLongs;
    packedLongs.reserve(7 + 3 * PP_STEP_COUNT);
    packedLongs.push_back(info.corner_source);
    packedLongs.push_back(info.confidence);
    packedLongs.push_back(info.rotation_applied);
    packedLongs.push_back(info.orientation_confidence);
    packedLongs.push_back(static_cast<jlong>(info.flags));
    packedLongs.push_back(info.step_count);
    packedLongs.push_back(info.peak_bytes);
    for (int i = 0; i < PP_STEP_COUNT; ++i) {
        packedLongs.push_back(info.steps[i].step);
        packedLongs.push_back(info.steps[i].status);
        packedLongs.push_back(info.steps[i].micros);
    }
    const jsize longCount =
        std::min<jsize>(env->GetArrayLength(longs), static_cast<jsize>(packedLongs.size()));
    env->SetLongArrayRegion(longs, 0, longCount, packedLongs.data());
    // doubles: 8 corner coordinates (TL, TR, BR, BL), detected aspect
    jdouble packedDoubles[9];
    for (int i = 0; i < 4; ++i) {
        packedDoubles[i * 2] = info.corners.c[i].x;
        packedDoubles[i * 2 + 1] = info.corners.c[i].y;
    }
    packedDoubles[8] = info.detected_aspect;
    const jsize doubleCount = std::min<jsize>(env->GetArrayLength(doubles), 9);
    env->SetDoubleArrayRegion(doubles, 0, doubleCount, packedDoubles);
}

void packRenderInfo(JNIEnv* env, jlongArray outInfo, pp_status status,
                    const pp_output_image& image) {
    if (outInfo == nullptr) {
        return;
    }
    jlong packed[6] = {status,
                       image.channels,
                       image.style_used,
                       image.fallback_reason,
                       image.steps[0].micros,
                       image.steps[1].micros};
    const jsize count = std::min<jsize>(env->GetArrayLength(outInfo), 6);
    env->SetLongArrayRegion(outInfo, 0, count, packed);
}

jbyteArray nativeRenderToBytes(JNIEnv* env, jclass, jlong session, jint style, jint fit, jint width,
                               jint height, jlong cancel, jlongArray outInfo) {
    pp_output_image image{};
    pp_status status =
        pp_render(fromHandle<pp_session>(session), static_cast<pp_style>(style),
                  static_cast<pp_fit>(fit), width, height, cancelPointer(cancel), &image);
    packRenderInfo(env, outInfo, status, image);
    if (status != PP_OK) {
        pp_output_free(&image);
        return nullptr;
    }
    const jsize bytes = static_cast<jsize>(image.stride_bytes) * image.height;
    jbyteArray result = env->NewByteArray(bytes);
    if (result != nullptr) {
        env->SetByteArrayRegion(result, 0, bytes, reinterpret_cast<const jbyte*>(image.pixels));
    }
    pp_output_free(&image);
    return result;
}

#if defined(PP_ANDROID)
jint nativeRenderToBitmap(JNIEnv* env, jclass, jlong session, jint style, jint fit, jint width,
                          jint height, jlong cancel, jobject bitmap, jlongArray outInfo) {
    pp_output_image image{};
    pp_status status =
        pp_render(fromHandle<pp_session>(session), static_cast<pp_style>(style),
                  static_cast<pp_fit>(fit), width, height, cancelPointer(cancel), &image);
    packRenderInfo(env, outInfo, status, image);
    if (status != PP_OK) {
        pp_output_free(&image);
        return status;
    }
    AndroidBitmapInfo info{};
    void* pixels = nullptr;
    const bool ok = bitmap != nullptr &&
                    AndroidBitmap_getInfo(env, bitmap, &info) == ANDROID_BITMAP_RESULT_SUCCESS &&
                    static_cast<int32_t>(info.width) == image.width &&
                    static_cast<int32_t>(info.height) == image.height &&
                    ((image.channels == 1 && info.format == ANDROID_BITMAP_FORMAT_A_8) ||
                     (image.channels == 4 && info.format == ANDROID_BITMAP_FORMAT_RGBA_8888)) &&
                    AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS;
    if (!ok) {
        pp_output_free(&image);
        return PP_ERR_INVALID_INPUT;
    }
    const size_t rowBytes = static_cast<size_t>(image.width) * image.channels;
    for (int y = 0; y < image.height; ++y) {
        std::memcpy(static_cast<uint8_t*>(pixels) + static_cast<size_t>(y) * info.stride,
                    image.pixels + static_cast<size_t>(y) * image.stride_bytes, rowBytes);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    pp_output_free(&image);
    return PP_OK;
}
#endif

void nativeSessionDestroy(JNIEnv*, jclass, jlong session) {
    pp_session_destroy(fromHandle<pp_session>(session));
}

jstring nativeLastError(JNIEnv* env, jclass) {
    return env->NewStringUTF(pp_last_error());
}

/** JNINativeMethod carries non-const char pointers; the literals are never written. */
JNINativeMethod method(const char* name, const char* signature, void* function) {
    return JNINativeMethod{const_cast<char*>(name), const_cast<char*>(signature), function};
}

const JNINativeMethod kMethods[] = {
    method("nativeApiVersion", "()I", reinterpret_cast<void*>(nativeApiVersion)),
    method("nativeEngineCreate", "()J", reinterpret_cast<void*>(nativeEngineCreate)),
    method("nativeEngineDestroy", "(J)V", reinterpret_cast<void*>(nativeEngineDestroy)),
    method("nativeCancelFlagCreate", "()J", reinterpret_cast<void*>(nativeCancelFlagCreate)),
    method("nativeCancelFlagSet", "(J)V", reinterpret_cast<void*>(nativeCancelFlagSet)),
    method("nativeCancelFlagDestroy", "(J)V", reinterpret_cast<void*>(nativeCancelFlagDestroy)),
    method("nativeAnalyzeBytes", "(J[BIIIIIZ[FJ[I)J", reinterpret_cast<void*>(nativeAnalyzeBytes)),
#if defined(PP_ANDROID)
    method("nativeAnalyzeBitmap", "(JLjava/lang/Object;IIZ[FJ[I)J",
           reinterpret_cast<void*>(nativeAnalyzeBitmap)),
    method("nativeRenderToBitmap", "(JIIIIJLjava/lang/Object;[J)I",
           reinterpret_cast<void*>(nativeRenderToBitmap)),
#endif
    method("nativeSessionInfo", "(J[J[D)V", reinterpret_cast<void*>(nativeSessionInfo)),
    method("nativeRenderToBytes", "(JIIIIJ[J)[B", reinterpret_cast<void*>(nativeRenderToBytes)),
    method("nativeSessionDestroy", "(J)V", reinterpret_cast<void*>(nativeSessionDestroy)),
    method("nativeLastError", "()Ljava/lang/String;", reinterpret_cast<void*>(nativeLastError)),
};

} // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass bridge = env->FindClass(kBridgeClass);
    if (bridge == nullptr) {
        env->ExceptionClear();
        return JNI_ERR;
    }
    // The Kotlin declarations type the Bitmap parameters as Any, hence java/lang/Object below.
    const jint count = static_cast<jint>(sizeof(kMethods) / sizeof(kMethods[0]));
    if (env->RegisterNatives(bridge, kMethods, count) != JNI_OK) {
        env->ExceptionClear();
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
