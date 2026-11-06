// Ports of LessonCatalogTest.kt and the lessonsAreSolvable test in CheckersTest.kt.
import test from "node:test";
import assert from "node:assert/strict";
import { uci } from "../docs/play/js/chess/position.js";
import { isDark } from "../docs/play/js/checkers/checkers.js";
import { Goal, LessonRules, LessonSolver } from "../docs/play/js/lessons/chess-lessons.js";
import { CheckersRules, CheckersSolver } from "../docs/play/js/lessons/checkers-lessons.js";
import { GAMES, lessonById, nextLessonAfter } from "../docs/play/js/lessons/catalog.js";

test("lesson ids are unique across both games", () => {
  const ids = [...GAMES.chess.lessons, ...GAMES.checkers.lessons].map((l) => l.id);
  assert.equal(ids.length, new Set(ids).size);
  assert.equal(GAMES.chess.lessons.length, 21);
  assert.equal(GAMES.checkers.lessons.length, 7);
  const challenges = [...GAMES.chess.lessons, ...GAMES.checkers.lessons].reduce((n, l) => n + l.challenges.length, 0);
  assert.equal(challenges, 96, "the README promises 28 lessons with 96 challenges");
});

test("catalog helpers", () => {
  assert.equal(lessonById("rook").title, "The Rook");
  assert.equal(lessonById("ck_jump").game, "checkers");
  assert.equal(nextLessonAfter("chess", "rook").id, "bishop");
  assert.equal(nextLessonAfter("chess", "forks").id, "center");
  assert.equal(nextLessonAfter("chess", "endgame_plans"), null);
});

test("every chess challenge is solvable and is a real puzzle", () => {
  for (const lesson of GAMES.chess.lessons) {
    lesson.challenges.forEach((challenge, i) => {
      const label = `${lesson.id} #${i + 1}`;
      if (challenge.setupMove) assert.ok(LessonRules.setupMove(challenge), `${label} setup move`);
      const pos = LessonRules.playablePosition(challenge);
      assert.ok(pos.whiteToMove, `${label}: White should be to move`);
      assert.ok(!pos.inCheck(false), `${label}: Black can't already be in check`);
      if (!challenge.setupMove) assert.ok(!pos.inCheck(true), `${label}: White starts in check`);
      assert.ok(pos.legalMoves().length > 0, `${label}: no legal moves`);

      const goal = challenge.goal;
      if (goal.multiMove) {
        const stars = goal.kind === "stars" ? goal.stars : new Set();
        const plan = LessonSolver.solveMultiMove(pos, goal, stars, challenge.safeOnly);
        assert.ok(plan, `${label} can't be solved`);
      } else {
        const legal = pos.legalMoves();
        const solutions = legal.filter((m) => LessonRules.solves(goal, pos, m, pos.makeMove(m)));
        assert.ok(solutions.length > 0, `${label} has no solution`);
        if (goal.kind !== "escapeCheck") {
          assert.ok(solutions.length < legal.length, `${label}: every move wins, so it isn't a puzzle`);
        }
      }
    });
  }
});

test("every checkers challenge is solvable and is a real puzzle", () => {
  for (const lesson of GAMES.checkers.lessons) {
    lesson.challenges.forEach((c, i) => {
      const label = `${lesson.id} #${i + 1}`;
      const pos = c.position;
      assert.ok(pos.redToMove, `${label}: Red should be to move`);
      assert.ok(pos.legalMoves().length > 0, `${label}: no legal moves`);
      if (c.goal.multiMove) {
        const stars = c.goal.kind === "stars" ? c.goal.stars : new Set();
        for (const s of stars) assert.ok(isDark(s), `${label} star on light square`);
        assert.ok(CheckersSolver.solveMultiMove(pos, c.goal, stars), `${label} can't be solved`);
      } else {
        const legal = pos.legalMoves();
        const solutions = legal.filter((m) => CheckersRules.solves(c.goal, m, pos.makeMove(m)));
        assert.ok(solutions.length > 0, `${label} has no solution`);
        if (legal.length !== 1) assert.ok(solutions.length < legal.length, `${label}: every move solves it`);
        if (c.goal.kind === "shot") {
          const after = pos.makeMove(solutions[0]);
          const reply = after.legalMoves();
          const follow = after.makeMove(reply[0]).captureMoves().filter((m) => m.captured.length >= c.goal.atLeast);
          assert.ok(follow.length > 0, `${label}: no follow-up jump`);
        }
      }
    });
  }
});
