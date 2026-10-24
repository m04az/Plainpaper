package app.plainpaper.core.data.platform

import okio.FileSystem
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

private const val MILLIS_PER_SECOND = 1000.0

internal actual fun systemFileSystem(): FileSystem = FileSystem.SYSTEM

internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()
