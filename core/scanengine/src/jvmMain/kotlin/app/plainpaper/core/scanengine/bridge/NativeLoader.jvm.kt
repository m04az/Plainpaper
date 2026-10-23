package app.plainpaper.core.scanengine.bridge

/** Set by the Gradle tasks of `:tools:scansuite` to the CMake host build output. */
public const val NATIVE_PATH_PROPERTY: String = "pp.native.path"

private val loaded: Unit by lazy {
    val path =
        System.getProperty(NATIVE_PATH_PROPERTY)
            ?: error(
                "System property $NATIVE_PATH_PROPERTY must point at libplainpaper_scan (run :core:scanengine:buildNativeHost)",
            )
    System.load(path)
}

public actual fun loadNativeLibrary() {
    loaded
}

public actual fun platformNativeBridge(): NativeBridge = JvmNativeBridge()
