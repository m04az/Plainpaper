#pragma once

#include <opencv2/core.hpp>

namespace pp {

/** Development aids, active only when PP_DEBUG_DIR is set (host builds): PGM dumps and stderr logs.
 */
void debugDump(const char* name, const cv::Mat& image);
void debugLog(const char* message, double a, double b, double c);
bool debugEnabled();

} // namespace pp
