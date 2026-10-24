package app.plainpaper.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 2f

/**
 * Line icons from the approved design (24dp viewport, 2dp round strokes). Each is drawn in
 * black and tinted by [androidx.compose.material3.Icon]; chevrons and arrows auto-mirror in RTL.
 */
public object PpIcons {
    public val ScanFrame: ImageVector by lazy {
        strokeIcon("ScanFrame") {
            path("M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3")
            path("M8 12h8")
        }
    }

    public val ImportPhoto: ImageVector by lazy {
        strokeIcon("ImportPhoto") {
            path("M3 4h18v16H3z")
            path("M9 10m-2 0a2 2 0 1 0 4 0a2 2 0 1 0-4 0")
            path("M21 16l-5-5-9 9")
        }
    }

    public val Document: ImageVector by lazy {
        strokeIcon("Document") {
            path("M6 3h8l5 5v13H6z")
            path("M14 3v5h5")
        }
    }

    public val Folder: ImageVector by lazy {
        strokeIcon("Folder") {
            path("M3 6h7l2 2h9v11H3z")
        }
    }

    public val Settings: ImageVector by lazy {
        strokeIcon("Settings") {
            path("M12 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
            path("M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1")
        }
    }

    public val Globe: ImageVector by lazy {
        strokeIcon("Globe") {
            path("M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0-18 0")
            path("M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18")
        }
    }

    public val Moon: ImageVector by lazy {
        strokeIcon("Moon") {
            path("M20 14A8 8 0 1 1 10 4a6 6 0 0 0 10 10z")
        }
    }

    public val Shield: ImageVector by lazy {
        strokeIcon("Shield") {
            path("M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z")
        }
    }

    public val Info: ImageVector by lazy {
        strokeIcon("Info") {
            path("M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0-18 0")
            path("M12 11v6M12 7.5v.5")
        }
    }

    public val Check: ImageVector by lazy {
        strokeIcon("Check") {
            path("M5 12l5 5 9-10")
        }
    }

    /** Points to the "end" side; mirrored automatically in RTL. */
    public val ChevronEnd: ImageVector by lazy {
        strokeIcon("ChevronEnd", autoMirror = true) {
            path("M9 5l7 7-7 7")
        }
    }

    /** Points to the "start" side; mirrored automatically in RTL. */
    public val Flash: ImageVector by lazy {
        strokeIcon("Flash") {
            path("M13 2L4 14h7l-1 8 9-12h-7z")
        }
    }

    public val Close: ImageVector by lazy {
        strokeIcon("Close") {
            path("M6 6l12 12M18 6L6 18")
        }
    }

    public val Warning: ImageVector by lazy {
        strokeIcon("Warning") {
            path("M12 3l10 18H2z")
            path("M12 10v5M12 18v.5")
        }
    }

    public val Lock: ImageVector by lazy {
        strokeIcon("Lock") {
            path("M5 10h14v10H5z")
            path("M8 10V7a4 4 0 0 1 8 0v3")
        }
    }

    public val ArrowBack: ImageVector by lazy {
        strokeIcon("ArrowBack", autoMirror = true) {
            path("M19 12H5M11 6l-6 6 6 6")
        }
    }
}

private class StrokeIconScope(
    private val builder: ImageVector.Builder,
) {
    fun path(data: String) {
        builder.addPath(
            pathData = PathParser().parsePathString(data).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}

private fun strokeIcon(
    name: String,
    autoMirror: Boolean = false,
    block: StrokeIconScope.() -> Unit,
): ImageVector {
    val builder =
        ImageVector.Builder(
            name = name,
            defaultWidth = VIEWPORT.dp,
            defaultHeight = VIEWPORT.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
            autoMirror = autoMirror,
        )
    StrokeIconScope(builder).block()
    return builder.build()
}
