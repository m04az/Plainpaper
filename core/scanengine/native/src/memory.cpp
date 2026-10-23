#include "memory.hpp"
#include "debug.hpp"

#include <algorithm>
#include <new>

namespace pp {

CountingAllocator::CountingAllocator() : delegate_(cv::Mat::getStdAllocator()) {}

CountingAllocator& CountingAllocator::instance() {
    static CountingAllocator allocator;
    return allocator;
}

void CountingAllocator::install() {
    static bool installed = false;
    if (!installed) {
        cv::Mat::setDefaultAllocator(&instance());
        installed = true;
    }
}

void CountingAllocator::resetPeak() {
    peak_.store(live_.load());
}

void CountingAllocator::add(int64_t bytes) const {
    const int64_t now = live_.fetch_add(bytes) + bytes;
    int64_t peak = peak_.load();
    while (now > peak && !peak_.compare_exchange_weak(peak, now)) {
    }
}

cv::UMatData* CountingAllocator::allocate(int dims, const int* sizes, int type, void* data,
                                          size_t* step, cv::AccessFlag flags,
                                          cv::UMatUsageFlags usageFlags) const {
    cv::UMatData* result = delegate_->allocate(dims, sizes, type, data, step, flags, usageFlags);
    if (result != nullptr && data == nullptr) {
        // The delegate records itself as the owner; take it back so frees reach this counter.
        result->currAllocator = this;
        result->userdata = const_cast<CountingAllocator*>(this);
        add(static_cast<int64_t>(result->size));
        ++allocations_;
    }
    return result;
}

bool CountingAllocator::allocate(cv::UMatData* data, cv::AccessFlag accessflags,
                                 cv::UMatUsageFlags usageFlags) const {
    return delegate_->allocate(data, accessflags, usageFlags);
}

void CountingAllocator::deallocate(cv::UMatData* data) const {
    if (data != nullptr && data->userdata == this) {
        add(-static_cast<int64_t>(data->size));
        ++frees_;
    }
    delegate_->deallocate(data);
}

namespace {
int gFailAtStage = -1;
} // namespace

void setFailAllocationAtStage(int stage) {
    gFailAtStage = stage;
}

void maybeFailAllocation(int stage) {
    if (gFailAtStage == stage) {
        throw std::bad_alloc();
    }
}

} // namespace pp
