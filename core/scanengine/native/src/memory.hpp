#pragma once

#include <cstdint>
#include <opencv2/core.hpp>

namespace pp {

/**
 * Approximate high-water mark of image memory held by a stage (FR-019 diagnostics). Stages
 * declare the large buffers they keep alive; it is not a malloc hook.
 */
class MemoryTracker {
  public:
    void hold(const cv::Mat& mat) {
        live_ += bytesOf(mat);
        if (live_ > peak_) {
            peak_ = live_;
        }
    }

    void release(const cv::Mat& mat) {
        auto bytes = bytesOf(mat);
        live_ = bytes > live_ ? 0 : live_ - bytes;
    }

    int64_t peak() const { return peak_; }

    static int64_t bytesOf(const cv::Mat& mat) {
        return static_cast<int64_t>(mat.total()) * static_cast<int64_t>(mat.elemSize());
    }

  private:
    int64_t live_ = 0;
    int64_t peak_ = 0;
};

} // namespace pp
