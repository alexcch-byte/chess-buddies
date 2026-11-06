// Port of CheckersTest.kt (lessons are tested in lessons.test.mjs). Run with: node --test web-tests/checkers.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { Square } from "../docs/play/js/chess/position.js";
import { BOTS } from "../docs/play/js/chess/ai.js";
import { Checker, CheckersPosition, CheckersGame, CheckersEndReason, cnotation } from "../docs/play/js/checkers/checkers.js";
import { chooseCheckersMove, bestCheckersMove } from "../docs/play/js/checkers/checkers-ai.js";
import { explainIllegalCheckers } from "../docs/play/js/checkers/checkers-coach.js";

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

test("start position move counts match English draughts", () => {
  const start = CheckersPosition.start();
  [7, 49, 302, 1469, 7361, 36768].forEach((count, i) => assert.equal(perft(start, i + 1), count, `depth ${i + 1}`));
});

test("jumps are compulsory", () => {
  const pos = CheckersPosition.setup({ red: "c3 g3", black: "d4" });
  assert.deepEqual(pos.legalMoves().map(cnotation), ["c3xe5"]);
});

test("a multi-jump must be finished", () => {
  const pos = CheckersPosition.setup({ red: "c1", black: "d2 f4" });
  assert.deepEqual(pos.legalMoves().map(cnotation), ["c1xe3xg5"]);
});

test("men only jump forward but kings jump backward", () => {
  assert.equal(CheckersPosition.setup({ red: "e5", black: "d4" }).captureMoves().length, 0);
  const king = CheckersPosition.setup({ redKings: "e5", black: "d4" });
  assert.deepEqual(king.captureMoves().map(cnotation), ["e5xc3"]);
});

test("crowning ends the jump", () => {
  const pos = CheckersPosition.setup({ red: "f6", black: "e7 c7" });
  const moves = pos.legalMoves();
  assert.deepEqual(moves.map(cnotation), ["f6xd8"]);
  assert.equal(pos.makeMove(moves[0]).get(Square.parse("d8")), Checker.RED_KING);
});

test("game ends when stuck or out of pieces", () => {
  const noPieces = new CheckersGame(CheckersPosition.setup({ red: "c3", redToMove: false }));
  assert.deepEqual(noPieces.outcome, { redWins: true, reason: CheckersEndReason.NO_PIECES });
  const stuck = new CheckersGame(CheckersPosition.setup({ red: "f2 g3", black: "h4", redToMove: false }));
  assert.deepEqual(stuck.outcome, { redWins: true, reason: CheckersEndReason.NO_MOVES });
  assert.equal(new CheckersGame().outcome, null);
});

test("every bot plays legal moves", () => {
  for (const bot of BOTS) {
    let game = new CheckersGame();
    const random = mulberry32(3);
    for (let i = 0; i < 8 && !game.isOver; i++) {
      const m = chooseCheckersMove(game.position, bot, random);
      assert.ok(m, `${bot.id} returned no move`);
      assert.ok(game.position.legalMoves().some((l) => cnotation(l) === cnotation(m)), `${bot.id} played an illegal move`);
      game = game.play(m);
    }
  }
});

test("search takes the double jump", () => {
  const pos = CheckersPosition.setup({ red: "e3", black: "b6 d4 f4" });
  assert.equal(cnotation(bestCheckersMove(pos, 6, 1000)), "e3xc5xa7");
});

test("a strong bot beats a random player", () => {
  for (const seed of [11, 12, 13]) {
    let game = new CheckersGame();
    const random = mulberry32(seed);
    for (let plies = 0; !game.isOver && plies < 200; plies++) {
      const legal = game.position.legalMoves();
      const m = game.position.redToMove
        ? bestCheckersMove(game.position, 6, 300)
        : legal[Math.floor(random() * legal.length)];
      game = game.play(m);
    }
    assert.equal(game.outcome?.redWins, true, `seed ${seed}`);
  }
});

test("coach explains forced jumps", () => {
  const pos = CheckersPosition.setup({ red: "a3 c3 g3", black: "f4" });
  const text = explainIllegalCheckers(pos, Square.parse("c3"), Square.parse("d4"));
  assert.ok(text.includes("have to jump"), text);
});
