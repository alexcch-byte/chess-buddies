// Ports of PerftTest.kt and AiTest.kt. Run with: node --test web-tests/
import test from "node:test";
import assert from "node:assert/strict";
import { Position, Square, START_FEN, uci } from "../docs/play/js/chess/position.js";
import { Game, EndReason, Notation } from "../docs/play/js/chess/game.js";
import { BOTS, botById, chooseMove, bestMove } from "../docs/play/js/chess/ai.js";
import { hangingPieces, opponentMateThreat, explainIllegal } from "../docs/play/js/chess/coach.js";

// Small seedable generator so bot tests are repeatable.
function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function perft(pos, depth) {
  if (depth === 0) return 1;
  const moves = pos.legalMoves();
  if (depth === 1) return moves.length;
  let n = 0;
  for (const m of moves) n += perft(pos.makeMove(m), depth - 1);
  return n;
}

function check(fen, ...expected) {
  const pos = Position.fromFen(fen);
  expected.forEach((count, i) => assert.equal(perft(pos, i + 1), count, `${fen} depth ${i + 1}`));
}

test("perft: start position", () => check(START_FEN, 20, 400, 8902, 197281));
test("perft: kiwipete", () => check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2039, 97862));
test("perft: en passant and pins", () => check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812, 43238));
test("perft: promotions and castling", () => check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9467));
test("perft: position 5", () => check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1486, 62379));

test("FEN round trip", () => {
  const fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
  assert.equal(Position.fromFen(fen).toFen(), fen);
});

test("SAN notation", () => {
  const pos = Position.start();
  assert.equal(Notation.san(pos, pos.findMove("g1f3")), "Nf3");
  assert.equal(Notation.san(pos, pos.findMove("e2e4")), "e4");
  const mate = Position.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1");
  assert.equal(Notation.san(mate, mate.findMove("a1a8")), "Ra8#");
});

test("game outcomes", () => {
  let game = new Game();
  for (const u of ["f2f3", "e7e5", "g2g4", "d8h4"]) game = game.play(game.position.findMove(u));
  assert.deepEqual(game.outcome, { whiteWins: false, reason: EndReason.CHECKMATE });

  assert.equal(new Game(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")).outcome?.reason, EndReason.STALEMATE);
  assert.equal(new Game(Position.fromFen("7k/8/6K1/8/8/8/8/8 w - - 0 1")).outcome?.reason, EndReason.INSUFFICIENT_MATERIAL);

  let repeat = new Game();
  for (let i = 0; i < 2; i++) {
    for (const u of ["g1f3", "g8f6", "f3g1", "f6g8"]) repeat = repeat.play(repeat.position.findMove(u));
  }
  assert.equal(repeat.outcome?.reason, EndReason.REPETITION);
});

test("every bot plays legal moves", () => {
  for (const bot of BOTS) {
    let game = new Game();
    const random = mulberry32(42);
    for (let i = 0; i < 6 && !game.isOver; i++) {
      const m = chooseMove(game.position, bot, random);
      assert.ok(m, `${bot.id} returned no move`);
      assert.ok(game.position.legalMoves().some((l) => uci(l) === uci(m)), `${bot.id} played an illegal move`);
      game = game.play(m);
    }
  }
});

test("strong bots find mate in one", () => {
  const pos = Position.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1");
  for (const id of ["fox", "owl", "dragon"]) {
    assert.equal(uci(chooseMove(pos, botById(id), mulberry32(1))), "a1a8", `${id} missed mate`);
  }
});

test("dragon grabs a hanging queen", () => {
  const pos = Position.fromFen("4k3/8/8/3q4/8/8/8/3RK3 w - - 0 1");
  assert.equal(uci(chooseMove(pos, botById("dragon"))), "d1d5");
});

// The Kotlin test uses one lucky seed (Random(7)). A depth-4 search sometimes shuffles into a threefold
// repetition instead of mating, in the Kotlin engine too (checked: identical moves and scores), so
// this asserts the real property across several seeds: white never loses and wins most games.
test("search wins most queen endgames and never loses", () => {
  let wins = 0, losses = 0;
  for (let seed = 1; seed <= 10; seed++) {
    let game = new Game(Position.fromFen("8/8/8/4k3/8/8/8/3QK3 w - - 0 1"));
    const random = mulberry32(seed);
    for (let plies = 0; !game.isOver && plies < 120; plies++) {
      const m = game.position.whiteToMove
        ? bestMove(game.position, 4, 400).move
        : chooseMove(game.position, botById("kitten"), random);
      game = game.play(m);
    }
    if (game.outcome?.whiteWins === true) wins++;
    if (game.outcome?.whiteWins === false) losses++;
  }
  assert.equal(losses, 0);
  assert.ok(wins >= 6, `white only won ${wins} of 10`);
});

test("coach spots hanging pieces", () => {
  const pos = Position.fromFen("4k3/8/2n5/1B6/8/8/8/4K3 b - - 0 1");
  assert.deepEqual(hangingPieces(pos, false), [Square.parse("c6")]);
  assert.deepEqual(hangingPieces(pos, true), []);
});

test("coach sees a mate threat", () => {
  const pos = Position.fromFen("4r1k1/5ppp/8/8/8/8/5PPP/6K1 w - - 0 1");
  assert.equal(uci(opponentMateThreat(pos)), "e8e1");
});

test("coach explains illegal moves", () => {
  const text = explainIllegal(Position.start(), Square.parse("g1"), Square.parse("g3"));
  assert.ok(text.includes("L shape"), text);
});
