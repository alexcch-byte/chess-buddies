package com.example.chess.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Counts move-tree leaves and compares them to well-known reference numbers. */
class PerftTest {

    private fun perft(pos: Position, depth: Int): Long {
        if (depth == 0) return 1
        val moves = pos.legalMoves()
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(pos.makeMove(it), depth - 1) }
    }

    private fun check(fen: String, vararg expected: Long) {
        val pos = Position.fromFen(fen)
        expected.forEachIndexed { i, count -> assertEquals("$fen depth ${i + 1}", count, perft(pos, i + 1)) }
    }

    @Test
    fun startPosition() = check(Position.START_FEN, 20, 400, 8902, 197281)

    @Test
    fun kiwipete() = check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2039, 97862)

    @Test
    fun endgameWithEnPassantPins() = check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812, 43238)

    @Test
    fun promotionsAndCastling() = check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9467)

    @Test
    fun position5() = check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1486, 62379)

    @Test
    fun fenRoundTrip() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(fen, Position.fromFen(fen).toFen())
    }

    @Test
    fun sanNotation() {
        val pos = Position.start()
        assertEquals("Nf3", Notation.san(pos, pos.findMove("g1f3")!!))
        assertEquals("e4", Notation.san(pos, pos.findMove("e2e4")!!))
        val mate = Position.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        assertEquals("Ra8#", Notation.san(mate, mate.findMove("a1a8")!!))
    }

    @Test
    fun gameOutcomes() {
        var game = Game()
        for (uci in listOf("f2f3", "e7e5", "g2g4", "d8h4")) game = game.play(game.position.findMove(uci)!!)
        assertEquals(Outcome(whiteWins = false, reason = EndReason.CHECKMATE), game.outcome)

        val stalemate = Game(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"))
        assertEquals(EndReason.STALEMATE, stalemate.outcome?.reason)

        val bare = Game(Position.fromFen("7k/8/6K1/8/8/8/8/8 w - - 0 1"))
        assertEquals(EndReason.INSUFFICIENT_MATERIAL, bare.outcome?.reason)

        var repeat = Game()
        repeat(2) {
            for (uci in listOf("g1f3", "g8f6", "f3g1", "f6g8")) repeat = repeat.play(repeat.position.findMove(uci)!!)
        }
        assertEquals(EndReason.REPETITION, repeat.outcome?.reason)
    }
}
