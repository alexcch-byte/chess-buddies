package com.example.chess.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.chess.checkers.Checker

/** Round checkers drawn with sculpted rims, dual tournament grooves, and gold crowns for kings. */
object CheckerArt {
    private val RedFill = Color(0xFFE54335)
    private val RedRim = Color(0xFF9E231B)
    private val RedShine = Color(0xFFFA776B)
    private val RedDark = Color(0xFF75150E)

    private val BlackFill = Color(0xFF383544)
    private val BlackRim = Color(0xFF1B1924)
    private val BlackShine = Color(0xFF6B687D)
    private val BlackDark = Color(0xFF100E17)

    private val Gold = Color(0xFFFFC93C)
    private val GoldLine = Color(0xFF996700)
    private val GoldGleam = Color(0xFFFFF2B2)

    private val AmbientShadow = Color(0x1F000000)
    private val ContactShadow = Color(0x38000000)
    private val CrownShadow = Color(0x40000000)

    private val CrownBasePoints = floatArrayOf(
        -0.62f, 0.42f,
        -0.72f, -0.32f,
        -0.32f, 0.02f,
        0f, -0.52f,
        0.32f, 0.02f,
        0.72f, -0.32f,
        0.62f, 0.42f,
    )

    private val BaseCrownPath: Path by lazy {
        val p = Path()
        var i = 0
        var first = true
        while (i < CrownBasePoints.size) {
            val x = CrownBasePoints[i++]
            val y = CrownBasePoints[i++]
            if (first) {
                p.moveTo(x, y)
                first = false
            } else {
                p.lineTo(x, y)
            }
        }
        p.close()
        p
    }

    private val CrownPearlTips = floatArrayOf(
        -0.72f, -0.32f,
        0f, -0.52f,
        0.72f, -0.32f,
    )

    fun DrawScope.drawChecker(piece: Int, center: Offset, radius: Float, alpha: Float = 1f) {
        if (piece == 0) return
        val red = Checker.isRed(piece)
        val fill = if (red) RedFill else BlackFill
        val rim = if (red) RedRim else BlackRim
        val shine = if (red) RedShine else BlackShine
        val dark = if (red) RedDark else BlackDark

        // 1. Dual-layer grounding drop shadow
        drawCircle(AmbientShadow, radius * 1.05f, center + Offset(0f, radius * 0.12f), alpha = alpha)
        drawCircle(ContactShadow, radius * 0.98f, center + Offset(0f, radius * 0.07f), alpha = alpha)

        // 2. Beveled outer rim
        drawCircle(dark, radius, center, alpha = alpha)
        drawCircle(rim, radius * 0.96f, center, alpha = alpha)

        // 3. Main face
        drawCircle(fill, radius * 0.88f, center, alpha = alpha)

        // 4. Concentric tournament ridges
        drawCircle(rim.copy(alpha = 0.65f), radius * 0.68f, center, alpha = alpha, style = Stroke(width = radius * 0.065f))
        drawCircle(dark.copy(alpha = 0.35f), radius * 0.50f, center, alpha = alpha)
        drawCircle(rim.copy(alpha = 0.55f), radius * 0.44f, center, alpha = alpha, style = Stroke(width = radius * 0.05f))

        // 5. Soft specular highlight dome
        drawCircle(shine.copy(alpha = 0.40f), radius * 0.28f, center - Offset(radius * 0.16f, radius * 0.16f), alpha = alpha)

        // 6. King's Crown
        if (Checker.isKing(piece)) drawCrown(center, radius * 0.62f, alpha)
    }

    private fun DrawScope.drawCrown(center: Offset, size: Float, alpha: Float) {
        // Crown shadow
        withTransform({
            translate(center.x, center.y + size * 0.08f)
            scale(size, size, pivot = Offset.Zero)
        }) {
            drawPath(BaseCrownPath, CrownShadow, alpha = alpha)
        }

        // Crown body
        withTransform({
            translate(center.x, center.y)
            scale(size, size, pivot = Offset.Zero)
        }) {
            drawPath(BaseCrownPath, Gold, alpha = alpha)
            drawPath(BaseCrownPath, GoldLine, alpha = alpha, style = Stroke(width = 0.12f, join = StrokeJoin.Round, cap = StrokeCap.Round))
        }

        // 3 Crown pearl tips with gleams
        var i = 0
        while (i < CrownPearlTips.size) {
            val x = CrownPearlTips[i++]
            val y = CrownPearlTips[i++]
            val tipPos = Offset(center.x + x * size, center.y + y * size)
            drawCircle(Gold, size * 0.13f, tipPos, alpha = alpha)
            drawCircle(GoldLine, size * 0.13f, tipPos, alpha = alpha, style = Stroke(width = size * 0.05f))
            drawCircle(GoldGleam, size * 0.045f, tipPos - Offset(size * 0.035f, size * 0.035f), alpha = alpha)
        }
    }
}

/** A single checker as a picture, for menus and lesson cards. */
@Composable
fun CheckerIcon(piece: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        with(CheckerArt) { drawChecker(piece, Offset(size.width / 2, size.height / 2), minOf(size.width, size.height) * 0.44f) }
    }
}
