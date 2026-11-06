package com.example.chess.lessons

import com.example.chess.engine.Move
import com.example.chess.engine.Piece
import com.example.chess.engine.Position
import com.example.chess.engine.Square
import kotlin.math.abs

/** What the child has to do to finish a challenge. */
sealed interface Goal {
    /**
     * Multi-move goals are played by the child alone: the other pieces stand still and the turn
     * passes straight back. One-shot goals are puzzles that are solved, or not, by a single move.
     */
    val multiMove: Boolean get() = false

    data class CollectStars(val stars: Set<Int>) : Goal {
        override val multiMove get() = true
    }

    /** Capture every enemy piece except the King. */
    data object CaptureAll : Goal {
        override val multiMove get() = true
    }

    data object Promote : Goal {
        override val multiMove get() = true
    }

    data object Checkmate : Goal
    data object GiveCheck : Goal

    /** Any legal move escapes check; the lesson is in finding one. */
    data object EscapeCheck : Goal
    data class CaptureOn(val square: Int) : Goal

    /** [kingside] null means either side is fine. */
    data class Castle(val kingside: Boolean?) : Goal
    data object EnPassant : Goal

    /** Attack two valuable pieces at once with a piece that can't be captured. */
    data object Fork : Goal
}

data class Challenge(
    val fen: String,
    val instruction: String,
    val goal: Goal,
    /** An opponent move (UCI) played automatically when the challenge starts. */
    val setupMove: String? = null,
    /** For multi-move goals: landing where an enemy piece can capture you fails the challenge. */
    val safeOnly: Boolean = false,
    /** Shown when a one-shot puzzle gets a wrong answer (otherwise a goal-specific message). */
    val wrongText: String? = null,
)

/** What every lesson has, whichever game it teaches. */
interface LessonInfo {
    val id: String
    val title: String
    val tagline: String
    val intro: List<String>
    val challengeCount: Int
}

data class Lesson(
    override val id: String,
    override val title: String,
    override val tagline: String,
    /** Piece drawn on the lesson card (signed piece code). */
    val icon: Int,
    override val intro: List<String>,
    val challenges: List<Challenge>,
) : LessonInfo {
    override val challengeCount: Int get() = challenges.size
}

data class LessonUnit<out T : LessonInfo>(val title: String, val emoji: String, val lessons: List<T>)

/** Checks challenge goals and produces friendly feedback. */
object LessonRules {

    /** The position before any setup move. */
    fun initialPosition(challenge: Challenge): Position = Position.fromFen(challenge.fen)

    /** The opponent's opening move for this challenge, if it has one. */
    fun setupMove(challenge: Challenge): Move? =
        challenge.setupMove?.let { initialPosition(challenge).findMove(it) }

    /** The position the child starts solving from. */
    fun playablePosition(challenge: Challenge): Position {
        val initial = initialPosition(challenge)
        val setup = setupMove(challenge) ?: return initial
        return initial.makeMove(setup)
    }

    /** For one-shot goals: does this move solve the puzzle? */
    fun solves(goal: Goal, before: Position, move: Move, after: Position): Boolean = when (goal) {
        Goal.Checkmate -> after.isCheckmate()
        Goal.GiveCheck -> after.inCheck()
        Goal.EscapeCheck -> !after.inCheck(before.whiteToMove)
        is Goal.CaptureOn -> move.isCapture && move.to == goal.square
        is Goal.Castle -> move.isCastle && (goal.kingside == null || (move.to > move.from) == goal.kingside)
        Goal.EnPassant -> move.isEnPassant
        Goal.Fork -> isSafeFork(after, move.to)
        else -> false
    }

    /** For multi-move goals: is the job done after this move? [starsLeft] already excludes the landing square. */
    fun finished(goal: Goal, move: Move, after: Position, starsLeft: Set<Int>): Boolean = when (goal) {
        is Goal.CollectStars -> starsLeft.isEmpty()
        Goal.CaptureAll -> after.pieceCount(white = !moverIsWhite(after)) == 0
        Goal.Promote -> move.isPromotion
        else -> false
    }

    /** After a multi-move step, true if an enemy piece could now capture the piece that just moved. */
    fun landedInDanger(after: Position, move: Move): Boolean =
        after.isAttacked(move.to, byWhite = after.whiteToMove)

    private fun moverIsWhite(after: Position) = !after.whiteToMove

    fun isSafeFork(after: Position, sq: Int): Boolean {
        val mover = after[sq]
        if (mover == 0) return false
        return !after.isAttacked(sq, byWhite = mover < 0) && forkTargets(after, sq) >= 2
    }

    /** How many enemy pieces (not counting pawns) the piece on [sq] attacks. */
    private fun forkTargets(after: Position, sq: Int): Int {
        val white = after[sq] > 0
        return after.attackedBy(sq).count { t ->
            val p = after[t]
            p != 0 && (p > 0) != white && abs(p) != Piece.PAWN
        }
    }

    /** Explains why a one-shot answer was wrong. */
    fun wrongAnswerText(challenge: Challenge, move: Move, after: Position): String {
        val goal = challenge.goal
        if (goal == Goal.Checkmate && after.isStalemate()) {
            return "Oh no, that's stalemate! The King can't move but isn't in check, so it's a tie. Try again!"
        }
        if (goal == Goal.Checkmate && after.inCheck()) {
            return "That's check, but the King can still escape. Look for checkmate!"
        }
        if (goal == Goal.Fork && forkTargets(after, move.to) >= 2) {
            return "That attacks two pieces, but your ${Piece.name(abs(after[move.to]))} could be captured. Find a safe fork!"
        }
        challenge.wrongText?.let { return it }
        return when (goal) {
            Goal.Checkmate -> "Not quite! Find a move that attacks the King so it has no escape."
            Goal.GiveCheck -> "That doesn't attack the King. Try again!"
            is Goal.CaptureOn -> if (move.isCapture) "Good capture, but there's a better one!" else "Look for a piece you can capture!"
            is Goal.Castle -> "That's not castling. Move your King two squares toward a Rook!"
            Goal.EnPassant -> "Try the special en passant capture: move diagonally behind the black pawn!"
            Goal.Fork -> "Find a move that attacks two pieces at the same time!"
            else -> "Not quite! Try again."
        }
    }

    fun squares(vararg names: String): Set<Int> = names.map { Square.parse(it) }.toSet()
}
