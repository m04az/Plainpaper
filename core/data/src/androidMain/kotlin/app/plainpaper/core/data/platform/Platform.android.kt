package app.plainpaper.core.data.platform

import okio.FileSystem

internal actual fun systemFileSystem(): FileSystem = FileSystem.SYSTEM

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()
