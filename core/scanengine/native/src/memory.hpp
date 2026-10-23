#pragma once

#include <atomic>
#include <cstdint>
#include <opencv2/core.hpp>

namespace pp {

/**
 * Counts the bytes OpenCV allocates for cv::Mat buffers (FR-019 diagnostics, research R11).
 * Installed as the default allocator; the counters are process-wide and reset per analysis,
 * which is exact when one scan runs at a time (the engine's intended use).
 */
class CountingAllocator : public cv::MatAllocator {
  public:
    static CountingAllocator& instance();

    /** Installs the allocator once (idempotent). */
    static void install();

    void resetPeak();
    int64_t peakBytes() const { return peak_.load(); }
    int64_t liveBytes() const { return live_.load(); }

    cv::UMatData* allocate(int dims, const int* sizes, int type, void* data, size_t* step,
                           cv::AccessFlag flags, cv::UMatUsageFlags usageFlags) const override;
    bool allocate(cv::UMatData* data, cv::AccessFlag accessflags,
                  cv::UMatUsageFlags usageFlags) const override;
    void deallocate(cv::UMatData* data) const override;

  private:
    CountingAllocator();
    void add(int64_t bytes) const;

    cv::MatAllocator* delegate_;
    mutable std::atomic<int64_t> live_{0};
    mutable std::atomic<int64_t> peak_{0};
    mutable std::atomic<int64_t> allocations_{0};
    mutable std::atomic<int64_t> frees_{0};
};

/** Test hook (host builds): makes the stage with this index throw std::bad_alloc; -1 disables. */
void setFailAllocationAtStage(int stage);
void maybeFailAllocation(int stage);

} // namespace pp
