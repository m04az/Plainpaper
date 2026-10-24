#pragma once

#include <cstddef>
#include <cstdint>
#include <map>
#include <mutex>
#include <opencv2/core.hpp>
#include <string>
#include <vector>

namespace pp {

/**
 * The document-detection model (specs/004 research R1/R2): the DocumentNet of
 * tools/detector-training (MobileNetV2-style encoder, U-Net decoder) evaluated with OpenCV core
 * operations, weights from a .ppdm file (BatchNorm folded). Input is the frame resized to
 * kModelInput square (aspect not kept, as in training); outputs are a document probability mask
 * (kModelMask square) and a "document present" probability.
 */
class DetectorModel {
  public:
    /** Parses and validates .ppdm bytes; false with a reason in `error` when unusable. */
    bool load(const uint8_t* bytes, size_t length, std::string& error);
    bool loaded() const;

    /** Runs the net on a BGR 8-bit image. Thread-safe (serialised). */
    bool infer(const cv::Mat& bgr, cv::Mat& mask, float& presence);

    /** Runs the net on a planar RGB float input (3 x kModelInput x kModelInput, 0..1). */
    bool inferPlanar(const float* rgb, cv::Mat& mask, float& presence);

  private:
    struct Param {
        std::vector<int> dims;
        cv::Mat data; // 1 x N float
    };
    struct Tensor {
        int c = 0, h = 0, w = 0;
        cv::Mat data; // c x (h*w) float, continuous
    };

    const Param* param(const std::string& name) const;
    bool validate(std::string& error) const;
    void run(Tensor x, cv::Mat& mask, float& presence) const;
    Tensor conv1x1(const Tensor& x, const std::string& name, bool relu) const;
    Tensor conv3x3(const Tensor& x, const std::string& name, int stride, bool depthwise,
                   bool relu) const;
    Tensor invertedResidual(const Tensor& x, const std::string& prefix, int stride, bool expand,
                            bool residual) const;
    Tensor up(const Tensor& x, const Tensor& skip, const std::string& name) const;

    std::map<std::string, Param> params_;
    bool ready_ = false;
    mutable std::mutex mutex_;
};

} // namespace pp
