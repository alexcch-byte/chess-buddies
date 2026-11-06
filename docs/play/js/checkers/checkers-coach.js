// Kid-friendly explanations for checkers. Port of checkers/CheckersCoach.kt.
import { Square } from "../chess/position.js";
import { Checker, isDark } from "./checkers.js";

/** Explains why moving the checker on `from` to `to` isn't allowed. */
export function explainIllegalCheckers(pos, from, to) {
  const p = pos.get(from);
  const legal = pos.legalMoves();
  const mustJump = legal.length > 0 && legal[0].isCapture;
  const mine = legal.filter((m) => m.from === from);
  if (mustJump && mine.length === 0) return "You have to jump when you can! Find the checker that can jump.";
  if (mustJump) return "This checker has to jump! Hop over the enemy checker.";
  if (from === to && mine.length === 0) return "That checker is stuck right now. Try another one!";
  if (!isDark(to)) return "Checkers only move on the dark squares.";
  if (pos.get(to) !== 0) return "That square is already taken.";
  const df = Square.file(to) - Square.file(from);
  const dr = Square.rank(to) - Square.rank(from);
  const forward = p > 0 ? dr : -dr;
  if (Math.abs(df) !== Math.abs(dr)) return "Checkers move diagonally, from corner to corner.";
  if (!Checker.isKing(p) && forward < 0) return "Regular checkers can only move forward. Kings can move backward!";
  if (Math.abs(df) === 2) return "You can only jump over an enemy checker, and only onto an empty square.";
  return "Checkers move just one square at a time, unless they're jumping.";
}
