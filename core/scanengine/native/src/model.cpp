#include "model.hpp"

#include "params.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <opencv2/imgproc.hpp>
#include <stdexcept>

namespace pp {

namespace {

constexpr uint32_t kFormatVersion = 1;
constexpr uint32_t kArchVersion = 1;

/** One encoder stage of DocumentNet: channels in/out, first stride, blocks, expansion. */
struct StageSpec {
    int cin, cout, stride, repeats, expand;
};
constexpr StageSpec kStages[] = {
    {16, 16, 1, 1, 1}, {16, 24, 2, 2, 4}, {24, 32, 2, 3, 4},
    {32, 64, 2, 3, 4}, {64, 96, 1, 2, 4}, {96, 128, 2, 2, 4},
};

struct Reader {
    const uint8_t* p;
    const uint8_t* end;
    bool take(void* out, size_t n) {
        if (static_cast<size_t>(end - p) < n) {
            return false;
        }
        std::memcpy(out, p, n);
        p += n;
        return true;
    }
};

void relu6(cv::Mat& m) {
    cv::max(m, 0.0, m);
    cv::min(m, 6.0, m);
}

void addBias(cv::Mat& rows, const cv::Mat& bias) {
    const float* b = bias.ptr<float>();
    for (int r = 0; r < rows.rows; ++r) {
        rows.row(r) += b[r];
    }
}

} // namespace

bool DetectorModel::load(const uint8_t* bytes, size_t length, std::string& error) {
    std::lock_guard<std::mutex> lock(mutex_);
    ready_ = false;
    params_.clear();
    if (bytes == nullptr || length < 16) {
        error = "empty model";
        return false;
    }
    Reader in{bytes, bytes + length};
    char magic[4];
    uint32_t format = 0, arch = 0, count = 0;
    if (!in.take(magic, 4) || std::memcmp(magic, "PPDM", 4) != 0 || !in.take(&format, 4) ||
        !in.take(&arch, 4) || !in.take(&count, 4)) {
        error = "not a .ppdm model";
        return false;
    }
    if (format != kFormatVersion || arch != kArchVersion) {
        error = "unsupported model version";
        return false;
    }
    for (uint32_t i = 0; i < count; ++i) {
        uint16_t nameLength = 0;
        uint8_t ndim = 0;
        if (!in.take(&nameLength, 2)) {
            error = "truncated model";
            return false;
        }
        std::string name(nameLength, '\0');
        if (!in.take(name.data(), nameLength) || !in.take(&ndim, 1) || ndim == 0 || ndim > 4) {
            error = "truncated model";
            return false;
        }
        Param param;
        size_t elements = 1;
        for (int d = 0; d < ndim; ++d) {
            uint32_t dim = 0;
            if (!in.take(&dim, 4) || dim == 0) {
                error = "truncated model";
                return false;
            }
            param.dims.push_back(static_cast<int>(dim));
            elements *= dim;
        }
        param.data.create(1, static_cast<int>(elements), CV_32F);
        if (!in.take(param.data.ptr<float>(), elements * sizeof(float))) {
            error = "truncated model";
            return false;
        }
        params_[name] = std::move(param);
    }
    if (!validate(error)) {
        params_.clear();
        return false;
    }
    ready_ = true;
    return true;
}

bool DetectorModel::loaded() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return ready_;
}

const DetectorModel::Param* DetectorModel::param(const std::string& name) const {
    auto it = params_.find(name);
    return it == params_.end() ? nullptr : &it->second;
}

bool DetectorModel::validate(std::string& error) const {
    // Run once on a blank input: every layer looks its weights up and checks their shapes.
    try {
        Tensor x;
        x.c = 3;
        x.h = x.w = params::kModelInput;
        x.data = cv::Mat::zeros(3, x.h * x.w, CV_32F);
        cv::Mat mask;
        float presence = 0;
        run(x, mask, presence);
        if (mask.rows != params::kModelMask || mask.cols != params::kModelMask) {
            error = "unexpected mask size";
            return false;
        }
        return true;
    } catch (const std::exception& e) {
        error = e.what();
        return false;
    }
}

