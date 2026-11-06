package com.example.chess.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.chess.engine.Square
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Drawing helpers shared by the chess and checkers boards. */

enum class BoardTheme(val id: String, val label: String, val light: Color, val dark: Color) {
    MEADOW("meadow", "Meadow", Color(0xFFF3E7C9), Color(0xFF7DB46C)),
    OCEAN("ocean", "Ocean", Color(0xFFE6F1FB), Color(0xFF6A9FD4)),
    CANDY("candy", "Candy", Color(0xFFFDE7EF), Color(0xFFE08DB0)),
    WOOD("wood", "Wood", Color(0xFFF0D9B5), Color(0xFFB58863)),
    ;

    companion object {
        fun byId(id: String?): BoardTheme = entries.firstOrNull { it.id == id } ?: MEADOW
    }
}

internal val SelectColor = Color(0xFFFFE066)
internal val HintColor = Color(0xFF00B894)
internal val DangerColor = Color(0xFFFF4D4D)
internal val DotColor = Color(0x33000000)
private val StarFill = Color(0xFFFFC93C)
private val StarLine = Color(0xFFD98A00)

private val BaseStarPath: Path by lazy {
    val path = Path()
    val outer = 1f
    val inner = 0.45f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val angle = -PI / 2 + i * PI / 5
        val x = (r * cos(angle)).toFloat()
        val y = (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    path
}

private class CoordinateMeasurementCache {
    var cell: Float = 0f
    val widths = FloatArray(8)
    val heights = FloatArray(8)

    fun ensure(cell: Float, textMeasurer: TextMeasurer, textStyle: TextStyle) {
        if (this.cell == cell) return
        this.cell = cell
        for (i in 0 until 8) {
            val measured = textMeasurer.measure("${'a' + i}", textStyle)
            widths[i] = measured.size.width.toFloat()
            heights[i] = measured.size.height.toFloat()
        }
    }
}

private val coordMeasurementCache = CoordinateMeasurementCache()

/** Square, rounded, with a soft shadow. */
internal fun Modifier.boardFrame(): Modifier = this
    .aspectRatio(1f)
    .shadow(10.dp, RoundedCornerShape(14.dp))
    .clip(RoundedCornerShape(14.dp))

/** A value that gently goes 0 -> 1 -> 0, for pulsing highlights. */
@Composable
internal fun rememberPulse(): State<Float> = rememberInfiniteTransition(label = "pulse").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
    label = "pulse",
)

/**
 * Progress (0..1) of the move animation with [id]. Until the animation actually starts it reports 0,
 * so the board never flashes the finished move first. Null [id] means nothing is animating.
 */
@Composable
internal fun rememberMoveProgress(id: Long?, durationMs: Int): Float {
    val progress = remember { Animatable(1f) }
    var runningId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        progress.snapTo(0f)
        runningId = id
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return when {
        id == null -> 1f
        runningId != id -> 0f
        else -> progress.value
    }
}

fun squareAt(offset: Offset, boardSize: Float, flipped: Boolean): Int? {
    val cell = boardSize / 8f
    val col = (offset.x / cell).toInt()
    val row = (offset.y / cell).toInt()
    if (offset.x < 0 || offset.y < 0 || col !in 0..7 || row !in 0..7) return null
    val file = if (flipped) 7 - col else col
    val rank = if (flipped) row else 7 - row
    return Square.of(file, rank)
}

fun squareTopLeft(sq: Int, cell: Float, flipped: Boolean): Offset {
    val col = if (flipped) 7 - Square.file(sq) else Square.file(sq)
    val row = if (flipped) Square.rank(sq) else 7 - Square.rank(sq)
    return Offset(col * cell, row * cell)
}

internal fun squareCenter(sq: Int, cell: Float, flipped: Boolean): Offset =
    squareTopLeft(sq, cell, flipped) + Offset(cell / 2, cell / 2)

internal fun DrawScope.drawSquares(theme: BoardTheme, cell: Float, flipped: Boolean) {
    drawRect(theme.light)
    for (sq in 0 until 64) {
        if (!Square.isLight(sq)) drawRect(theme.dark, squareTopLeft(sq, cell, flipped), Size(cell, cell))
    }
}

