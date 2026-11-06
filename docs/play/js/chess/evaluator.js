// Static evaluation in centipawns from the side to move's point of view. Port of engine/Evaluator.kt.
import { Square, NONE, PAWN, KNIGHT, BISHOP, ROOK, KING } from "./position.js";

/** Indexed by piece type. */
export const VALUE = [0, 100, 310, 325, 500, 900, 0];

// Tables are written as White sees the board: rank 8 on top, a-file on the left.
const PAWN_T = [
  0, 0, 0, 0, 0, 0, 0, 0,
  60, 60, 60, 60, 60, 60, 60, 60,
  15, 15, 25, 35, 35, 25, 15, 15,
  6, 6, 12, 26, 26, 12, 6, 6,
  2, 2, 6, 22, 22, 4, 2, 2,
  4, 0, -4, 4, 4, -6, 0, 4,
  4, 8, 8, -18, -18, 8, 8, 4,
  0, 0, 0, 0, 0, 0, 0, 0,
];
const KNIGHT_T = [
  -50, -35, -28, -28, -28, -28, -35, -50,
  -35, -18, 0, 4, 4, 0, -18, -35,
  -28, 4, 12, 16, 16, 12, 4, -28,
  -28, 6, 16, 22, 22, 16, 6, -28,
  -28, 2, 16, 22, 22, 16, 2, -28,
  -28, 6, 12, 14, 14, 12, 6, -28,
  -35, -18, 0, 6, 6, 0, -18, -35,
  -50, -32, -28, -28, -28, -28, -32, -50,
];
const BISHOP_T = [
  -20, -10, -10, -10, -10, -10, -10, -20,
  -10, 0, 0, 0, 0, 0, 0, -10,
  -10, 0, 6, 10, 10, 6, 0, -10,
  -10, 6, 6, 10, 10, 6, 6, -10,
  -10, 0, 10, 10, 10, 10, 0, -10,
  -10, 10, 10, 10, 10, 10, 10, -10,
  -10, 6, 0, 0, 0, 0, 6, -10,
  -20, -10, -12, -10, -10, -12, -10, -20,
];
const ROOK_T = [
  0, 0, 0, 0, 0, 0, 0, 0,
  10, 15, 15, 15, 15, 15, 15, 10,
  -5, 0, 0, 0, 0, 0, 0, -5,
  -5, 0, 0, 0, 0, 0, 0, -5,
  -5, 0, 0, 0, 0, 0, 0, -5,
  -5, 0, 0, 0, 0, 0, 0, -5,
  -5, 0, 0, 0, 0, 0, 0, -5,
  0, 0, 0, 6, 6, 4, 0, 0,
];
const QUEEN_T = [
  -20, -10, -10, -5, -5, -10, -10, -20,
  -10, 0, 0, 0, 0, 0, 0, -10,
  -10, 0, 5, 5, 5, 5, 0, -10,
  -5, 0, 5, 5, 5, 5, 0, -5,
  0, 0, 5, 5, 5, 5, 0, -5,
  -10, 5, 5, 5, 5, 5, 0, -10,
  -10, 0, 5, 0, 0, 0, 0, -10,
  -20, -10, -10, -5, -5, -10, -10, -20,
];
const KING_MG = [
  -30, -40, -40, -50, -50, -40, -40, -30,
  -30, -40, -40, -50, -50, -40, -40, -30,
  -30, -40, -40, -50, -50, -40, -40, -30,
  -30, -40, -40, -50, -50, -40, -40, -30,
  -20, -30, -30, -40, -40, -30, -30, -20,
  -10, -20, -20, -20, -20, -20, -20, -10,
  15, 15, -5, -5, -5, -5, 15, 15,
  20, 30, 10, 0, 0, 10, 30, 20,
];
const KING_EG = [
  -50, -40, -30, -20, -20, -30, -40, -50,
  -30, -20, -10, 0, 0, -10, -20, -30,
  -30, -10, 20, 30, 30, 20, -10, -30,
  -30, -10, 30, 40, 40, 30, -10, -30,
  -30, -10, 30, 40, 40, 30, -10, -30,
  -30, -10, 20, 30, 30, 20, -10, -30,
  -30, -30, 0, 0, 0, 0, -30, -30,
  -50, -30, -30, -30, -30, -30, -30, -50,
];
const TABLES = [new Array(64).fill(0), PAWN_T, KNIGHT_T, BISHOP_T, ROOK_T, QUEEN_T];
const PHASE_WEIGHT = [0, 0, 1, 1, 2, 4, 0];

const tableIndex = (sq, white) => (white ? (7 - Square.rank(sq)) * 8 + Square.file(sq) : sq);

export function evaluate(pos) {
  let score = 0, phase = 0;
  let whiteKing = NONE, blackKing = NONE;
  let whiteBishops = 0, blackBishops = 0, whiteMaterial = 0, blackMaterial = 0;
  for (let sq = 0; sq < 64; sq++) {
    const p = pos.board[sq];
    if (p === 0) continue;
    const type = Math.abs(p);
    const white = p > 0;
    if (type === KING) {
      if (white) whiteKing = sq; else blackKing = sq;
      continue;
    }
    phase += PHASE_WEIGHT[type];
    const v = VALUE[type] + TABLES[type][tableIndex(sq, white)];
    if (white) {
      score += v;
      whiteMaterial += VALUE[type];
      if (type === BISHOP) whiteBishops++;
    } else {
      score -= v;
      blackMaterial += VALUE[type];
      if (type === BISHOP) blackBishops++;
    }
  }
  if (whiteBishops >= 2) score += 30;
  if (blackBishops >= 2) score -= 30;

  const mg = Math.min(phase, 24);
  // Kotlin's Int division truncates toward zero; Math.trunc matches it.
  if (whiteKing !== NONE) {
    const i = tableIndex(whiteKing, true);
    score += Math.trunc((KING_MG[i] * mg + KING_EG[i] * (24 - mg)) / 24);
  }
  if (blackKing !== NONE) {
    const i = tableIndex(blackKing, false);
    score -= Math.trunc((KING_MG[i] * mg + KING_EG[i] * (24 - mg)) / 24);
  }

  // Help the stronger side drive a lone king into the corner to finish the game.
  if (whiteKing !== NONE && blackKing !== NONE) {
    if (blackMaterial === 0 && whiteMaterial >= VALUE[ROOK]) score += mopUp(whiteKing, blackKing);
    if (whiteMaterial === 0 && blackMaterial >= VALUE[ROOK]) score -= mopUp(blackKing, whiteKing);
  }
  return pos.whiteToMove ? score : -score;
}

function mopUp(winnerKing, loserKing) {
  const lf = Square.file(loserKing), lr = Square.rank(loserKing);
  const centerDistance = Math.max(3 - lf, lf - 4) + Math.max(3 - lr, lr - 4);
  const kingDistance = Math.abs(lf - Square.file(winnerKing)) + Math.abs(lr - Square.rank(winnerKing));
  return 10 * centerDistance + 4 * (14 - kingDistance);
}

// Re-exported so callers needing piece constants can stay on one import.
export { PAWN, KNIGHT, BISHOP };
