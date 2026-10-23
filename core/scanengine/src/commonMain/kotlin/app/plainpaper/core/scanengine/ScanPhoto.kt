package app.plainpaper.core.scanengine

/**
 * A decoded photo handed to the engine. Platform implementations wrap the native bitmap
 * (`Bitmap`, `CGImage`, `BufferedImage`); pixels are read in place by the bridge, never copied
 * into Kotlin (research R11).
 */
public interface ScanPhoto {
    public val width: Int
    public val height: Int
}
