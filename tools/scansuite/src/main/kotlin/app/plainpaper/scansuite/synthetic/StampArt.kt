package app.plainpaper.scansuite.synthetic

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import kotlin.math.cos
import kotlin.math.sin

/** A red circular stamp and a blue signature scribble at known page positions (Color document checks). */
object StampArt {
    val STAMP_COLOR: Color = Color(200, 30, 40)
    val SIGNATURE_COLOR: Color = Color(30, 50, 180)
    const val CENTER_X = 900
    const val CENTER_Y = 1450
    const val RADIUS = 140
    const val SIGNATURE_X = 200
    const val SIGNATURE_Y = 1500

    fun draw(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = STAMP_COLOR
        g.stroke = BasicStroke(10f)
        g.drawOval(CENTER_X - RADIUS, CENTER_Y - RADIUS, 2 * RADIUS, 2 * RADIUS)
        g.drawOval(CENTER_X - RADIUS + 24, CENTER_Y - RADIUS + 24, 2 * RADIUS - 48, 2 * RADIUS - 48)
        g.stroke = BasicStroke(6f)
        for (i in 0 until 8) {
            val a = i * Math.PI / 4
            val r1 = RADIUS - 24
            val r0 = r1 - 40
            g.drawLine(
                (CENTER_X + r0 * cos(a)).toInt(),
                (CENTER_Y + r0 * sin(a)).toInt(),
                (CENTER_X + r1 * cos(a)).toInt(),
                (CENTER_Y + r1 * sin(a)).toInt(),
            )
        }
        g.color = SIGNATURE_COLOR
        g.stroke = BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val path = Path2D.Double()
        path.moveTo(SIGNATURE_X.toDouble(), SIGNATURE_Y.toDouble())
        for (i in 1..12) {
            val x = SIGNATURE_X + i * 30.0
            val y = SIGNATURE_Y + (if (i % 2 == 0) -40.0 else 40.0) * (1.0 + (i % 3) * 0.3)
            path.quadTo(x - 15.0, y, x, SIGNATURE_Y.toDouble())
        }
        g.draw(path)
    }
}
