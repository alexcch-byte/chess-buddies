package com.example.chess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AiTest {

    @Test
    fun everyBotPlaysLegalMoves() {
        for (bot in Bot.entries) {
            var game = Game()
            val random = Random(42)
            repeat(6) {
                if (game.isOver) return@repeat
                val move = Ai.chooseMove(game.position, bot, random)
                assertNotNull(move)
                assertTrue("${bot.name} played an illegal move", move in game.position.legalMoves())
                game = game.play(move!!)
            }
        }
    }

    @Test
    fun strongBotsFindMateInOne() {
        val pos = Position.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        for (bot in listOf(Bot.FOX, Bot.OWL, Bot.DRAGON)) {
            val move = Ai.chooseMove(pos, bot, Random(1))
            assertEquals("${bot.name} missed mate", "a1a8", move?.uci())
        }
    }

    @Test
    fun dragonGrabsAHangingQueen() {
        val pos = Position.fromFen("4k3/8/8/3q4/8/8/8/3RK3 w - - 0 1")
        assertEquals("d1d5", Ai.chooseMove(pos, Bot.DRAGON)?.uci())
    }

    @Test
    fun searchFinishesQueenEndgame() {
        var game = Game(Position.fromFen("8/8/8/4k3/8/8/8/3QK3 w - - 0 1"))
        val random = Random(7)
        var plies = 0
        while (!game.isOver && plies < 120) {
            val move = if (game.position.whiteToMove) Ai.bestMove(game.position, maxDepth = 4, timeLimitMs = 400)!!.move
            else Ai.chooseMove(game.position, Bot.KITTEN, random)!!
            game = game.play(move)
            plies++
        }
        assertEquals(true, game.outcome?.whiteWins)
    }

    @Test
    fun coachSpotsHangingPieces() {
        // The black knight on c6 is unprotected and attacked by the bishop on b5.
        val pos = Position.fromFen("4k3/8/2n5/1B6/8/8/8/4K3 b - - 0 1")
        assertEquals(listOf(Square.parse("c6")), Coach.hangingPieces(pos, white = false))
        assertTrue(Coach.hangingPieces(pos, white = true).isEmpty())
    }

    @Test
    fun coachSeesMateThreat() {
        // White to move, but Black threatens Re1# on the back rank.
        val pos = Position.fromFen("4r1k1/5ppp/8/8/8/8/5PPP/6K1 w - - 0 1")
        assertEquals("e8e1", Coach.opponentMateThreat(pos)?.uci())
    }

    @Test
    fun explainsIllegalMoves() {
        val pos = Position.start()
        val text = Coach.explainIllegal(pos, Square.parse("g1"), Square.parse("g3"))
        assertTrue(text, text.contains("L shape"))
    }
}
