package app.plainpaper.core.data.platform

import okio.FileSystem

/** The real file system of the device. */
internal expect fun systemFileSystem(): FileSystem

/** Wall-clock time in milliseconds since the epoch. */
internal expect fun currentTimeMillis(): Long
