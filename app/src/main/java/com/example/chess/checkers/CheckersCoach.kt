package com.example.chess.checkers

import com.example.chess.engine.Square
import kotlin.math.abs

/** Kid-friendly explanations for checkers. */
object CheckersCoach {

    /** Explains why moving the checker on [from] to [to] isn't allowed. */
    fun explainIllegal(pos: CheckersPosition, from: Int, to: Int): String {
        val p = pos[from]
        val legal = pos.legalMoves()
        val mustJump = legal.firstOrNull()?.isCapture == true
        val mine = legal.filter { it.from == from }
        return when {
            mustJump && mine.isEmpty() -> "You have to jump when you can! Find the checker that can jump."
            mustJump -> "This checker has to jump! Hop over the enemy checker."
            from == to && mine.isEmpty() -> "That checker is stuck right now. Try another one!"
            !CheckersPosition.isDark(to) -> "Checkers only move on the dark squares."
            pos[to] != 0 -> "That square is already taken."
            else -> {
                val df = Square.file(to) - Square.file(from)
                val dr = Square.rank(to) - Square.rank(from)
                val forward = if (p > 0) dr else -dr
                when {
                    abs(df) != abs(dr) -> "Checkers move diagonally, from corner to corner."
                    !Checker.isKing(p) && forward < 0 -> "Regular checkers can only move forward. Kings can move backward!"
                    abs(df) == 2 -> "You can only jump over an enemy checker, and only onto an empty square."
                    else -> "Checkers move just one square at a time, unless they're jumping."
                }
            }
        }
    }
}
