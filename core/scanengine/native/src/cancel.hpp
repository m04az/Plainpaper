#pragma once

#include "errors.hpp"

#include <cstdint>

namespace pp {

/** Cooperative cancellation: stages call this between steps and inside banded loops. */
inline void checkCancel(volatile int32_t* flag) {
    if (flag != nullptr && *flag != 0) {
        throw Cancelled{};
    }
}

} // namespace pp
