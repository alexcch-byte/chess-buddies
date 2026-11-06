// Checkers lesson goals, rules, solver and catalog. Port of checkers/CheckersLessons.kt.
import { Square } from "../chess/position.js";
import { Checker, CheckersPosition } from "../checkers/checkers.js";

/** What the child has to do to finish a checkers challenge. The child always plays Red. */
export const CGoal = {
  /** Multi-move goals are played by the child alone; the black checkers stand still. */
  collectStars: (stars) => ({ kind: "stars", stars, multiMove: true }),
  captureAll: { kind: "captureAll", multiMove: true },
  crown: { kind: "crown", multiMove: true },
  /** Capture at least atLeast checkers in a single move. */
  capture: (atLeast) => ({ kind: "capture", atLeast, multiMove: false }),
  /** Move so that Black has no jump. */
  safeMove: { kind: "safeMove", multiMove: false },
  /** Leave Black with no checkers or no moves. */
  win: { kind: "win", multiMove: false },
  /** Two steps: offer a checker so Black is forced to jump, then jump back for atLeast checkers. */
  shot: (atLeast) => ({ kind: "shot", atLeast, multiMove: false }),
};

const squareSet = (names) => new Set(names.split(" ").filter((n) => n.trim()).map((n) => Square.parse(n)));

export const CheckersRules = {
  /** For one-shot goals: does this move solve the puzzle? */
  solves(goal, m, after) {
    switch (goal.kind) {
      case "capture": return m.captured.length >= goal.atLeast;
      case "safeMove": return after.captureMoves().length === 0;
      case "win": return after.legalMoves().length === 0;
      case "shot": return CheckersRules.shotWorks(after, goal.atLeast);
      default: return false;
    }
  },

  /** True if every reply Black can make lets Red jump at least atLeast checkers next move. */
  shotWorks(after, atLeast) {
    const replies = after.legalMoves();
    if (replies.length === 0 || !replies[0].isCapture) return false;
    return replies.every((r) => after.makeMove(r).captureMoves().some((c) => c.captured.length >= atLeast));
  },

  /** For multi-move goals: is the job done? starsLeft already excludes the landing square. */
  finished(goal, before, m, after, starsLeft) {
    switch (goal.kind) {
      case "stars": return starsLeft.size === 0;
      case "captureAll": return after.pieceCount(false) === 0;
      case "crown": return !Checker.isKing(before.get(m.from)) && Checker.isKing(after.get(m.to));
      default: return false;
    }
  },

  wrongAnswerText(challenge, m, after) {
    if (challenge.wrongText) return challenge.wrongText;
    const goal = challenge.goal;
    switch (goal.kind) {
      case "capture": return `You captured ${m.captured.length}, but you can capture ${goal.atLeast}! Try again.`;
      case "safeMove": return "Uh-oh, Black can jump that checker! Find a move where your checker stays safe.";
      case "win":
        return after.captureMoves().length > 0
          ? "Oops, now Black can jump you! Try again."
          : "Black can still move. Find a move that leaves Black stuck!";
      case "shot": return "That doesn't set up a double jump. Which checker could you give away?";
      default: return "Not quite! Try again.";
    }
  },
};

const solverKey = (pos, stars) => pos.key() + [...stars].sort((a, b) => a - b).join(",");

/** Finds the best way through a challenge, for star ratings and hints. */
export const CheckersSolver = {
  solveMultiMove(pos, goal, starsLeft, maxStates = 100_000) {
    const seen = new Set([solverKey(pos, starsLeft)]);
    const queue = [{ pos, stars: starsLeft, first: null, depth: 0 }];
    for (let head = 0; head < queue.length; head++) {
      const node = queue[head];
      for (const m of node.pos.legalMoves()) {
        const after = node.pos.makeMove(m);
        let stars = node.stars;
        if (stars.has(m.to)) { stars = new Set(stars); stars.delete(m.to); }
        const first = node.first ?? m;
        if (CheckersRules.finished(goal, node.pos, m, after, stars)) return { firstMove: first, length: node.depth + 1 };
        const next = after.passTurn();
        const key = solverKey(next, stars);
        if (!seen.has(key)) {
          seen.add(key);
          if (seen.size > maxStates) return null;
          queue.push({ pos: next, stars, first, depth: node.depth + 1 });
        }
      }
    }
    return null;
  },

  solveOneShot(pos, goal) {
    return pos.legalMoves().find((m) => CheckersRules.solves(goal, m, pos.makeMove(m))) ?? null;
  },
};

const stars = (names) => CGoal.collectStars(squareSet(names));
const setup = (red = "", black = "", redKings = "") => CheckersPosition.setup({ red, black, redKings });
const cch = (position, instruction, goal, wrongText = null) => ({ position, instruction, goal, wrongText });
const clesson = (id, title, tagline, icon, intro, challenges) => ({
  id, title, tagline, icon, intro, challenges, game: "checkers", challengeCount: challenges.length,
});

