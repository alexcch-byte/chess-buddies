package com.example.chess.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.example.chess.engine.Piece
import kotlin.math.abs

/**
 * Hand-drawn, sculpted vector piece graphics on a 100 x 100 grid.
 * Designed with 3D volume, grounding contact shadows, refined Staunton proportions,
 * and a charming, expressive Sir Hops mascot.
 */
object PieceArt {
    private enum class Kind {
        SHADOW,
        SHAPE,
        LINE,
        ACCENT_LINE,
        HIGHLIGHT_LINE,
        DOT,
        HIGHLIGHT_DOT,
        JEWEL_DOT,
    }

    private class Part(val path: Path, val kind: Kind)

    private fun shadow(d: String) = Part(PathParser().parsePathString(d).toPath(), Kind.SHADOW)
    private fun shape(d: String) = Part(PathParser().parsePathString(d).toPath(), Kind.SHAPE)
    private fun line(d: String) = Part(PathParser().parsePathString(d).toPath(), Kind.LINE)
    private fun accentLine(d: String) = Part(PathParser().parsePathString(d).toPath(), Kind.ACCENT_LINE)
    private fun highlightLine(d: String) = Part(PathParser().parsePathString(d).toPath(), Kind.HIGHLIGHT_LINE)
    private fun dot(d: String) = Part(PathParser().parsePathString(d).toPath(), Kind.DOT)
    private fun highlightDot(d: String) = Part(PathParser().parsePathString(d).toPath(), Kind.HIGHLIGHT_DOT)
    private fun jewelDot(d: String) = Part(PathParser().parsePathString(d).toPath(), Kind.JEWEL_DOT)

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): String =
        "M${cx - rx},$cy a$rx,$ry 0 1,0 ${2 * rx},0 a$rx,$ry 0 1,0 ${-2 * rx},0 Z"

    // Harmonious Staunton Base Shared Across Pieces
    private val BASE_SHADOW = ellipse(50f, 91.5f, 33f, 5f)
    private const val BASE_LOWER =
        "M17,91.5 C17,87.5 21,84.5 25,84.5 L75,84.5 C79,84.5 83,87.5 83,91.5 C83,92.5 80.5,93 76,93 L24,93 C19.5,93 17,92.5 17,91.5 Z"
    private const val BASE_UPPER =
        "M23,78.5 L77,78.5 C79.5,78.5 81,80.5 81,83 L81,84.5 L19,84.5 L19,83 C19,80.5 20.5,78.5 23,78.5 Z"
    private const val BASE_LINE = "M25,84.5 L75,84.5"
    private const val BASE_HIGHLIGHT = "M23,80.5 L46,80.5"

    private fun baseParts(): List<Part> = listOf(
        shadow(BASE_SHADOW),
        shape(BASE_LOWER),
        shape(BASE_UPPER),
        line(BASE_LINE),
        highlightLine(BASE_HIGHLIGHT),
    )

    private val parts: Map<Int, List<Part>> by lazy {
        mapOf(
            Piece.PAWN to baseParts() + listOf(
                // Body
                shape("M28,78.5 C32,67 38,62 41,55 L59,55 C62,62 68,67 72,78.5 Z"),
                // Collar Ring
                shape(ellipse(50f, 54f, 16.5f, 4.5f)),
                // Head Sphere
                shape(ellipse(50f, 36f, 14.5f, 14.5f)),
                // Spherical Gleam
                highlightDot(ellipse(45f, 30.5f, 4.5f, 3f)),
                // Stem Left Highlight
                highlightLine("M35,74 C36,66 41,61 43,56"),
            ),
            Piece.ROOK to baseParts() + listOf(
                // Tower Body
                shape("M28,78.5 L32,39 L68,39 L72,78.5 Z"),
                // Cornice Trim
                shape("M24,39 L76,39 C78,39 79,37 79,35 L79,34 L21,34 L21,35 C21,37 22,39 24,39 Z"),
                // Castle Battlements / Crenellations
                shape("M22,34 L22,19 L34,19 L34,26 L44,26 L44,19 L56,19 L56,26 L66,26 L66,19 L78,19 L78,34 Z"),
                // Arched Castle Window
                dot("M46,60 L46,51 C46,47.5 54,47.5 54,51 L54,60 Z"),
                // Highlights
                highlightLine("M34,74 L37,42"),
                highlightLine("M25,22 L31,22"),
            ),
            Piece.BISHOP to baseParts() + listOf(
                // Body
                shape("M29,78.5 C33,67 39,63 41,56 L59,56 C61,63 67,67 71,78.5 Z"),
                // Collar Ring
                shape(ellipse(50f, 55.5f, 16f, 4f)),
                // Mitre Hat
                shape("M50,16 C64,26 69,38 65,49 C63,54 58,56 50,56 C42,56 37,54 35,49 C31,38 36,26 50,16 Z"),
                // Diagonal Cleft
                line("M57,28 C53,33 46,38 43,44"),
                accentLine("M56,28 C52,33 46,38 44,43"),
                // Top Finial Ball
                shape(ellipse(50f, 13.5f, 4.5f, 4.5f)),
                highlightDot(ellipse(48.5f, 12f, 1.5f, 1.5f)),
                // Highlights
                highlightLine("M38,46 C35,39 38,29 46,21"),
            ),
            Piece.KNIGHT to baseParts() + listOf(
                // Sir Hops Horse Body Silhouette
                shape("M29,78.5 C27,67 31,59 40,54 C34,57 26,58 20,53 C15,48 16,42 22,38 C27,35 30,30 33,23 C35,19 37,13 40,8 L47,15 C50,12 53,10 56,8 L56,16 C68,19 76,30 75,48 C74,62 71,71 72,78.5 Z"),
                // Mane Wave Detailing
                line("M56,18 C65,24 69,34 68,48 C67,61 65,70 66,78.5"),
                accentLine("M52,28 C58,33 60,40 60,49"),
                // Jaw / Cheek Arc
                accentLine("M40,54 C36,49 37,42 42,39"),
                // Friendly Big Eye with Sparkle
                dot(ellipse(34f, 30f, 3.5f, 4f)),
                highlightDot(ellipse(33f, 28.5f, 1.4f, 1.4f)),
                // Cute Nostril and Smile
                dot(ellipse(20f, 46f, 1.6f, 1.2f)),
                accentLine("M21,50 C24,52 27,51 29,49"),
                // Highlights
                highlightLine("M43,15 C46,20 44,25 39,28"),
                highlightLine("M70,36 C71,48 69,63 69,75"),
            ),
            Piece.QUEEN to baseParts() + listOf(
                // Body
                shape("M28,78.5 C32,67 37,60 38,51 L62,51 C63,60 68,67 72,78.5 Z"),
                // Waist Trim
                shape(ellipse(50f, 51.5f, 18f, 4.5f)),
                // Regal Flared Tiara / Coronet
                shape("M29,51 L21,24 L35,37 L37,18 L45,34 L50,14 L55,34 L63,18 L65,37 L79,24 L71,51 Z"),
                // 5 Crown Pearls
                shape(ellipse(21f, 23f, 4.2f, 4.2f)),
                shape(ellipse(37f, 17f, 4.2f, 4.2f)),
                shape(ellipse(50f, 13f, 4.5f, 4.5f)),
                shape(ellipse(63f, 17f, 4.2f, 4.2f)),
                shape(ellipse(79f, 23f, 4.2f, 4.2f)),
                // Pearly Glints
                highlightDot(ellipse(19.5f, 21.5f, 1.3f, 1.3f)),
                highlightDot(ellipse(35.5f, 15.5f, 1.3f, 1.3f)),
                highlightDot(ellipse(48.5f, 11.5f, 1.4f, 1.4f)),
                // Bodice Fluting & Highlights
                accentLine("M50,56 L50,74"),
                highlightLine("M34,74 C36,65 41,59 42,52"),
            ),
            Piece.KING to baseParts() + listOf(
                // Body
                shape("M28,78.5 C32,67 37,60 38,51 L62,51 C63,60 68,67 72,78.5 Z"),
                // Waist Trim
                shape(ellipse(50f, 51.5f, 19f, 4.5f)),
                // Royal Imperial Cap / Arches
                shape("M27,51 C21,40 26,28 38,30 C42,25 58,25 62,30 C74,28 79,40 73,51 Z"),
                // Central Arch Rib
                line("M50,26 L50,51"),
                // Royal Cross Pattée
                shape("M47,7 h6 v6 h6 v5 h-6 v7 h-6 v-7 h-6 v-5 h6 Z"),
                // Jewel Finial Gem
                jewelDot(ellipse(50f, 15.5f, 2.2f, 2.2f)),
                // Highlights
                highlightLine("M34,74 C36,65 41,59 42,52"),
                highlightLine("M27,37 C29,32 34,31 38,32"),
            ),
        )
    }

    private val WhiteHighlight = Color(0xCCFFFFFF)
    private val WhiteGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFFDF6), Color(0xFFEBE1CA)),
        start = Offset(25f, 10f),
        end = Offset(75f, 90f)
    )
    private val WhiteLine = Color(0xFF2A2430)
    private val WhiteAccent = Color(0xFF4E4656)

    private val BlackHighlight = Color(0x73B9B2D7)
    private val BlackGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF514C60), Color(0xFF383446), Color(0xFF221F2D)),
        start = Offset(25f, 10f),
        end = Offset(75f, 90f)
    )
    private val BlackLine = Color(0xFF14121A)
    private val BlackAccent = Color(0xFFA69DC2)

    private val Gold = Color(0xFFFFC93C)
    private val ShadowColor = Color(0x38000000)

    private val outline = Stroke(width = 3.2f, join = StrokeJoin.Round, cap = StrokeCap.Round)
    private val detail = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val highlightStroke = Stroke(width = 2.2f, cap = StrokeCap.Round)
    private val jewelStroke = Stroke(width = 1.2f)

    /** Draws [piece] (signed code) inside the square at [topLeft] with side [size]. */
    fun DrawScope.drawPiece(piece: Int, topLeft: Offset, size: Float, alpha: Float = 1f) {
        if (piece == 0) return
        val list = parts[abs(piece)] ?: return
        val white = piece > 0
        val fillBrush = if (white) WhiteGradient else BlackGradient
        val line = if (white) WhiteLine else BlackLine
        val accent = if (white) WhiteAccent else BlackAccent
        val highlight = if (white) WhiteHighlight else BlackHighlight

        withTransform({
            translate(topLeft.x, topLeft.y)
            scale(size / 100f, size / 100f, pivot = Offset.Zero)
        }) {
            for (part in list) {
                when (part.kind) {
                    Kind.SHADOW -> drawPath(part.path, ShadowColor, alpha = alpha)
                    Kind.SHAPE -> {
                        drawPath(part.path, fillBrush, alpha = alpha)
                        drawPath(part.path, line, alpha = alpha, style = outline)
                    }
                    Kind.LINE -> drawPath(part.path, line, alpha = alpha, style = outline)
                    Kind.ACCENT_LINE -> drawPath(part.path, accent, alpha = alpha, style = detail)
                    Kind.HIGHLIGHT_LINE -> drawPath(part.path, highlight, alpha = alpha, style = highlightStroke)
                    Kind.DOT -> drawPath(part.path, line, alpha = alpha)
                    Kind.HIGHLIGHT_DOT -> drawPath(part.path, if (white) Color.White else highlight, alpha = alpha)
                    Kind.JEWEL_DOT -> {
                        drawPath(part.path, Gold, alpha = alpha)
                        drawPath(part.path, line, alpha = alpha, style = jewelStroke)
                    }
                }
            }
        }
    }
}

/** A single piece as a standalone picture, for menus and lesson cards. */
@Composable
fun PieceIcon(piece: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = minOf(size.width, size.height)
        with(PieceArt) { drawPiece(piece, Offset((size.width - s) / 2, (size.height - s) / 2), s) }
    }
}
