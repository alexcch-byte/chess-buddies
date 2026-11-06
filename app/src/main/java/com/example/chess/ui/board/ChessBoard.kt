package com.example.chess.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.chess.engine.Move
import com.example.chess.engine.Piece
import com.example.chess.engine.Position
import com.example.chess.ui.board.PieceArt.drawPiece
import kotlin.math.abs

/** A move to slide across the board. [id] must change for every new animation. */
data class MoveAnimation(val move: Move, val before: Position, val id: Long)

/** Extra things painted on the board. */
data class BoardDecor(
    val lastMove: Move? = null,
    val stars: Set<Int> = emptySet(),
    val hint: Move? = null,
    val danger: Set<Int> = emptySet(),
    val wrongSquare: Int? = null,
    /** Show where the piece on this square can go, even when the board isn't interactive. */
    val previewSquare: Int? = null,
)

/** Pieces sit a little inside their square so the board letters stay readable. */
private fun DrawScope.drawBoardPiece(piece: Int, squareTopLeft: Offset, cell: Float, alpha: Float = 1f) =
    drawPiece(piece, squareTopLeft + Offset(cell * 0.06f, cell * 0.03f), cell * 0.88f, alpha)

@Composable
fun ChessBoard(
    position: Position,
    onMove: (from: Int, to: Int, dragged: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    theme: BoardTheme = BoardTheme.MEADOW,
    flipped: Boolean = false,
    interactive: Boolean = true,
    showLegalMoves: Boolean = true,
    showCoordinates: Boolean = true,
    decor: BoardDecor = BoardDecor(),
    animation: MoveAnimation? = null,
    onSelect: () -> Unit = {},
    onIllegal: (from: Int, to: Int) -> Unit = { _, _ -> },
) {
    val legal = remember(position) { position.legalMoves() }
    var selected by remember { mutableStateOf<Int?>(null) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(position, interactive) {
        selected = null
        dragFrom = null
    }

    val t = rememberMoveProgress(animation?.id, 320)
    val pulse by rememberPulse()
    val textMeasurer = rememberTextMeasurer()

    val currentPosition by rememberUpdatedState(position)
    val currentLegal by rememberUpdatedState(legal)
    val currentInteractive by rememberUpdatedState(interactive)
    val currentFlipped by rememberUpdatedState(flipped)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnIllegal by rememberUpdatedState(onIllegal)

    Canvas(
        modifier
            .boardFrame()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!currentInteractive) return@awaitEachGesture
                    val boardSize = size.width.toFloat()
                    val flip = currentFlipped
                    val sq = squareAt(down.position, boardSize, flip) ?: return@awaitEachGesture
                    val pos = currentPosition
                    val piece = pos[sq]
                    val own = piece != 0 && (piece > 0) == pos.whiteToMove
                    val previous = selected

                    fun attempt(from: Int, to: Int, dragged: Boolean) {
                        selected = null
                        if (currentLegal.any { it.from == from && it.to == to }) currentOnMove(from, to, dragged)
                        else currentOnIllegal(from, to)
                    }

                    // Tapping your own rook with the king selected castles toward that rook.
                    val castleTarget = previous?.let { k ->
                        if (abs(pos[k]) != Piece.KING || abs(piece) != Piece.ROOK || !own) null
                        else currentLegal.firstOrNull { it.from == k && it.isCastle && (it.to > k) == (sq > k) }?.to
                    }

                    if (own && castleTarget == null) {
                        selected = sq
                        currentOnSelect()
                    }

                    var dragging = false
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        if (!change.pressed) break
                        if (own && !dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            dragFrom = sq
                        }
                        if (dragging) {
                            dragPoint = change.position
                            change.consume()
                        }
                    }

                    val target = squareAt(last, boardSize, flip)
                    when {
                        dragging -> {
                            dragFrom = null
                            if (target != null && target != sq) attempt(sq, target, dragged = true)
                        }
                        castleTarget != null -> attempt(previous, castleTarget, dragged = false)
                        own -> if (previous == sq) selected = null
                        previous != null && target == sq -> attempt(previous, sq, dragged = false)
                        else -> selected = null
                    }
                }
            }
    ) {
        val cell = size.width / 8f
        drawSquares(theme, cell, flipped)

        decor.lastMove?.let {
            highlight(it.from, cell, flipped, SelectColor.copy(alpha = 0.38f))
            highlight(it.to, cell, flipped, SelectColor.copy(alpha = 0.5f))
        }
        decor.hint?.let {
            highlight(it.from, cell, flipped, HintColor.copy(alpha = 0.35f + 0.2f * pulse))
            highlight(it.to, cell, flipped, HintColor.copy(alpha = 0.35f + 0.2f * pulse))
        }
        decor.wrongSquare?.let { highlight(it, cell, flipped, DangerColor.copy(alpha = 0.45f)) }
        val focus = selected ?: dragFrom
        focus?.let { highlight(it, cell, flipped, SelectColor.copy(alpha = 0.75f)) }

        if (position.inCheck() && t >= 1f) {
            val c = squareCenter(position.kingSquare(position.whiteToMove), cell, flipped)
            drawCircle(
                Brush.radialGradient(listOf(DangerColor.copy(alpha = 0.95f), DangerColor.copy(alpha = 0f)), center = c, radius = cell * 0.72f),
                radius = cell * 0.72f,
                center = c,
            )
        }

        if (showCoordinates) drawCoordinates(textMeasurer, theme, cell, flipped)

        for (s in decor.stars) drawStar(s, cell, flipped, 0.92f + 0.1f * pulse)

        // Danger rings go under the pieces so the piece stays easy to see.
        for (s in decor.danger) drawDangerRing(s, cell, flipped, pulse)

        // Pieces. While a move animates, the moving pieces are drawn separately on top.
        val moving = if (t < 1f && animation != null) movingPieces(animation) else emptyList()
        val hidden = moving.map { it.to }.toSet()
        for (s in 0 until 64) {
            val p = position[s]
            if (p == 0 || s in hidden || s == dragFrom) continue
            drawBoardPiece(p, squareTopLeft(s, cell, flipped), cell)
        }
        if (t < 1f && animation != null) {
            val captured = animation.before[animation.move.to]
            if (captured != 0) drawBoardPiece(captured, squareTopLeft(animation.move.to, cell, flipped), cell, alpha = 1f - t)
            if (animation.move.isEnPassant) {
                val victim = animation.move.to + if (animation.before[animation.move.from] > 0) -8 else 8
                drawBoardPiece(animation.before[victim], squareTopLeft(victim, cell, flipped), cell, alpha = 1f - t)
            }
        }

        // Where the selected piece can go.
        val dotsFrom = focus ?: decor.previewSquare
        if (showLegalMoves && dotsFrom != null) {
            val targets = if (focus != null) legal.filter { it.from == dotsFrom }.map { it.to }.toSet()
            else previewTargets(position, dotsFrom)
            for (s in targets) drawTargetMarker(s, cell, flipped, occupied = position[s] != 0)
        }

        decor.hint?.let { drawArrow(listOf(it.from, it.to), cell, flipped, HintColor.copy(alpha = 0.85f)) }

        for (slideItem in moving) {
            val a = squareTopLeft(slideItem.from, cell, flipped)
            val b = squareTopLeft(slideItem.to, cell, flipped)
            drawBoardPiece(slideItem.piece, a + (b - a) * t, cell)
        }

        dragFrom?.let { s ->
            val big = cell * 1.2f
            drawPiece(position[s], Offset(dragPoint.x - big / 2, dragPoint.y - big * 0.85f), big)
        }
    }
}

private data class Slide(val piece: Int, val from: Int, val to: Int)

/** Every piece that slides during [animation]; castling moves the rook too. */
private fun movingPieces(animation: MoveAnimation): List<Slide> {
    val m = animation.move
    val list = mutableListOf(Slide(animation.before[m.from], m.from, m.to))
    if (m.isCastle) {
        val (rookFrom, rookTo) = if (m.to > m.from) m.from + 3 to m.from + 1 else m.from - 4 to m.from - 1
        list += Slide(animation.before[rookFrom], rookFrom, rookTo)
    }
    return list
}

/** Targets for a piece even if it isn't that side's turn (used to demonstrate how a piece moves). */
private fun previewTargets(position: Position, sq: Int): Set<Int> {
    val p = position[sq]
    if (p == 0) return emptySet()
    val pos = if ((p > 0) == position.whiteToMove) position else position.passTurn()
    return pos.legalMoves().filter { it.from == sq }.map { it.to }.toSet()
}
