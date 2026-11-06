// Simple position checks that let the app give kid-friendly advice. Port of engine/Coach.kt.
import { Piece, Square, PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING } from "./position.js";

/**
 * Pieces of the given color that can be won: attacked and either unprotected,
 * or attacked by something worth less.
 */
export function hangingPieces(pos, white) {
  const result = [];
  for (let sq = 0; sq < 64; sq++) {
    const p = pos.get(sq);
    if (p === 0 || (p > 0) !== white || Math.abs(p) === KING) continue;
    const attackers = pos.attackers(sq, !white);
    if (attackers.length === 0) continue;
    const defended = pos.attackers(sq, white).length > 0;
    // A king can't capture a protected piece, so it never counts as a cheap attacker.
    // Points (not centipawns) keep even trades like Knight-for-Bishop from counting.
    const cheapest = Math.min(...attackers.map((a) => (Math.abs(pos.get(a)) === KING ? Infinity : Piece.points(Math.abs(pos.get(a))))));
    if (!defended || cheapest < Piece.points(Math.abs(p))) result.push(sq);
  }
  return result;
}

/** A move that checkmates right away, if the side to move has one. */
export function mateInOne(pos) {
  return pos.legalMoves().find((m) => pos.makeMove(m).isCheckmate()) ?? null;
}

/** If it were the opponent's turn, could they checkmate immediately? Returns their mating move. */
export function opponentMateThreat(pos) {
  if (pos.inCheck()) return null;
  return mateInOne(pos.passTurn());
}

/** Explains, in plain words, why a move from `from` to `to` isn't allowed. */
export function explainIllegal(pos, from, to) {
  const p = pos.get(from);
  const type = Math.abs(p);
  const white = p > 0;
  if (pos.pseudoLegalMoves().some((m) => m.from === from && m.to === to)) {
    if (pos.inCheck()) return "Your King is in check! First you have to get your King out of danger.";
    if (type === KING) return "The King can't step into danger. That square is attacked!";
    return "Careful! That move would leave your King in danger.";
  }
  const target = pos.get(to);
  if (target !== 0 && (target > 0) === white) return "You can't capture your own piece!";

  const df = Square.file(to) - Square.file(from);
  const dr = Square.rank(to) - Square.rank(from);
  const forward = white ? dr : -dr;
  const straight = df === 0 || dr === 0;
  const diagonal = Math.abs(df) === Math.abs(dr);
  switch (type) {
    case PAWN:
      if (forward <= 0) return "Pawns can only move forward, never backward or sideways.";
      if (Math.abs(df) === 1 && forward === 1) return "Pawns can only move diagonally when they capture something.";
      if (df === 0 && target !== 0) return "Pawns can't capture straight ahead. That square is blocked!";
      if (df === 0 && forward === 2) return "A pawn can only jump two squares on its very first move, and both squares must be empty.";
      return "Pawns move straight forward, one square at a time.";
    case KNIGHT: return "Knights move in an L shape: two squares one way, then one square to the side.";
    case BISHOP: return diagonal ? "Something is in the Bishop's way!" : "Bishops only move diagonally.";
    case ROOK: return straight ? "Something is in the Rook's way!" : "Rooks only move in straight lines: up, down, left or right.";
    case QUEEN: return straight || diagonal ? "Something is in the Queen's way!" : "The Queen moves in straight lines and diagonals, but not in an L shape.";
    case KING: return dr === 0 && Math.abs(df) === 2 ? "You can't castle right now." : "The King moves just one square at a time.";
    default: return "That move isn't allowed.";
  }
}
