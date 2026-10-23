#pragma once

#include "plainpaper_scan.h"

#include <chrono>
#include <cstring>

namespace pp {

/** Per-step status and duration table (FR-019). */
struct Timings {
    pp_step_timing steps[PP_STEP_COUNT];
    int32_t count = 0;

    Timings() { std::memset(steps, 0, sizeof(steps)); }

    void record(pp_step step, pp_step_status status, int64_t micros) {
        if (count < PP_STEP_COUNT) {
            steps[count++] = pp_step_timing{step, status, micros};
        }
    }
};

/** RAII timer: records the step on destruction with the status set by the stage. */
class StepTimer {
  public:
    StepTimer(Timings& timings, pp_step step)
        : timings_(timings), step_(step), start_(std::chrono::steady_clock::now()) {}

    void setStatus(pp_step_status status) { status_ = status; }

    ~StepTimer() {
        auto micros = std::chrono::duration_cast<std::chrono::microseconds>(
                          std::chrono::steady_clock::now() - start_)
                          .count();
        timings_.record(step_, status_, status_ == PP_STEP_APPLIED ? micros : 0);
    }

    StepTimer(const StepTimer&) = delete;
    StepTimer& operator=(const StepTimer&) = delete;

  private:
    Timings& timings_;
    pp_step step_;
    pp_step_status status_ = PP_STEP_APPLIED;
    std::chrono::steady_clock::time_point start_;
};

} // namespace pp
