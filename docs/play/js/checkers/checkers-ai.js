// Alpha-beta search for checkers. Port of checkers/CheckersAi.kt: the same forest-friend bots, with checkers-sized settings.
import { Square } from "../chess/position.js";
import { Checker } from "./checkers.js";

export const WIN = 100_000;
const INF = 1_000_000;
const MAX_PLY = 64;

const SETTINGS = {
  bunny: { randomMoveChance: 0.6, depth: 1, noise: 150, timeLimitMs: 600 },
  kitten: { randomMoveChance: 0.15, depth: 2, noise: 80, timeLimitMs: 800 },
  fox: { randomMoveChance: 0.0, depth: 4, noise: 30, timeLimitMs: 1200 },
  owl: { randomMoveChance: 0.0, depth: 6, noise: 10, timeLimitMs: 2000 },
  dragon: { randomMoveChance: 0.0, depth: 14, noise: 0, timeLimitMs: 2200 },
};

const now = () => (typeof performance !== "undefined" ? performance.now() : Date.now());
const ordered = (moves) => moves.map((m) => [m.captured.length, m]).sort((a, b) => b[0] - a[0]).map((x) => x[1]);

export function chooseCheckersMove(pos, bot, random = Math.random) {
  const legal = pos.legalMoves();
  if (legal.length === 0) return null;
  if (legal.length === 1) return legal[0];
  const s = SETTINGS[bot.id] ?? SETTINGS.bunny;
  if (random() < s.randomMoveChance) return legal[Math.floor(random() * legal.length)];
  if (s.noise === 0) return bestCheckersMove(pos, s.depth, s.timeLimitMs) ?? legal[0];

  const searcher = new Searcher(now() + s.timeLimitMs);
  let best = null;
  let bestScore = -INF;
  for (const m of ordered(legal)) {
    const raw = -searcher.negamax(pos.makeMove(m), s.depth - 1, -INF, INF, 1);
    if (searcher.aborted) break;
    const wobble = Math.abs(raw) >= WIN - MAX_PLY ? 0 : Math.floor(random() * (2 * s.noise + 1)) - s.noise;
    const score = raw + wobble;
    if (score > bestScore) { bestScore = score; best = m; }
  }
  return best ?? legal[0];
}

/** Iterative-deepening search for the strongest bot and for hints. */
export function bestCheckersMove(pos, maxDepth, timeLimitMs) {
  const legal = pos.legalMoves();
  if (legal.length === 0) return null;
  if (legal.length === 1) return legal[0];
  const searcher = new Searcher(now() + timeLimitMs);
  let rootMoves = ordered(legal);
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
      if (result === null) result = bestThisDepth;
      break;
    }
    if (!bestThisDepth) break;
    result = bestThisDepth;
    rootMoves = [bestThisDepth, ...rootMoves.filter((m) => m !== bestThisDepth)];
    if (alpha >= WIN - MAX_PLY) break;
  }
  return result ?? rootMoves[0];
}

/**
 * Scores every legal move with a short search, from the mover's point of view.
 * The coach uses this to notice when a move throws pieces away. Returns a Map of move -> score.
 */
export function scoreCheckersMoves(pos, depth, timeLimitMs) {
  const searcher = new Searcher(now() + timeLimitMs);
  const out = new Map();
  for (const m of ordered(pos.legalMoves())) {
    const score = -searcher.negamax(pos.makeMove(m), depth - 1, -INF, INF, 1);
    if (searcher.aborted) break;
    out.set(m, score);
  }
  return out;
}

/** Static evaluation from the side to move's point of view. */
export function evaluateCheckers(pos) {
  let score = 0, redMaterial = 0, blackMaterial = 0, pieces = 0;
  for (let sq = 0; sq < 64; sq++) if (pos.get(sq) !== 0) pieces++;
  const endgame = pieces <= 10;
  for (let sq = 0; sq < 64; sq++) {
    const p = pos.get(sq);
    if (p === 0) continue;
    const red = p > 0;
    const f = Square.file(sq), r = Square.rank(sq);
    let v;
    if (Checker.isKing(p)) {
      v = 160;
      if (f >= 2 && f <= 5 && r >= 2 && r <= 5) v += 8;
      if (f === 0 || f === 7) v -= 6;
    } else {
      v = 100;
      const advance = red ? r : 7 - r;
      v += advance * (endgame ? 6 : 3);
      // Men on the back row stop the other side from getting kings.
      if (!endgame && advance === 0) v += 8;
      if (f >= 2 && f <= 5 && r >= 2 && r <= 5) v += 4;
    }
    if (red) { score += v; redMaterial += v; } else { score -= v; blackMaterial += v; }
  }
  // When ahead, send the kings after the remaining enemy pieces so the game gets finished.
  if (endgame && redMaterial !== blackMaterial) {
    const strongRed = redMaterial > blackMaterial;
    score += (strongRed ? 1 : -1) * huntBonus(pos, strongRed);
  }
  return pos.redToMove ? score : -score;
}

function huntBonus(pos, red) {
  const targets = [];
  for (let sq = 0; sq < 64; sq++) if (pos.get(sq) !== 0 && (pos.get(sq) > 0) !== red) targets.push(sq);
  if (targets.length === 0) return 0;
  let bonus = 0;
  for (let sq = 0; sq < 64; sq++) {
    const p = pos.get(sq);
    if (p === 0 || (p > 0) !== red || !Checker.isKing(p)) continue;
    let nearest = 14;
    for (const t of targets) {
      nearest = Math.min(nearest, Math.max(Math.abs(Square.file(t) - Square.file(sq)), Math.abs(Square.rank(t) - Square.rank(sq))));
    }
    bonus += (8 - nearest) * 3;
  }
  return bonus;
}

class Searcher {
  constructor(deadline) {
    this.deadline = deadline;
    this.aborted = false;
    this.nodes = 0;
  }

  negamax(pos, depth, alphaIn, beta, ply) {
    if ((++this.nodes & 1023) === 0 && now() > this.deadline) this.aborted = true;
    if (this.aborted) return 0;
    const moves = pos.legalMoves();
    if (moves.length === 0) return -WIN + ply;
    // Jumps are forced, so keep following them past the depth limit (a natural quiescence search).
    if ((depth <= 0 && !moves[0].isCapture) || ply >= MAX_PLY) return evaluateCheckers(pos);
    let alpha = alphaIn;
    let best = -INF;
    for (const m of ordered(moves)) {
      const score = -this.negamax(pos.makeMove(m), depth - 1, -beta, -alpha, ply + 1);
      if (this.aborted) return 0;
      if (score > best) best = score;
      if (score > alpha) alpha = score;
      if (alpha >= beta) break;
    }
    return best;
  }
}
