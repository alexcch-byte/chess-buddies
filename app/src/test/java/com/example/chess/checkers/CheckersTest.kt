package com.example.chess.checkers

import com.example.chess.engine.Bot
import com.example.chess.engine.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CheckersTest {

    private fun perft(pos: CheckersPosition, depth: Int): Long {
        if (depth == 0) return 1
        val moves = pos.legalMoves()
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(pos.makeMove(it), depth - 1) }
    }

    @Test
    fun startPositionMoveCounts() {
        // Well-known move-tree sizes for English draughts from the opening position.
        val start = CheckersPosition.start()
        assertEquals(7, perft(start, 1))
        assertEquals(49, perft(start, 2))
        assertEquals(302, perft(start, 3))
        assertEquals(1469, perft(start, 4))
        assertEquals(7361, perft(start, 5))
        assertEquals(36768, perft(start, 6))
    }

    @Test
    fun jumpsAreCompulsory() {
        val pos = CheckersPosition.setup(red = "c3 g3", black = "d4")
        val moves = pos.legalMoves()
        assertEquals(listOf("c3xe5"), moves.map { it.notation() })
    }

    @Test
    fun multiJumpMustBeFinished() {
        val pos = CheckersPosition.setup(red = "c1", black = "d2 f4")
        assertEquals(listOf("c1xe3xg5"), pos.legalMoves().map { it.notation() })
    }

    @Test
    fun menOnlyJumpForwardButKingsJumpBackward() {
        val man = CheckersPosition.setup(red = "e5", black = "d4")
        assertTrue(man.captureMoves().isEmpty())
        val king = CheckersPosition.setup(redKings = "e5", black = "d4")
        assertEquals(listOf("e5xc3"), king.captureMoves().map { it.notation() })
    }

    @Test
    fun crowningEndsTheJump() {
        // After landing on d8 the man is crowned; it may not keep jumping backward as a king.
        val pos = CheckersPosition.setup(red = "f6", black = "e7 c7")
        val moves = pos.legalMoves()
        assertEquals(listOf("f6xd8"), moves.map { it.notation() })
        val after = pos.makeMove(moves.first())
        assertEquals(Checker.RED_KING, after[Square.parse("d8")])
    }

    @Test
    fun gameEndsWhenStuckOrEmpty() {
        val noPieces = CheckersGame(CheckersPosition.setup(red = "c3", redToMove = false))
        assertEquals(CheckersOutcome(redWins = true, reason = CheckersEndReason.NO_PIECES), noPieces.outcome)
        val stuck = CheckersGame(CheckersPosition.setup(red = "f2 g3", black = "h4", redToMove = false))
        assertEquals(CheckersOutcome(redWins = true, reason = CheckersEndReason.NO_MOVES), stuck.outcome)
        assertNull(CheckersGame().outcome)
    }

    @Test
    fun everyBotPlaysLegalMoves() {
        for (bot in Bot.entries) {
            var game = CheckersGame()
            val random = Random(3)
            repeat(8) {
                if (game.isOver) return@repeat
                val move = CheckersAi.chooseMove(game.position, bot, random)
                assertNotNull(move)
                assertTrue("${bot.name} played an illegal move", move in game.position.legalMoves())
                game = game.play(move!!)
            }
        }
    }

    @Test
    fun searchTakesTheDoubleJump() {
        val pos = CheckersPosition.setup(red = "e3", black = "b6 d4 f4")
        assertEquals("e3xc5xa7", CheckersAi.bestMove(pos, maxDepth = 6, timeLimitMs = 1000)?.notation())
    }

    @Test
    fun strongBotBeatsRandomPlayer() {
        var game = CheckersGame()
        val random = Random(11)
        var plies = 0
        while (!game.isOver && plies < 200) {
            val move = if (game.position.redToMove) CheckersAi.bestMove(game.position, maxDepth = 6, timeLimitMs = 300)!!
            else game.position.legalMoves().let { it[random.nextInt(it.size)] }
            game = game.play(move)
            plies++
        }
        assertEquals(true, game.outcome?.redWins)
    }

    @Test
    fun coachExplainsForcedJumps() {
        val pos = CheckersPosition.setup(red = "a3 c3 g3", black = "f4")
        val text = CheckersCoach.explainIllegal(pos, Square.parse("c3"), Square.parse("d4"))
        assertTrue(text, text.contains("have to jump"))
    }

    @Test
    fun lessonsAreSolvable() {
        for (lesson in CheckersLessonCatalog.lessons) {
            lesson.challenges.forEachIndexed { i, c ->
                val label = "${lesson.id} #${i + 1}"
                val pos = c.position
                assertTrue("$label: Red should be to move", pos.redToMove)
                assertTrue("$label: no legal moves", pos.legalMoves().isNotEmpty())
                if (c.goal.multiMove) {
                    val stars = (c.goal as? CheckersGoal.CollectStars)?.stars.orEmpty()
                    stars.forEach { assertTrue("$label star on light square", CheckersPosition.isDark(it)) }
                    val plan = CheckersSolver.solveMultiMove(pos, c.goal, stars)
                    assertNotNull("$label can't be solved", plan)
                    println("$label: best is ${plan!!.length} moves")
                } else {
                    val legal = pos.legalMoves()
                    val solutions = legal.filter { CheckersRules.solves(c.goal, it, pos.makeMove(it)) }
                    assertTrue("$label has no solution", solutions.isNotEmpty())
                    val forced = legal.size == 1
                    if (!forced) assertFalse("$label: every move solves it", solutions.size == legal.size)
                    println("$label: ${solutions.size}/${legal.size} solve it: ${solutions.map { it.notation() }}")
                    val goal = c.goal
                    if (goal is CheckersGoal.Shot) {
                        val after = pos.makeMove(solutions.first())
                        val reply = after.legalMoves()
                        println("$label: Black's replies ${reply.map { it.notation() }}")
                        val follow = after.makeMove(reply.first()).captureMoves().filter { it.captured.size >= goal.atLeast }
                        assertTrue("$label: no follow-up jump", follow.isNotEmpty())
                        println("$label: then ${follow.map { it.notation() }}")
                    }
                }
            }
        }
    }
}
