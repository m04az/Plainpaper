#pragma once

#include "plainpaper_scan.h"

#include <string>

namespace pp {

/** Thrown by stages to abort analysis/rendering with a specific status (never escapes the C API).
 */
struct Error {
    pp_status status;
    std::string detail;
};

/** Thrown when the caller's cancel flag was observed. */
struct Cancelled {};

/** Stores the thread-local detail returned by pp_last_error(). */
void setLastError(const std::string& detail);
void clearLastError();

} // namespace pp
