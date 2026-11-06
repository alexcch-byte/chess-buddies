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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.chess.checkers.CheckersMove
import com.example.chess.checkers.CheckersPosition
import com.example.chess.engine.Square
import com.example.chess.ui.board.CheckerArt.drawChecker
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** A checkers move to animate hop by hop. [id] must change for every new animation. */
data class CheckersAnimation(val move: CheckersMove, val before: CheckersPosition, val id: Long)

data class CheckersDecor(
    val lastMove: CheckersMove? = null,
    val stars: Set<Int> = emptySet(),
    val hint: CheckersMove? = null,
    val danger: Set<Int> = emptySet(),
    val wrongSquare: Int? = null,
    /** Show where the checker on this square can go, even when the board isn't interactive. */
    val previewSquare: Int? = null,
)

private const val HOP_MS = 260

/** The square jumped over between two landing squares. */
private fun between(a: Int, b: Int): Int =
    Square.of((Square.file(a) + Square.file(b)) / 2, (Square.rank(a) + Square.rank(b)) / 2)

/**
 * A checkers board. Tap a checker, then tap where it should go. For a multi-jump the child can tap
 * each landing square in turn, or tap the last one straight away. Dragging works too.
 */
@Composable
fun CheckersBoard(
    position: CheckersPosition,
    onMove: (move: CheckersMove, animate: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    theme: BoardTheme = BoardTheme.MEADOW,
    flipped: Boolean = false,
    interactive: Boolean = true,
    showLegalMoves: Boolean = true,
    showCoordinates: Boolean = true,
    decor: CheckersDecor = CheckersDecor(),
    animation: CheckersAnimation? = null,
    onSelect: () -> Unit = {},
    onIllegal: (from: Int, to: Int) -> Unit = { _, _ -> },
) {
    val legal = remember(position) { position.legalMoves() }
    // The squares chosen so far: the selected checker, then any landing squares of a jump in progress.
    var path by remember { mutableStateOf<List<Int>>(emptyList()) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragPoint by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(position, interactive) {
        path = emptyList()
        dragFrom = null
    }

    val hops = animation?.let { it.move.path.size - 1 } ?: 1
    val t = rememberMoveProgress(animation?.id, HOP_MS * hops)
    val pulse by rememberPulse()
    val textMeasurer = rememberTextMeasurer()

    val currentPosition by rememberUpdatedState(position)
    val currentLegal by rememberUpdatedState(legal)
    val currentInteractive by rememberUpdatedState(interactive)
    val currentFlipped by rememberUpdatedState(flipped)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnIllegal by rememberUpdatedState(onIllegal)

    fun matching(p: List<Int>): List<CheckersMove> =
        currentLegal.filter { it.path.size > p.size && it.path.subList(0, p.size) == p }

    /** The child picked [target] for the checker on the current path. */
    fun chooseTarget(target: Int, dragged: Boolean) {
        val current = path
        if (current.isEmpty()) return
        val candidates = matching(current)
        val viaHop = candidates.filter { it.path[current.size] == target }
        val animate = !dragged && current.size == 1
        if (viaHop.isNotEmpty()) {
            val next = current + target
            val complete = viaHop.firstOrNull { it.path.size == next.size }
            if (complete != null) {
                path = emptyList()
                currentOnMove(complete, animate)
            } else {
                path = next // Part way through a multi-jump; wait for the next hop.
            }
            return
        }
        val toEnd = candidates.filter { it.to == target }.maxByOrNull { it.captured.size }
        if (toEnd != null) {
            path = emptyList()
            currentOnMove(toEnd, animate)
            return
        }
        currentOnIllegal(current.first(), target)
        if (current.size == 1) path = emptyList()
    }

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
                    val own = piece != 0 && (piece > 0) == pos.redToMove
                    val before = path
                    val midJump = before.size > 1
                    val canMove = currentLegal.any { it.from == sq }

                    if (own && !midJump) {
                        path = listOf(sq)
                        currentOnSelect()
                    }

                    var dragging = false
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        if (!change.pressed) break
                        if (own && !midJump && canMove && !dragging &&
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                        ) {
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
                            if (target != null && target != sq) chooseTarget(target, dragged = true)
                        }
                        own && !midJump -> when {
                            before == listOf(sq) -> path = emptyList()
                            !canMove -> currentOnIllegal(sq, sq)
                        }
                        before.isNotEmpty() && target == sq -> chooseTarget(sq, dragged = false)
                        !midJump -> path = emptyList()
                    }
                }
            }
    ) {
        val cell = size.width / 8f
        val radius = cell * 0.4f
        drawSquares(theme, cell, flipped)

        decor.lastMove?.let { m ->
            m.path.forEachIndexed { i, s -> highlight(s, cell, flipped, SelectColor.copy(alpha = if (i == 0) 0.38f else 0.5f)) }
        }
        decor.hint?.let { m ->
            m.path.forEach { s -> highlight(s, cell, flipped, HintColor.copy(alpha = 0.35f + 0.2f * pulse)) }
        }
        decor.wrongSquare?.let { highlight(it, cell, flipped, DangerColor.copy(alpha = 0.45f)) }
        val selectedPath = if (dragFrom != null) listOf(dragFrom!!) else path
        selectedPath.forEachIndexed { i, s -> highlight(s, cell, flipped, SelectColor.copy(alpha = if (i == selectedPath.lastIndex) 0.75f else 0.4f)) }

        if (showCoordinates) drawCoordinates(textMeasurer, theme, cell, flipped)
        for (s in decor.stars) drawStar(s, cell, flipped, 0.92f + 0.1f * pulse)
        for (s in decor.danger) drawDangerRing(s, cell, flipped, pulse)

        // Checkers already jumped during a multi-jump in progress fade out.
        val jumpedSoFar = (0 until path.size - 1).map { between(path[it], path[it + 1]) }.toSet()
        val animating = t < 1f && animation != null
        for (s in 0 until 64) {
            val p = position[s]
            if (p == 0 || s == dragFrom) continue
            if (animating && s == animation.move.to) continue
            if (path.size > 1 && s == path.first()) continue
            drawChecker(p, squareCenter(s, cell, flipped), radius, alpha = if (s in jumpedSoFar) 0.35f else 1f)
        }
        if (path.size > 1) drawChecker(position[path.first()], squareCenter(path.last(), cell, flipped), radius)

        // Where the selected checker can go next.
        val dotsPath = when {
            path.isNotEmpty() -> path
            decor.previewSquare != null -> listOf(decor.previewSquare)
            else -> emptyList()
        }
        if (showLegalMoves && dotsPath.isNotEmpty()) {
            val moves = if (path.isNotEmpty()) matching(dotsPath) else previewMoves(position, dotsPath.first())
            val next = moves.map { it.path[dotsPath.size] }.toSet()
            for (s in next) drawTargetMarker(s, cell, flipped, occupied = false)
        }

        decor.hint?.let { drawArrow(it.path, cell, flipped, HintColor.copy(alpha = 0.85f)) }

        if (animating) {
            val a = animation
            val n = a.move.path.size - 1
            val x = t * n
            val seg = floor(x).toInt().coerceAtMost(n - 1)
            val local = x - seg
            a.move.captured.forEachIndexed { i, c ->
                val alpha = if (x < i + 0.5f) 1f else (1f - (x - i - 0.5f) * 2f).coerceIn(0f, 1f)
                if (alpha > 0f) drawChecker(a.before[c], squareCenter(c, cell, flipped), radius, alpha)
            }
            val from = squareCenter(a.move.path[seg], cell, flipped)
            val to = squareCenter(a.move.path[seg + 1], cell, flipped)
            val lift = if (a.move.isCapture) sin(local * PI).toFloat() * cell * 0.3f else 0f
            drawChecker(a.before[a.move.from], from + (to - from) * local - Offset(0f, lift), radius * (1f + lift / cell * 0.4f))
        }

        dragFrom?.let { s -> drawChecker(position[s], dragPoint - Offset(0f, cell * 0.35f), radius * 1.2f) }
    }
}

/** Moves for a checker even when it isn't that side's turn (used to demonstrate how it moves). */
private fun previewMoves(position: CheckersPosition, sq: Int): List<CheckersMove> {
    val p = position[sq]
    if (p == 0) return emptyList()
    val pos = if ((p > 0) == position.redToMove) position else position.passTurn()
    return pos.legalMoves().filter { it.from == sq }
}
