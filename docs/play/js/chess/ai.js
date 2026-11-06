// The computer opponents and their search. Port of engine/Ai.kt.
import { PAWN, isCapture, isEnPassant } from "./position.js";
import { evaluate, VALUE } from "./evaluator.js";

/** The computer opponents, from gentlest to toughest. */
export const BOTS = [
  { id: "bunny", displayName: "Bubbles the Bunny", emoji: "🐰", blurb: "Just learning! Hops around and makes lots of mistakes.", level: 1, randomMoveChance: 0.7, depth: 1, noise: 250, timeLimitMs: 800 },
  { id: "kitten", displayName: "Kiki the Kitten", emoji: "🐱", blurb: "Loves to pounce on pieces, but forgets to look out.", level: 2, randomMoveChance: 0.15, depth: 1, noise: 120, timeLimitMs: 1000 },
  { id: "fox", displayName: "Felix the Fox", emoji: "🦊", blurb: "Sneaky! Spots captures and simple tricks.", level: 3, randomMoveChance: 0.0, depth: 2, noise: 60, timeLimitMs: 1500 },
  { id: "owl", displayName: "Professor Owl", emoji: "🦉", blurb: "Thinks a few moves ahead. A real test!", level: 4, randomMoveChance: 0.0, depth: 3, noise: 20, timeLimitMs: 2500 },
  { id: "dragon", displayName: "Drake the Dragon", emoji: "🐉", blurb: "The forest champion. Can you beat the Dragon?", level: 5, randomMoveChance: 0.0, depth: 6, noise: 0, timeLimitMs: 2500 },
];
export const botById = (id) => BOTS.find((b) => b.id === id) ?? BOTS[0];

export const MATE = 100_000;
const INF = 1_000_000;
const MAX_PLY = 40;

const now = () => (typeof performance !== "undefined" ? performance.now() : Date.now());

/** Picks a move for bot. Never returns null if the side to move has a legal move. */
export function chooseMove(pos, bot, random = Math.random) {
  const legal = pos.legalMoves();
  if (legal.length === 0) return null;
  if (legal.length === 1) return legal[0];
  if (random() < bot.randomMoveChance) return legal[Math.floor(random() * legal.length)];
  if (bot.noise === 0) return bestMove(pos, bot.depth, bot.timeLimitMs)?.move ?? legal[0];

  const searcher = new Searcher(now() + bot.timeLimitMs);
  const ordered = orderMoves(pos, legal);
  let best = null;
  let bestScore = -INF;
  for (const m of ordered) {
    const raw = -searcher.negamax(pos.makeMove(m), bot.depth - 1, -INF, INF, 1);
    if (searcher.aborted) break;
    const wobble = Math.abs(raw) >= MATE - MAX_PLY ? 0 : Math.floor(random() * (2 * bot.noise + 1)) - bot.noise;
    const score = raw + wobble;
    if (score > bestScore) { bestScore = score; best = m; }
  }
  return best ?? ordered[0];
}

/** Iterative-deepening alpha-beta search. Used by the strongest bot and for hints. */
export function bestMove(pos, maxDepth, timeLimitMs) {
  const legal = pos.legalMoves();
  if (legal.length === 0) return null;
  if (legal.length === 1) return { move: legal[0], score: 0, depth: 0 };
  const searcher = new Searcher(now() + timeLimitMs);
  let rootMoves = orderMoves(pos, legal);
  let result = null;
  for (let depth = 1; depth <= maxDepth; depth++) {
    let alpha = -INF;
    let bestThisDepth = null;
    for (const m of rootMoves) {
      const score = -searcher.negamax(pos.makeMove(m), depth - 1, -INF, -alpha, 1);
      if (searcher.aborted) break;
      if (score > alpha) { alpha = score; bestThisDepth = m; }
    }
    if (searcher.aborted) {
      // A partial search still beats the previous depth if the new best move was found first.
      if (result === null && bestThisDepth) result = { move: bestThisDepth, score: alpha, depth };
      break;
    }
    if (!bestThisDepth) break;
    result = { move: bestThisDepth, score: alpha, depth };
    rootMoves = [bestThisDepth, ...rootMoves.filter((m) => m !== bestThisDepth)];
    if (alpha >= MATE - MAX_PLY) break;
  }
  return result ?? { move: rootMoves[0], score: 0, depth: 0 };
}

function moveOrderScore(pos, m) {
  let s = 0;
  if (isCapture(m)) {
    const victim = isEnPassant(m) ? PAWN : Math.abs(pos.get(m.to));
    s += 10_000 + VALUE[victim] * 10 - Math.trunc(VALUE[Math.abs(pos.get(m.from))] / 10);
  }
  if (m.promotion) s += 9_000 + VALUE[m.promotion];
  return s;
}

function orderMoves(pos, moves) {
  return moves.map((m) => [moveOrderScore(pos, m), m]).sort((a, b) => b[0] - a[0]).map((x) => x[1]);
}

class Searcher {
  constructor(deadline) {
    this.deadline = deadline;
    this.aborted = false;
    this.nodes = 0;
  }

  tick() {
    if ((++this.nodes & 1023) === 0 && now() > this.deadline) this.aborted = true;
  }

  negamax(pos, depthIn, alphaIn, beta, ply) {
    this.tick();
    if (this.aborted) return 0;
    if (pos.halfmoveClock >= 100) return 0;
    const inCheck = pos.inCheck();
    let depth = depthIn;
    if (depth <= 0) {
      if (!inCheck || ply >= MAX_PLY) return this.quiesce(pos, alphaIn, beta, ply);
      depth = 1; // Look one more move when in check so mates aren't missed.
    }
    let alpha = alphaIn;
    let best = -INF;
    let legal = 0;
    for (const m of orderMoves(pos, pos.pseudoLegalMoves())) {
      const child = pos.makeMove(m);
      if (child.moverLeftKingInCheck()) continue;
      legal++;
      const score = -this.negamax(child, depth - 1, -beta, -alpha, ply + 1);
      if (this.aborted) return 0;
      if (score > best) best = score;
      if (score > alpha) alpha = score;
      if (alpha >= beta) break;
    }
    if (legal === 0) return inCheck ? -MATE + ply : 0;
    return best;
  }

  quiesce(pos, alphaIn, beta, ply) {
    this.tick();
    if (this.aborted) return 0;
    const standPat = evaluate(pos);
    if (standPat >= beta || ply >= MAX_PLY) return standPat;
    let alpha = Math.max(alphaIn, standPat);
    for (const m of orderMoves(pos, pos.pseudoLegalMoves(true))) {
      const child = pos.makeMove(m);
      if (child.moverLeftKingInCheck()) continue;
      const score = -this.quiesce(child, -beta, -alpha, ply + 1);
      if (this.aborted) return 0;
      if (score >= beta) return score;
      if (score > alpha) alpha = score;
    }
    return alpha;
  }
}
