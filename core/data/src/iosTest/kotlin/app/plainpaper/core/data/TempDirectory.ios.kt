package app.plainpaper.core.data

import platform.Foundation.NSTemporaryDirectory

actual fun tempDirectoryPath(): String = NSTemporaryDirectory()
