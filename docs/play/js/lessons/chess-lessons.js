// Chess lesson goals, rules, solver and catalog. Ports of lessons/Lesson.kt, LessonSolver.kt and LessonCatalog.kt.
import {
  Position, Square, Piece, PAWN, ROOK, BISHOP, QUEEN, KING, KNIGHT, isCapture, isCastle, isEnPassant, uci,
} from "../chess/position.js";

/** What the child has to do to finish a challenge. */
export const Goal = {
  /** Multi-move goals are played by the child alone: the other pieces stand still and the turn passes straight back. */
  collectStars: (stars) => ({ kind: "stars", stars, multiMove: true }),
  /** Capture every enemy piece except the King. */
  captureAll: { kind: "captureAll", multiMove: true },
  promote: { kind: "promote", multiMove: true },
  checkmate: { kind: "checkmate", multiMove: false },
  giveCheck: { kind: "giveCheck", multiMove: false },
  /** Any legal move escapes check; the lesson is in finding one. */
  escapeCheck: { kind: "escapeCheck", multiMove: false },
  captureOn: (square) => ({ kind: "captureOn", square, multiMove: false }),
  /** kingside null means either side is fine. */
  castle: (kingside) => ({ kind: "castle", kingside, multiMove: false }),
  enPassant: { kind: "enPassant", multiMove: false },
  /** Attack two valuable pieces at once with a piece that can't be captured. */
  fork: { kind: "fork", multiMove: false },
  /** Strategy puzzles: pick the sensible move. Solved by any of the listed moves (UCI, e.g. "e2e4"). */
  playMove: (ucis) => ({ kind: "playMove", moves: new Set(ucis), multiMove: false }),
};

export const squares = (...names) => new Set(names.map((n) => Square.parse(n)));
const sq = (name) => Square.parse(name);

/** Checks challenge goals and produces friendly feedback. */
export const LessonRules = {
  /** The position before any setup move. */
  initialPosition: (challenge) => Position.fromFen(challenge.fen),

  /** The opponent's opening move for this challenge, if it has one. */
  setupMove(challenge) {
    return challenge.setupMove ? LessonRules.initialPosition(challenge).findMove(challenge.setupMove) : null;
  },

  /** The position the child starts solving from. */
  playablePosition(challenge) {
    const initial = LessonRules.initialPosition(challenge);
    const setup = LessonRules.setupMove(challenge);
    return setup ? initial.makeMove(setup) : initial;
  },

  /** For one-shot goals: does this move solve the puzzle? */
  solves(goal, before, m, after) {
    switch (goal.kind) {
      case "checkmate": return after.isCheckmate();
      case "giveCheck": return after.inCheck();
      case "escapeCheck": return !after.inCheck(before.whiteToMove);
      case "captureOn": return isCapture(m) && m.to === goal.square;
      case "castle": return isCastle(m) && (goal.kingside === null || (m.to > m.from) === goal.kingside);
      case "enPassant": return isEnPassant(m);
      case "fork": return LessonRules.isSafeFork(after, m.to);
      case "playMove": return goal.moves.has(uci(m));
      default: return false;
    }
  },

  /** For multi-move goals: is the job done after this move? starsLeft already excludes the landing square. */
  finished(goal, m, after, starsLeft) {
    switch (goal.kind) {
      case "stars": return starsLeft.size === 0;
      // The side to move now is the opponent of whoever just moved.
      case "captureAll": return after.pieceCount(after.whiteToMove) === 0;
      case "promote": return m.promotion !== 0;
      default: return false;
    }
  },

  /** After a multi-move step, true if an enemy piece could now capture the piece that just moved. */
  landedInDanger: (after, m) => after.isAttacked(m.to, after.whiteToMove),

  isSafeFork(after, square) {
    const mover = after.get(square);
    if (mover === 0) return false;
    return !after.isAttacked(square, mover < 0) && forkTargets(after, square) >= 2;
  },

  /** Explains why a one-shot answer was wrong. */
  wrongAnswerText(challenge, m, after) {
    const goal = challenge.goal;
    if (goal.kind === "checkmate" && after.isStalemate()) {
      return "Oh no, that's stalemate! The King can't move but isn't in check, so it's a tie. Try again!";
    }
    if (goal.kind === "checkmate" && after.inCheck()) {
      return "That's check, but the King can still escape. Look for checkmate!";
    }
    if (goal.kind === "fork" && forkTargets(after, m.to) >= 2) {
      return `That attacks two pieces, but your ${Piece.name(Math.abs(after.get(m.to)))} could be captured. Find a safe fork!`;
    }
    if (challenge.wrongText) return challenge.wrongText;
    switch (goal.kind) {
      case "checkmate": return "Not quite! Find a move that attacks the King so it has no escape.";
      case "giveCheck": return "That doesn't attack the King. Try again!";
      case "captureOn": return isCapture(m) ? "Good capture, but there's a better one!" : "Look for a piece you can capture!";
      case "castle": return "That's not castling. Move your King two squares toward a Rook!";
      case "enPassant": return "Try the special en passant capture: move diagonally behind the black pawn!";
      case "fork": return "Find a move that attacks two pieces at the same time!";
      default: return "Not quite! Try again.";
    }
  },
};

