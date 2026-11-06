package com.example.chess.lessons

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonCatalogTest {

    @Test
    fun lessonIdsAreUnique() {
        val ids = LessonCatalog.lessons.map { it.id }
        assertTrue(ids.size == ids.toSet().size)
    }

    @Test
    fun everyChallengeIsSolvable() {
        for (lesson in LessonCatalog.lessons) {
            lesson.challenges.forEachIndexed { i, challenge ->
                val label = "${lesson.id} #${i + 1}"
                if (challenge.setupMove != null) assertNotNull("$label setup move", LessonRules.setupMove(challenge))
                val pos = LessonRules.playablePosition(challenge)
                assertTrue("$label: White should be to move", pos.whiteToMove)
                assertFalse("$label: Black can't already be in check", pos.inCheck(white = false))
                if (challenge.setupMove == null) assertFalse("$label: White starts in check", pos.inCheck(white = true))
                assertTrue("$label: no legal moves", pos.legalMoves().isNotEmpty())

                val goal = challenge.goal
                if (goal.multiMove) {
                    val stars = (goal as? Goal.CollectStars)?.stars.orEmpty()
                    val plan = LessonSolver.solveMultiMove(pos, goal, stars, challenge.safeOnly)
                    assertNotNull("$label can't be solved", plan)
                    println("$label: best is ${plan!!.length} moves")
                } else {
                    val legal = pos.legalMoves()
                    val solutions = legal.filter { LessonRules.solves(goal, pos, it, pos.makeMove(it)) }
                    assertTrue("$label has no solution", solutions.isNotEmpty())
                    if (goal != Goal.EscapeCheck) {
                        assertTrue("$label: every move wins, so it isn't a puzzle", solutions.size < legal.size)
                    }
                    println("$label: ${solutions.size}/${legal.size} moves solve it: ${solutions.map { it.uci() }}")
                }
            }
        }
    }
}
