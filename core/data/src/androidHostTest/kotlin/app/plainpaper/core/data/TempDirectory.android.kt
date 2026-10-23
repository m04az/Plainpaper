package app.plainpaper.core.data

actual fun tempDirectoryPath(): String = requireNotNull(System.getProperty("java.io.tmpdir"))
