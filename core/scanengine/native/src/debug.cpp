#include "debug.hpp"

#include <cstdio>
#include <cstdlib>
#include <opencv2/imgproc.hpp>
#include <string>

namespace pp {

bool debugEnabled() {
    return std::getenv("PP_DEBUG_DIR") != nullptr;
}

void debugDump(const char* name, const cv::Mat& image) {
    const char* dir = std::getenv("PP_DEBUG_DIR");
    if (dir == nullptr || image.empty()) {
        return;
    }
    cv::Mat single;
    if (image.channels() == 3) {
        cv::cvtColor(image, single, cv::COLOR_BGR2GRAY);
    } else if (image.channels() == 4) {
        cv::cvtColor(image, single, cv::COLOR_BGRA2GRAY);
    } else {
        single = image;
    }
    if (single.depth() != CV_8U) {
        single.convertTo(single, CV_8U);
    }
    const std::string path = std::string(dir) + "/" + name + ".pgm";
    FILE* f = std::fopen(path.c_str(), "wb");
    if (f == nullptr) {
        return;
    }
    std::fprintf(f, "P5\n%d %d\n255\n", single.cols, single.rows);
    for (int y = 0; y < single.rows; ++y) {
        std::fwrite(single.ptr<uint8_t>(y), 1, static_cast<size_t>(single.cols), f);
    }
    std::fclose(f);
}

void debugLog(const char* message, double a, double b, double c) {
    if (debugEnabled()) {
        std::fprintf(stderr, "[pp] %s %.4f %.4f %.4f\n", message, a, b, c);
    }
}

} // namespace pp