internal fun DrawScope.highlight(sq: Int, cell: Float, flipped: Boolean, color: Color) {
    drawRect(color, squareTopLeft(sq, cell, flipped), Size(cell, cell))
}

internal fun DrawScope.drawCoordinates(textMeasurer: TextMeasurer, theme: BoardTheme, cell: Float, flipped: Boolean) {
    val fontSize = (cell * 0.17f).toSp()
    val baseStyle = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold)
    coordMeasurementCache.ensure(cell, textMeasurer, baseStyle)

    for (i in 0 until 8) {
        // Rank numbers down the left edge.
        val rank = if (flipped) i else 7 - i
        val leftSq = Square.of(if (flipped) 7 else 0, rank)
        val rankColor = if (Square.isLight(leftSq)) theme.dark else theme.light
        drawText(
            textMeasurer,
            "${rank + 1}",
            topLeft = Offset(cell * 0.05f, i * cell + cell * 0.02f),
            style = TextStyle(color = rankColor, fontSize = fontSize, fontWeight = FontWeight.Bold),
        )
        // File letters along the bottom edge.
        val file = if (flipped) 7 - i else i
        val bottomSq = Square.of(file, if (flipped) 7 else 0)
        val fileColor = if (Square.isLight(bottomSq)) theme.dark else theme.light
        val w = coordMeasurementCache.widths[file]
        val h = coordMeasurementCache.heights[file]
        drawText(
            textMeasurer,
            "${'a' + file}",
            topLeft = Offset((i + 1) * cell - w - cell * 0.05f, 8 * cell - h - cell * 0.01f),
            style = TextStyle(color = fileColor, fontSize = fontSize, fontWeight = FontWeight.Bold),
        )
    }
}

internal fun DrawScope.drawStar(sq: Int, cell: Float, flipped: Boolean, scaleBy: Float) {
    val c = squareCenter(sq, cell, flipped)
    val r = cell * 0.36f
    withTransform({
        translate(c.x, c.y)
        scale(r * scaleBy, r * scaleBy, pivot = Offset.Zero)
    }) {
        drawPath(BaseStarPath, StarFill)
        drawPath(BaseStarPath, StarLine, style = Stroke(width = 0.111f / scaleBy.coerceAtLeast(0.001f)))
    }
}

/** A soft pulsing ring that marks a piece the opponent could capture. */
internal fun DrawScope.drawDangerRing(sq: Int, cell: Float, flipped: Boolean, pulse: Float) {
    val c = squareCenter(sq, cell, flipped)
    drawCircle(DangerColor.copy(alpha = 0.10f + 0.12f * pulse), radius = cell * 0.46f, center = c)
    drawCircle(DangerColor.copy(alpha = 0.55f + 0.35f * pulse), radius = cell * 0.45f, center = c, style = Stroke(width = cell * 0.035f))
}

/** Dot on an empty target square, ring around an occupied one. */
internal fun DrawScope.drawTargetMarker(sq: Int, cell: Float, flipped: Boolean, occupied: Boolean) {
    val c = squareCenter(sq, cell, flipped)
    if (occupied) drawCircle(DotColor, radius = cell * 0.46f, center = c, style = Stroke(width = cell * 0.08f))
    else drawCircle(DotColor, radius = cell * 0.16f, center = c)
}

/** An arrow along [squares] (two or more), with a head on the last leg. */
internal fun DrawScope.drawArrow(squares: List<Int>, cell: Float, flipped: Boolean, color: Color) {
    if (squares.size < 2) return
    val points = squares.map { squareCenter(it, cell, flipped) }
    val head = cell * 0.38f
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val last = i == points.size - 2
        val angle = atan2(b.y - a.y, b.x - a.x)
        val end = if (last) b - Offset(cos(angle), sin(angle)) * (head * 0.8f) else b
        drawLine(color, a, end, strokeWidth = cell * 0.16f, cap = StrokeCap.Round)
        if (last) {
            rotate(Math.toDegrees(angle.toDouble()).toFloat(), pivot = b) {
                val p = Path().apply {
                    moveTo(b.x, b.y)
                    lineTo(b.x - head, b.y - head * 0.7f)
                    lineTo(b.x - head, b.y + head * 0.7f)
                    close()
                }
                drawPath(p, color)
            }
        }
    }
}