const moving = clesson("ck_moves", "How Checkers Move", "Diagonal steps on dark squares", Checker.RED, [
  "Welcome to checkers! Every checker lives on the dark squares.",
  "A checker moves one step diagonally forward, to an empty dark square.",
  "Red always moves first. You'll play the Red checkers!",
], [
  cch(setup("c3"), "Tap your checker, then tap the star. Checkers move diagonally forward!", stars("d4")),
  cch(setup("c1"), "Collect both stars. Only forward, only diagonal!", stars("e3 g5")),
  cch(setup("a3 g3"), "You have two checkers. Grab both stars!", stars("b4 h4")),
]);

const jumping = clesson("ck_jump", "Jumping", "Hop over to capture", Checker.BLACK, [
  "In checkers, you capture by JUMPING!",
  "If an enemy checker is diagonally in front of yours, and the square behind it is empty, hop over it and it's out of the game.",
], [
  cch(setup("c3", "d4"), "An enemy checker is right in front of yours. Jump over it!", CGoal.capture(1)),
  cch(setup("b2", "c3 f6"), "Capture both black checkers! You may need to move closer first.", CGoal.captureAll),
  cch(setup("c3", "b4 d4 f6 g7"), "You can jump two different ways. Pick the jump where Black can't jump you back!", CGoal.safeMove,
    "Oops! The black checker on f6 can jump you back. Find the safe jump!"),
]);

const doubleJumps = clesson("ck_double", "Double Jumps", "Keep on jumping!", Checker.RED, [
  "Here's the exciting part: after a jump, if your checker can jump AGAIN, it keeps going!",
  "That's called a double jump. Sometimes you can even do a triple jump!",
  "Tap each square you land on, one hop at a time.",
], [
  cch(setup("c1", "d2 f4"), "Jump, then jump again! Capture two checkers in one turn.", CGoal.capture(2)),
  cch(setup("b2", "c3 e5 e7"), "Can you find a TRIPLE jump?", CGoal.capture(3)),
  cch(setup("e3", "b6 d4 f4"), "Two different jumps! Pick the one that captures the most.", CGoal.capture(2),
    "You got one, but there was a double jump! Try again."),
  cch(setup("c3", "d4 d6"), "Zig-zag! Jump one way, then the other way.", CGoal.capture(2)),
]);

const kings = clesson("ck_king", "Crown a King", "Kings can go backward", Checker.RED_KING, [
  "When your checker reaches the very last row, it gets crowned. Now it's a KING!",
  "Kings are extra powerful: they can move and jump backward as well as forward.",
], [
  cch(setup("c5"), "March your checker to the last row to crown it!", CGoal.crown),
  cch(setup("", "", "d4"), "Kings can move backward! Collect the stars behind your king.", stars("c3 d2 e3")),
  cch(setup("g7"), "Get crowned, then use your new king to grab the star behind you!", stars("e5")),
  cch(setup("", "b4 d4", "e5"), "Kings can jump backward too. Find the double jump!", CGoal.capture(2)),
]);

const mustJump = clesson("ck_mustjump", "You Must Jump!", "The rule that sets traps", Checker.BLACK_KING, [
  "Here's a big checkers rule: if you CAN jump, you MUST jump!",
  "Sneaky players use this rule to set traps. They give away one checker so they can jump two!",
  "Now it's your turn to set the trap.",
], [
  cch(setup("a3 c3 g3", "f4"), "One of your checkers MUST jump. Which one?", CGoal.capture(1)),
  cch(setup("c1 d2 e3 g3", "e5 g5"), "Set a trap! Give Black one checker, so you can jump two of theirs.", CGoal.shot(2)),
  cch(setup("a1 b2 c3 e3", "c5 e5"), "Try that trick again! Which checker should you offer this time?", CGoal.shot(2)),
]);

const safety = clesson("ck_safe", "Stay Safe", "Don't get jumped", Checker.RED, [
  "Before you move, always ask: can my checker be jumped?",
  "Checkers on the edge of the board can never be jumped. And checkers that stand together protect each other!",
], [
  cch(setup("c3 e3", "c5"), "Make a move where Black can't jump you.", CGoal.safeMove),
  cch(setup("b4 d4", "b6 d6 f6"), "Only ONE move is safe here. Hint: the edge is a safe place!", CGoal.safeMove),
]);

const winning = clesson("ck_win", "Winning the Game", "Capture them all, or trap them", Checker.RED_KING, [
  "You win at checkers when your opponent has no checkers left...",
  "...or when they're stuck and can't make any move at all!",
], [
  cch(setup("c3", "d4 f6"), "Capture Black's last two checkers to win!", CGoal.win),
  cch(setup("f2 h2", "h4"), "Black has one checker left. Block it so it can't move!", CGoal.win),
  cch(setup("a3 c3", "a5"), "Trap the last black checker again!", CGoal.win),
]);

export const CHECKERS_UNITS = [
  { title: "Checkers Basics", emoji: "🔴", lessons: [moving, jumping, doubleJumps] },
  { title: "Kings and Tricks", emoji: "👑", lessons: [kings, mustJump] },
  { title: "Play to Win", emoji: "🏆", lessons: [safety, winning] },
];
export const CHECKERS_LESSONS = CHECKERS_UNITS.flatMap((u) => u.lessons);