/** How many enemy pieces (not counting pawns) the piece on sq attacks. */
function forkTargets(after, square) {
  const white = after.get(square) > 0;
  return after.attackedBy(square).filter((t) => {
    const p = after.get(t);
    return p !== 0 && (p > 0) !== white && Math.abs(p) !== PAWN;
  }).length;
}

const solverKey = (pos, stars) => pos.toFen().split(" ")[0] + "|" + [...stars].sort((a, b) => a - b).join(",");

/** Finds the best way through a challenge, for star ratings and hints. */
export const LessonSolver = {
  /**
   * Shortest solution of a multi-move goal from pos, where only the side to move ever plays.
   * Returns { firstMove, length } or null if it can't be done (or the search gets too big).
   */
  solveMultiMove(pos, goal, starsLeft, safeOnly, maxStates = 150_000) {
    const seen = new Set([solverKey(pos, starsLeft)]);
    const queue = [{ pos, stars: starsLeft, first: null, depth: 0 }];
    for (let head = 0; head < queue.length; head++) {
      const node = queue[head];
      for (const m of node.pos.legalMoves()) {
        const after = node.pos.makeMove(m);
        if (safeOnly && LessonRules.landedInDanger(after, m)) continue;
        let stars = node.stars;
        if (goal.kind === "stars" && stars.has(m.to)) { stars = new Set(stars); stars.delete(m.to); }
        const first = node.first ?? m;
        if (LessonRules.finished(goal, m, after, stars)) return { firstMove: first, length: node.depth + 1 };
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

  /** A move that solves a one-shot puzzle, if there is one. */
  solveOneShot(pos, goal) {
    return pos.legalMoves().find((m) => LessonRules.solves(goal, pos, m, pos.makeMove(m))) ?? null;
  },
};

const ch = (fen, instruction, goal, extra = {}) => ({
  fen, instruction, goal, setupMove: null, safeOnly: false, wrongText: null, ...extra,
});

const lesson = (id, title, tagline, icon, intro, challenges) => ({
  id, title, tagline, icon, intro, challenges, game: "chess", challengeCount: challenges.length,
});

const rook = lesson("rook", "The Rook", "Straight lines, like a train on its tracks", ROOK, [
  "This is the Rook! It looks like a castle tower.",
  "The Rook moves in straight lines: up, down, left or right. It can go as far as it likes, but it can't jump over other pieces.",
], [
  ch("8/8/8/8/8/8/8/R7 w - - 0 1", "Tap the Rook, then tap the star to move there!", Goal.collectStars(squares("a6"))),
  ch("8/8/8/8/8/8/8/R7 w - - 0 1", "Collect both stars!", Goal.collectStars(squares("a7", "f7"))),
  ch("8/8/8/8/3R4/8/8/8 w - - 0 1", "Collect all the stars in as few moves as you can.", Goal.collectStars(squares("d8", "h8", "h1", "b1"))),
  ch("8/8/p3p3/8/8/8/4p3/R7 w - - 0 1", "Rooks can capture! Land on a black pawn to take it off the board. Capture them all!", Goal.captureAll),
]);

const bishop = lesson("bishop", "The Bishop", "Slides along the diagonals", BISHOP, [
  "Meet the Bishop! It wears a tall pointy hat.",
  "The Bishop moves diagonally, like sliding down a slanted slide. It can go as far as it wants until something is in the way.",
  "A Bishop that starts on a dark square stays on dark squares forever!",
], [
  ch("8/8/8/8/8/8/8/2B5 w - - 0 1", "Slide the Bishop to the star.", Goal.collectStars(squares("h6"))),
  ch("8/8/8/8/8/8/8/2B5 w - - 0 1", "Collect all the stars!", Goal.collectStars(squares("a3", "c5", "f8"))),
  ch("8/8/8/8/8/8/8/5B2 w - - 0 1", "This Bishop lives on light squares. Grab the stars!", Goal.collectStars(squares("b5", "e8", "h5"))),
  ch("8/8/1p6/6p1/8/4p3/8/2B5 w - - 0 1", "Capture all the pawns with your Bishop!", Goal.captureAll),
]);

const queen = lesson("queen", "The Queen", "The most powerful piece", QUEEN, [
  "The Queen is the most powerful piece on the board!",
  "She moves like a Rook AND like a Bishop: straight lines and diagonals, as far as she wants.",
], [
  ch("8/8/8/8/8/8/8/3Q4 w - - 0 1", "Collect the stars with your Queen.", Goal.collectStars(squares("d7", "h3"))),
  ch("8/8/8/8/8/8/8/3Q4 w - - 0 1", "So many stars! Collect them all.", Goal.collectStars(squares("a4", "e8", "h5", "h1"))),
  ch("8/1p3p2/8/8/2p5/5p2/8/3Q4 w - - 0 1", "Capture every pawn!", Goal.captureAll),
]);

const king = lesson("king", "The King", "The most important piece", KING, [
  "This is the King, the most important piece of all!",
  "The King moves just one square at a time, in any direction.",
  "If your King gets trapped, you lose the game. So always keep your King safe!",
], [
  ch("8/8/8/8/8/8/8/4K3 w - - 0 1", "One step at a time! Collect the stars.", Goal.collectStars(squares("d2", "e3", "f4"))),
  ch("8/8/8/8/4K3/8/8/8 w - - 0 1", "Walk the King to every star.", Goal.collectStars(squares("c6", "e6", "g6"))),
  ch("8/8/8/8/2p2p2/8/8/4K3 w - - 0 1", "The King can capture too! Capture both pawns, but the King can never step onto a square a pawn attacks.", Goal.captureAll),
]);

const knight = lesson("knight", "The Knight", "Jumps in an L shape", KNIGHT, [
  "The Knight is a horse, and it's the trickiest piece!",
  "It moves in an L shape: two squares in one direction, then one square to the side.",
  "The Knight is the only piece that can jump over other pieces!",
], [
  ch("8/8/8/8/8/8/8/1N6 w - - 0 1", "Hop the Knight to the star.", Goal.collectStars(squares("c3"))),
  ch("8/8/8/8/8/8/8/6N1 w - - 0 1", "Collect the stars. Remember the L shape!", Goal.collectStars(squares("f3", "e5", "g6"))),
  ch("8/8/8/8/8/8/PPPP4/1N6 w - - 0 1", "Knights can jump over other pieces! Collect the stars.", Goal.collectStars(squares("a3", "c3"))),
  ch("8/4p3/2p3p1/8/3N1p2/8/8/8 w - - 0 1", "Capture all the pawns with your Knight!", Goal.captureAll),
]);

const pawn = lesson("pawn", "The Pawn", "Small but brave", PAWN, [
  "Pawns are small, but they're brave!",
  "A pawn moves straight forward, one square at a time. On its very first move, it may jump forward two squares.",
  "Pawns never move backward. And here's the funny part: they capture diagonally, one square forward!",
], [
  ch("8/8/8/8/8/8/4P3/8 w - - 0 1", "On its first move a pawn can jump two squares. Reach the star in one move!", Goal.collectStars(squares("e4"))),
  ch("8/8/8/8/8/8/4P3/8 w - - 0 1", "March forward to the star.", Goal.collectStars(squares("e6"))),
  ch("8/8/8/8/4p3/3p4/2P5/8 w - - 0 1", "Pawns capture diagonally! Capture both black pawns.", Goal.captureAll),
  ch("8/8/8/3pp3/4P3/8/8/8 w - - 0 1", "Pawns can't capture straight ahead, so this pawn is stuck! Find another way to the star.", Goal.collectStars(squares("d7"))),
]);

const capture = lesson("capture", "Capturing", "Which pieces are worth the most?", -KNIGHT, [
  "When a piece lands on an enemy piece, it captures it and takes it off the board!",
  "Pieces are worth different points: a Pawn is worth 1, a Knight or Bishop 3, a Rook 5, and the Queen 9.",
  "Capture pieces that nobody is protecting, and watch out that your pieces don't get captured!",
], [
  ch("8/4p3/3p4/8/8/8/8/3R4 w - - 0 1", "Capture both pawns, but never land where a black pawn could capture your Rook!", Goal.captureAll, { safeOnly: true }),
  ch("8/7n/2p5/1n6/8/3B4/8/8 w - - 0 1", "Your Bishop can capture a Knight. Which Knight is NOT protected?", Goal.captureOn(sq("h7")),
    { wrongText: "Oops! That Knight was protected by a pawn. Find the piece nobody is guarding!" }),
  ch("8/8/5r2/2b5/4N3/8/3p4/8 w - - 0 1", "Your Knight can capture three different pieces. Take the one worth the most points!", Goal.captureOn(sq("f6")),
    { wrongText: "That's a capture, but there's a piece worth more. Rooks are worth 5 points!" }),
  ch("8/8/8/3q1r2/4P3/8/8/8 w - - 0 1", "Your pawn can capture! Which piece should it take?", Goal.captureOn(sq("d5")),
    { wrongText: "A Rook is nice, but the Queen is worth 9 points!" }),
  ch("8/2p3p1/1p5p/8/8/8/8/Q7 w - - 0 1", "Capture all the pawns with your Queen. Some pawns protect each other, so pick your order carefully!", Goal.captureAll, { safeOnly: true }),
]);

const promotion = lesson("promotion", "Pawn Promotion", "A pawn's secret power", QUEEN, [
  "Here's a pawn's secret power!",
  "When a pawn reaches the very last row, it gets promoted. It can turn into a Queen, Rook, Bishop or Knight!",
  "Most of the time you should pick a Queen, because she's the strongest.",
], [
  ch("8/4P3/8/8/8/8/8/8 w - - 0 1", "Move your pawn to the last row and choose a new piece!", Goal.promote),
  ch("8/8/8/1P6/8/8/8/8 w - - 0 1", "March all the way to the end and promote!", Goal.promote),
  ch("2rnr3/3P4/8/8/8/8/8/8 w - - 0 1", "Your pawn is blocked! Can you capture your way to the last row?", Goal.promote),
  ch("8/6P1/8/8/8/8/8/8 w - - 0 1", "Promote your pawn, then use your new piece to grab the star!", Goal.collectStars(squares("a1"))),
]);

const check = lesson("check", "Check!", "Attack the King", -KING, [
  "When a piece attacks the King, we say CHECK!",
  "A King in check must escape right away. There are three ways: move the King, block the attack, or capture the attacker.",
], [
  ch("4k3/8/8/8/8/8/8/R3K3 w - - 0 1", "Put the black King in check with your Rook!", Goal.giveCheck),
  ch("4k3/8/8/8/4N3/8/8/4K3 w - - 0 1", "Give check with your Knight!", Goal.giveCheck),
  ch("4k3/8/8/8/8/8/8/4KB2 w - - 0 1", "Give check with your Bishop!", Goal.giveCheck),
  ch("4k3/r7/8/8/8/8/8/4K3 b - - 0 1", "The Rook says CHECK! Move your King to a safe square.", Goal.escapeCheck, { setupMove: "a7a1" }),
  ch("7k/r7/8/8/8/3B4/5PPP/6K1 b - - 0 1", "Check again! This time your King can't move. Block the attack with your Bishop!", Goal.escapeCheck, { setupMove: "a7a1" }),
  ch("4k3/8/8/6n1/8/8/6P1/4K3 b - - 0 1", "The Knight gives check! Capture the piece that is attacking your King.", Goal.captureOn(sq("f3")),
    { setupMove: "g5f3", wrongText: "That's safe, but you can do better: capture the Knight!" }),
]);

const checkmate = lesson("checkmate", "Checkmate", "How you win the game", KING, [
  "Checkmate is how you win the game!",
  "It's checkmate when the King is in check and there's NO way out: it can't move, nothing can block, and nobody can capture the attacker.",
], [
  ch("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", "The black King is trapped behind its own pawns. Checkmate it with your Rook!", Goal.checkmate),
  ch("k7/8/1K6/8/8/8/7Q/8 w - - 0 1", "Your King and Queen work together. Find checkmate!", Goal.checkmate),
  ch("4k3/R7/8/8/8/8/8/1R2K3 w - - 0 1", "Two Rooks make a ladder. Checkmate in one move!", Goal.checkmate),
  ch("4k3/8/4K3/8/7Q/8/8/8 w - - 0 1", "Find the checkmate with your Queen!", Goal.checkmate),
  ch("6rk/6pp/8/6N1/8/8/8/7K w - - 0 1", "The black King has no room at all. Can your Knight checkmate it?", Goal.checkmate),
  ch("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4", "This famous trap is called Scholar's Mate. Find the checkmate, and watch out for it in your own games!", Goal.checkmate),
]);

const castling = lesson("castling", "Castling", "Tuck your King in safely", ROOK, [
  "Castling is a special move that tucks your King away safely and brings a Rook into the game.",
  "To castle, move your King two squares toward a Rook. The Rook then hops over to the King's other side.",
  "You can only castle if the King and that Rook haven't moved yet, nothing is in between, and the King isn't in check or crossing an attacked square.",
], [
  ch("4k3/8/8/8/8/8/PPPPPPPP/R3K2R w KQ - 0 1", "Castle on the King's side: move your King two squares to the right!", Goal.castle(true)),
  ch("4k3/8/8/8/8/8/PPPPPPPP/R3K2R w KQ - 0 1", "Now castle on the Queen's side: two squares to the left!", Goal.castle(false)),
  ch("4k3/8/b7/8/8/8/8/R3K2R w KQ - 0 1", "The black Bishop is watching f1, so your King can't pass through it. Find the castling move that IS allowed!", Goal.castle(null)),
]);

const enPassant = lesson("en_passant", "En Passant", "A sneaky pawn capture", PAWN, [
  "Here's a sneaky pawn trick called en passant. Say it like \"on pah-SAHN\". It's French for \"in passing\"!",
  "If an enemy pawn jumps two squares and lands right beside your pawn, your pawn can capture it as if it had moved just one square.",
  "You have to do it right away, on the very next move, or the chance is gone!",
], [
  ch("8/3p4/8/4P3/8/8/8/8 b - - 0 1", "The black pawn just zoomed past your pawn! Capture it en passant.", Goal.enPassant, { setupMove: "d7d5" }),
  ch("8/5p2/8/4P3/8/8/8/8 b - - 0 1", "It happened again! Capture en passant.", Goal.enPassant, { setupMove: "f7f5" }),
]);

const stalemate = lesson("stalemate", "Stalemate", "When nobody wins", -QUEEN, [
  "Not every game has a winner. Sometimes it's a tie, called a draw.",
  "Stalemate is a draw that happens when a player has NO legal moves, but their King is NOT in check.",
  "When you're winning, be careful! Leave the enemy King a square to move to until you can checkmate.",
], [
  ch("7k/8/5K2/8/8/8/8/6Q1 w - - 0 1", "One move here is checkmate, and one is stalemate. Find the checkmate!", Goal.checkmate),
  ch("7k/4Q3/6K1/8/8/8/8/8 w - - 0 1", "Checkmate in one move, and don't trap the King in a stalemate!", Goal.checkmate),
]);

const forks = lesson("forks", "Forks", "Attack two pieces at once", KNIGHT, [
  "A fork is when one piece attacks two enemy pieces at the same time.",
  "Your opponent can only save one of them, so you get to capture the other!",
  "Knights are great at forks because of their funny L-shaped jump.",
], [
  ch("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1", "Find a Knight move that attacks the King AND the Rook!", Goal.fork),
  ch("4k3/8/2r1r3/8/3P4/8/8/6K1 w - - 0 1", "Even a little pawn can fork! Attack both Rooks.", Goal.fork),
  ch("4k3/3q4/8/8/4N3/8/8/4K3 w - - 0 1", "Fork the King and the Queen with your Knight. That's called a Royal Fork!", Goal.fork),
  ch("4k3/8/8/7r/8/8/1Q6/4K3 w - - 0 1", "Use your Queen to attack the King and the Rook, without letting the Rook capture your Queen.", Goal.fork),
]);

// ---------- Strategy lessons ----------
// These are "find the best move" puzzles. The accepted moves were worked out with the engine; the lesson tests
// check that every puzzle is solvable and that not every move solves it.

const center = lesson("center", "Control the Center", "The middle of the board is the best place", PAWN, [
  "The four squares in the middle of the board (d4, e4, d5 and e5) are the most important squares!",
  "A piece in the center can reach almost anywhere. A piece in the corner can hardly do anything.",
  "So at the start of a game, fight for the center!",
], [
  ch("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "Take the center! Push your e-pawn or your d-pawn two squares.",
    Goal.playMove(["e2e4", "d2d4"]), { wrongText: "That's fine, but the middle squares are the best. Push your e-pawn or d-pawn two squares!" }),
  ch("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", "Both sides have a pawn in the center. Bring a Knight out to help fight for it!",
    Goal.playMove(["g1f3", "b1c3"]), { wrongText: "Knights love the center! Try moving a Knight to f3 or c3." }),
  ch("4k3/8/8/8/8/8/8/1N2K3 w - - 0 1", "A Knight on the edge can't do much. Which move puts it where it looks at the center?",
    Goal.playMove(["b1c3"]), { wrongText: "On the edge, a Knight sees only a few squares. Move it to c3, where it eyes the center!" }),
  ch("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3", "Black's pawn on e5 holds the center. Challenge it with your d-pawn!",
    Goal.playMove(["d2d4"]), { wrongText: "Good thinking, but this time fight for the center with a pawn: push your d-pawn to d4!" }),
]);

const develop = lesson("develop", "Develop Your Pieces", "Get everyone into the game", BISHOP, [
  "Developing means bringing your Knights and Bishops out from their starting squares so they can join the game.",
  "Pieces stuck at home can't help you! Try to get every Knight and Bishop out early.",
  "Don't bring your Queen out too soon, because she can get chased around. And try not to move the same piece twice.",
], [
  ch("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", "It's the opening! Bring out a Knight or a Bishop.",
    Goal.playMove(["b1c3", "f1e2", "f1d3", "f1c4", "f1b5", "g1e2", "g1f3"]),
    { wrongText: "Not quite! In the opening, bring a Knight or Bishop out toward the center. Pawns and the Queen can wait." }),
  ch("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3", "Your Knight is out. Now develop your Bishop!",
    Goal.playMove(["f1e2", "f1d3", "f1c4", "f1b5"]), { wrongText: "Your Bishop is still asleep at home! Wake it up with a move like Bc4." }),
  ch("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", "Your Bishop and Knight are out, but one piece is still asleep. Develop your other Knight!",
    Goal.playMove(["b1c3"]), { wrongText: "Your Knight on b1 is still at home. Bring it out to c3!" }),
]);

const kingSafety = lesson("king_safety", "Keep Your King Safe", "Castle early and tuck him away", KING, [
  "Your King is the most important piece, so keep him safe! In the middle of the board he can be attacked from everywhere.",
  "The best way to protect him is to castle early. He hides behind his pawns and the Rook comes into the game.",
  "Try to castle in the first ten moves, once your Knight and Bishop are out.",
], [
  ch("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", "Your Knight and Bishop are out, so it's time to castle on the King's side!", Goal.castle(true)),
  ch("rnbqk2r/pppp1ppp/5n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4", "Black is getting ready to attack. Tuck your King away with a castle!", Goal.castle(null)),
  ch("r3kbnr/pppqpppp/2n5/3p1b2/3P1B2/2N5/PPPQPPPP/R3KBNR w KQkq - 6 5", "The pieces on the Queen's side have moved out of the way. Castle that way!", Goal.castle(false)),
]);

const lookFirst = lesson("look_first", "Look Before You Move", "Don't leave your pieces hanging", -BISHOP, [
  "Before every move, ask yourself: is anything of mine hanging? A piece is hanging when the enemy can capture it for free.",
  "You can save a piece by moving it away, by protecting it, or by capturing the piece that attacks it.",
  "Always look at the whole board before you move!",
], [
  ch("rnbqkbnr/1ppp1ppp/p7/1B2p3/4P3/8/PPPP1PPP/RNBQK1NR w KQkq - 0 3", "Black's pawn is about to capture your Bishop! Move it to a square where nothing can capture it.",
    Goal.playMove(["b5a4", "b5c4", "b5d3", "b5e2", "b5f1"]),
    { wrongText: "Oh no, something of yours can still be captured! Move the Bishop where no black piece can reach it." }),
  ch("rnbqkb1r/pppp1ppp/5n2/4p2Q/4P3/8/PPPP1PPP/RNB1KBNR w KQkq - 2 3", "Black's Knight attacks your Queen! Move her to a safe square.",
    Goal.playMove(["h5g5", "h5f5", "h5e5", "h5h4", "h5h3", "h5f3", "h5e2", "h5d1"]),
    { wrongText: "Your Queen can still be captured! Find a square that no black piece attacks." }),
  ch("4k3/8/8/2b5/3N4/8/2P1P3/4K3 w - - 0 1", "Black's Bishop attacks your Knight, and nothing protects it. Move it away, or protect it with a pawn!",
    Goal.playMove(["c2c3", "e2e3", "d4e6", "d4f5", "d4f3", "d4b3", "d4b5", "d4c6"]),
    { wrongText: "Your Knight can still be captured for free! Protect it with a pawn, or move it to safety." }),
]);

const trades = lesson("trades", "Smart Trades", "Trade pieces when you're ahead", -ROOK, [
  "When you have more than your opponent, swapping pieces one for one makes your lead even bigger.",
  "With fewer pieces left, there are fewer tricks to watch out for, and it gets easier to win!",
  "But if you're behind, don't trade. Keep as many pieces as you can.",
], [
  ch("4k3/4p3/3q4/8/3Q4/8/R7/4K3 w - - 0 1", "You have an extra Rook! Trade Queens to make winning easier.",
    Goal.playMove(["d4d6"]), { wrongText: "When you're ahead, trade! Capture the Queen on d6. Black will take back, but you keep your extra Rook." }),
  ch("3rk3/pp3ppp/8/8/8/2N5/PP3PPP/3RK3 w - - 0 1", "You're a Knight ahead. Trade Rooks on the open file!",
    Goal.playMove(["d1d8"]), { wrongText: "Take the trade! Your Rook can capture Black's Rook on d8. Black takes back, and you're still ahead." }),
]);

const rookFiles = lesson("rook_files", "Rooks Love Open Files", "Give your Rooks room to run", ROOK, [
  "An open file is a column of squares with no pawns on it. Rooks are strongest there, because nothing blocks their way!",
  "Another great spot is the seventh row, where a Rook can attack lots of pawns.",
  "Wake up your Rooks! A sleepy Rook stuck behind its own pawn does nothing.",
], [
  ch("4k3/pp3ppp/8/8/8/8/PP3PPP/R3K3 w - - 0 1", "Put your Rook on an open file: a column with no pawns!",
    Goal.playMove(["a1c1", "a1d1"]), { wrongText: "A Rook is strongest on a column with no pawns in the way. Look for an open file!" }),
  ch("6k1/pp3ppp/8/8/8/8/PP3PPP/3RK3 w - - 0 1", "Your Rook is on an open file. Now march it to the seventh row, where it attacks the pawns!",
    Goal.playMove(["d1d7"]), { wrongText: "Rooks love the seventh row, where they attack the enemy pawns. March your Rook up to d7!" }),
  ch("4k3/pp3ppp/8/8/8/8/PP3PPP/R2R2K1 w - - 0 1", "One of your Rooks is asleep behind a pawn. Wake it up by moving it to an open file!",
    Goal.playMove(["a1c1"]), { wrongText: "The Rook on a1 is stuck behind its own pawn. Move it to the open c-file!" }),
]);

const endgamePlans = lesson("endgame_plans", "Endgame Plans", "Wake up your King and push your pawns", -PAWN, [
  "In the endgame, only a few pieces are left, so there's less danger. Your King can come out and fight!",
  "Walk your King toward the center and toward the action. A King hiding in the corner doesn't help.",
  "A passed pawn has no enemy pawns in front of it. Push it to make a new Queen!",
], [
  ch("4k3/8/8/8/8/8/6P1/6K1 w - - 0 1", "Time to wake up your King! Walk him toward the center and collect the stars.", Goal.collectStars(squares("f2", "e3", "d4"))),
  ch("6k1/6pp/8/8/8/8/P5P1/4K3 w - - 0 1", "You have two pawns, but only one is a passed pawn that nothing can stop. Push it!",
    Goal.playMove(["a2a3", "a2a4"]), { wrongText: "That pawn has enemy pawns in its way. Find the passed pawn, where no black pawn can stop it!" }),
]);

export const CHESS_UNITS = [
  { title: "Meet the Pieces", emoji: "♞", lessons: [rook, bishop, queen, king, knight, pawn] },
  { title: "Capture!", emoji: "🎯", lessons: [capture, promotion] },
  { title: "Attack the King", emoji: "⚔️", lessons: [check, checkmate] },
  { title: "Special Rules", emoji: "✨", lessons: [castling, enPassant, stalemate] },
  { title: "Clever Tricks", emoji: "🧠", lessons: [forks] },
  { title: "Opening Strategy", emoji: "🚀", lessons: [center, develop, kingSafety] },
  { title: "Winning Plans", emoji: "🏆", lessons: [lookFirst, trades, rookFiles, endgamePlans] },
];
export const CHESS_LESSONS = CHESS_UNITS.flatMap((u) => u.lessons);
