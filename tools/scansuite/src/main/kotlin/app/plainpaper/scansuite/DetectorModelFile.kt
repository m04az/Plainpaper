package app.plainpaper.scansuite

import app.plainpaper.core.scanengine.DetectorModelSource
import java.io.File

/** The bundled detection model, when it exists in the repo (specs/004); absent means classic only. */
object DetectorModelFile {
    private const val RELATIVE_PATH = "composeApp/src/commonMain/composeResources/files/detector.ppdm"

    /** Source for the model file under the working directory (the repo root), or null when missing. */
    fun source(rootDir: File = File(System.getProperty("user.dir"))): DetectorModelSource? {
        val file = File(rootDir, RELATIVE_PATH)
        return if (file.isFile) DetectorModelSource { file.readBytes() } else null
    }
}
