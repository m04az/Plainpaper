package app.plainpaper.core.scanengine.bridge

private val loaded: Unit by lazy { System.loadLibrary("plainpaper_scan") }

public actual fun loadNativeLibrary() {
    loaded
}

public actual fun platformNativeBridge(): NativeBridge = AndroidNativeBridge()
