package app.plainpaper.core.data

actual fun tempDirectoryPath(): String = System.getProperty("java.io.tmpdir")