DetectorModel::Tensor DetectorModel::conv1x1(const Tensor& x, const std::string& name,
                                             bool relu) const {
    const Param* w = param(name + ".w");
    const Param* b = param(name + ".b");
    if (w == nullptr || b == nullptr || w->dims.size() != 4 || w->dims[1] != x.c ||
        w->dims[2] != 1 || w->dims[3] != 1) {
        throw std::runtime_error("bad layer " + name);
    }
    const int cout = w->dims[0];
    cv::Mat weights = w->data.reshape(1, cout); // cout x cin
    Tensor y;
    y.c = cout;
    y.h = x.h;
    y.w = x.w;
    // Direct axpy form (Y[o] = b[o] + sum_i W[o,i] X[i]): contiguous rows the compiler
    // vectorises; cv::gemm was 85% of the pass time at these shapes.
    const int n = x.h * x.w;
    y.data.create(cout, n, CV_32F);
    const float* bias = b->data.ptr<float>();
    cv::parallel_for_(cv::Range(0, cout), [&](const cv::Range& range) {
        for (int o = range.start; o < range.end; ++o) {
            float* out = y.data.ptr<float>(o);
            std::fill(out, out + n, bias[o]);
            const float* wRow = weights.ptr<float>(o);
            for (int i = 0; i < x.c; ++i) {
                const float wi = wRow[i];
                const float* in = x.data.ptr<float>(i);
                for (int k = 0; k < n; ++k) {
                    out[k] += wi * in[k];
                }
            }
        }
    });
    if (relu) {
        relu6(y.data);
    }
    return y;
}

DetectorModel::Tensor DetectorModel::conv3x3(const Tensor& x, const std::string& name, int stride,
                                             bool depthwise, bool relu) const {
    const Param* w = param(name + ".w");
    const Param* b = param(name + ".b");
    const int expectedIn = depthwise ? 1 : x.c;
    if (w == nullptr || b == nullptr || w->dims.size() != 4 || w->dims[1] != expectedIn ||
        w->dims[2] != 3 || w->dims[3] != 3 || (depthwise && w->dims[0] != x.c)) {
        throw std::runtime_error("bad layer " + name);
    }
    const int cout = w->dims[0];
    Tensor y;
    y.c = cout;
    y.h = (x.h + stride - 1) / stride;
    y.w = (x.w + stride - 1) / stride;
    y.data.create(cout, y.h * y.w, CV_32F);
    const float* weights = w->data.ptr<float>();
    const int inputs = depthwise ? 1 : x.c;
    cv::parallel_for_(cv::Range(0, cout), [&](const cv::Range& range) {
        for (int o = range.start; o < range.end; ++o) {
            float* out = y.data.ptr<float>(o);
            std::fill(out, out + y.h * y.w, 0.0f);
            for (int i = 0; i < inputs; ++i) {
                const int channel = depthwise ? o : i;
                const float* in = x.data.ptr<float>(channel);
                const float* k = weights + (o * inputs + i) * 9;
                // Zero padding of 1; output (r, c) is centred on input (r*stride, c*stride),
                // as in PyTorch's conv (cross-correlation).
                for (int r = 0; r < y.h; ++r) {
                    float* outRow = out + r * y.w;
                    for (int dr = 0; dr < 3; ++dr) {
                        const int ir = r * stride + dr - 1;
                        if (ir < 0 || ir >= x.h) {
                            continue;
                        }
                        const float* inRow = in + ir * x.w;
                        for (int dc = 0; dc < 3; ++dc) {
                            const float kv = k[dr * 3 + dc];
                            const int first = std::max(0, (1 - dc + stride - 1) / stride);
                            const int last = std::min(y.w, (x.w - dc + 1 + stride - 1) / stride);
                            if (stride == 1) {
                                const float* src = inRow + dc - 1;
                                for (int c = first; c < last; ++c) {
                                    outRow[c] += kv * src[c];
                                }
                            } else {
                                for (int c = first; c < last; ++c) {
                                    outRow[c] += kv * inRow[c * stride + dc - 1];
                                }
                            }
                        }
                    }
                }
            }
        }
    });
    addBias(y.data, b->data);
    if (relu) {
        relu6(y.data);
    }
    return y;
}

DetectorModel::Tensor DetectorModel::invertedResidual(const Tensor& x, const std::string& prefix,
                                                      int stride, bool expand,
                                                      bool residual) const {
    Tensor hidden = expand ? conv1x1(x, prefix + ".expand", true) : x;
    Tensor dw = conv3x3(hidden, prefix + ".dw", stride, true, true);
    Tensor y = conv1x1(dw, prefix + ".project", false);
    if (residual) {
        y.data += x.data;
    }
    return y;
}

