// Host parity check: the engine's DetectorModel against PyTorch test vectors
// (tools/detector-training: python -m detector.ppdm <ckpt> <model.ppdm> --vectors <vec>).
#include "plainpaper_scan.h"

#include <chrono>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <iterator>
#include <vector>

int main(int argc, char** argv) {
    if (argc < 3) {
        std::fprintf(stderr, "usage: model_parity <model.ppdm> <vectors.vec>\n");
        return 2;
    }
    std::ifstream modelFile(argv[1], std::ios::binary);
    std::vector<uint8_t> model((std::istreambuf_iterator<char>(modelFile)), {});
    pp_engine* engine = pp_engine_create();
    if (pp_engine_load_detector(engine, model.data(), model.size()) != PP_OK) {
        std::fprintf(stderr, "load failed: %s\n", pp_last_error());
        return 1;
    }
    std::ifstream vec(argv[2], std::ios::binary);
    uint32_t count = 0;
    vec.read(reinterpret_cast<char*>(&count), 4);
    const size_t inSize = 3 * 256 * 256, maskSize = 128 * 128;
    double worst = 0;
    for (uint32_t c = 0; c < count; ++c) {
        std::vector<float> input(inSize), expectedMask(maskSize), mask(maskSize);
        float expectedPresence = 0, presence = 0;
        vec.read(reinterpret_cast<char*>(input.data()), inSize * 4);
        vec.read(reinterpret_cast<char*>(expectedMask.data()), maskSize * 4);
        vec.read(reinterpret_cast<char*>(&expectedPresence), 4);
        if (pp_detector_run(engine, input.data(), mask.data(), &presence) != PP_OK) {
            std::fprintf(stderr, "run failed\n");
            return 1;
        }
        for (size_t i = 0; i < maskSize; ++i) {
            worst = std::fmax(worst, std::fabs(mask[i] - expectedMask[i]));
        }
        worst = std::fmax(worst, std::fabs(presence - expectedPresence));
    }
    std::vector<float> input(3 * 256 * 256, 0.5f), mask(128 * 128);
    float presence = 0;
    const auto start = std::chrono::steady_clock::now();
    for (int i = 0; i < 20; ++i) {
        pp_detector_run(engine, input.data(), mask.data(), &presence);
    }
    const double ms =
        std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - start)
            .count() /
        20;
    std::printf("cases=%u max_abs_diff=%.3g ms_per_pass=%.1f\n", count, worst, ms);
    pp_engine_destroy(engine);
    return worst < 1e-4 ? 0 : 1;
}
