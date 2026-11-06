package com.example.chess.lessons

import com.example.chess.engine.Move
import com.example.chess.engine.Position

/** Finds the best way through a challenge, for star ratings and hints. */
object LessonSolver {
    data class Plan(val firstMove: Move, val length: Int)

    /**
     * Shortest solution of a multi-move goal from [pos], where only the side to move ever plays.
     * Returns null if it can't be done (or the search gets too big).
     */
    fun solveMultiMove(
        pos: Position,
        goal: Goal,
        starsLeft: Set<Int>,
        safeOnly: Boolean,
        maxStates: Int = 150_000,
    ): Plan? {
        class Node(val pos: Position, val stars: Set<Int>, val first: Move?, val depth: Int)

        val seen = HashSet<String>()
        val queue = ArrayDeque<Node>()
        queue.add(Node(pos, starsLeft, null, 0))
        seen.add(key(pos, starsLeft))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            for (m in node.pos.legalMoves()) {
                val after = node.pos.makeMove(m)
                if (safeOnly && LessonRules.landedInDanger(after, m)) continue
                val stars = if (goal is Goal.CollectStars) node.stars - m.to else node.stars
                val first = node.first ?: m
                if (LessonRules.finished(goal, m, after, stars)) return Plan(first, node.depth + 1)
                val next = after.passTurn()
                if (seen.add(key(next, stars))) {
                    if (seen.size > maxStates) return null
                    queue.add(Node(next, stars, first, node.depth + 1))
                }
            }
        }
        return null
    }

    /** A move that solves a one-shot puzzle, if there is one. */
    fun solveOneShot(pos: Position, goal: Goal): Move? =
        pos.legalMoves().firstOrNull { LessonRules.solves(goal, pos, it, pos.makeMove(it)) }

    private fun key(pos: Position, stars: Set<Int>): String =
        pos.toFen().substringBefore(' ') + "|" + stars.sorted().joinToString(",")
}