DetectorModel::Tensor DetectorModel::up(const Tensor& x, const Tensor& skip,
                                        const std::string& name) const {
    Tensor upsampled;
    upsampled.c = x.c;
    upsampled.h = x.h * 2;
    upsampled.w = x.w * 2;
    upsampled.data.create(x.c, upsampled.h * upsampled.w, CV_32F);
    for (int i = 0; i < x.c; ++i) {
        const cv::Mat plane(x.h, x.w, CV_32F, const_cast<float*>(x.data.ptr<float>(i)));
        cv::Mat out(upsampled.h, upsampled.w, CV_32F, upsampled.data.ptr<float>(i));
        // Half-pixel bilinear, the same as PyTorch's align_corners=False.
        cv::resize(plane, out, out.size(), 0, 0, cv::INTER_LINEAR);
    }
    Tensor joined;
    joined.c = upsampled.c + skip.c;
    joined.h = upsampled.h;
    joined.w = upsampled.w;
    cv::vconcat(upsampled.data, skip.data, joined.data);
    Tensor fused = conv1x1(joined, name + ".fuse", true);
    Tensor refined = conv3x3(fused, name + ".dw", 1, true, true);
    return conv1x1(refined, name + ".pw", true);
}

void DetectorModel::run(Tensor x, cv::Mat& mask, float& presence) const {
    Tensor f = conv3x3(x, "stem", 2, false, true);
    std::vector<Tensor> outputs;
    for (int s = 0; s < 6; ++s) {
        const StageSpec& spec = kStages[s];
        for (int bIndex = 0; bIndex < spec.repeats; ++bIndex) {
            const int cin = bIndex == 0 ? spec.cin : spec.cout;
            const int stride = bIndex == 0 ? spec.stride : 1;
            const std::string prefix = "s" + std::to_string(s + 1) + "." + std::to_string(bIndex);
            f = invertedResidual(f, prefix, stride, spec.expand != 1,
                                 stride == 1 && cin == spec.cout);
        }
        outputs.push_back(f);
    }
    // outputs: s1 128, s2 64, s3 32, s4 16, s5 16, s6 8
    Tensor y = up(outputs[5], outputs[4], "up16");
    y = up(y, outputs[2], "up32");
    y = up(y, outputs[1], "up64");
    y = up(y, outputs[0], "up128");
    Tensor logits = conv1x1(y, "mask", false);
    cv::Mat plane = logits.data.reshape(1, logits.h);
    cv::Mat negative;
    cv::exp(-plane, negative);
    mask = 1.0 / (1.0 + negative);

    const Param* pw = param("presence.w");
    const Param* pb = param("presence.b");
    const Tensor& deepest = outputs[5];
    if (pw == nullptr || pb == nullptr || static_cast<int>(pw->data.total()) != deepest.c) {
        throw std::runtime_error("bad layer presence");
    }
    cv::Mat pooled;
    cv::reduce(deepest.data, pooled, 1, cv::REDUCE_AVG); // c x 1
    const double logit = pw->data.reshape(1, 1).dot(pooled.reshape(1, 1)) + pb->data.at<float>(0);
    presence = static_cast<float>(1.0 / (1.0 + std::exp(-logit)));
}

bool DetectorModel::infer(const cv::Mat& bgr, cv::Mat& mask, float& presence) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!ready_) {
        return false;
    }
    cv::Mat resized;
    cv::resize(bgr, resized, cv::Size(params::kModelInput, params::kModelInput), 0, 0,
               cv::INTER_AREA);
    cv::Mat rgb;
    cv::cvtColor(resized, rgb, cv::COLOR_BGR2RGB);
    std::vector<cv::Mat> planes;
    cv::split(rgb, planes);
    Tensor x;
    x.c = 3;
    x.h = x.w = params::kModelInput;
    x.data.create(3, x.h * x.w, CV_32F);
    for (int i = 0; i < 3; ++i) {
        cv::Mat row(x.h, x.w, CV_32F, x.data.ptr<float>(i));
        planes[i].convertTo(row, CV_32F, 1.0 / 255.0);
    }
    run(x, mask, presence);
    return true;
}

bool DetectorModel::inferPlanar(const float* rgb, cv::Mat& mask, float& presence) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!ready_ || rgb == nullptr) {
        return false;
    }
    Tensor x;
    x.c = 3;
    x.h = x.w = params::kModelInput;
    x.data = cv::Mat(3, x.h * x.w, CV_32F, const_cast<float*>(rgb)).clone();
    run(x, mask, presence);
    return true;
}

} // namespace pp
