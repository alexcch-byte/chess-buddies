// What the bots can be asked to do. Runs inside the Web Worker, or on the main thread if workers aren't available.
import { Position } from "./chess/position.js";
import { chooseMove, bestMove, botById } from "./chess/ai.js";
import { CheckersPosition, cmove } from "./checkers/checkers.js";
import { chooseCheckersMove, bestCheckersMove, scoreCheckersMoves } from "./checkers/checkers-ai.js";

/** Plain-data form of a checkers position, so it can cross into the worker. */
export const packCheckers = (pos) => ({ board: Array.from(pos.board), red: pos.redToMove, quiet: pos.quietPlies });
const unpackCheckers = (p) => new CheckersPosition(Int8Array.from(p.board), p.red, p.quiet);
const plainCheckersMove = (m) => (m ? { path: m.path, captured: m.captured } : null);
export const revivedCheckersMove = (m) => (m ? cmove(m.path, m.captured) : null);

export function handle(msg) {
  switch (msg.op) {
    case "chessChoose":
      return chooseMove(Position.fromFen(msg.fen), botById(msg.botId));
    case "chessBest": {
      const r = bestMove(Position.fromFen(msg.fen), msg.depth, msg.ms);
      return r ? { move: r.move, score: r.score, depth: r.depth } : null;
    }
    case "ckChoose":
      return plainCheckersMove(chooseCheckersMove(unpackCheckers(msg.pos), botById(msg.botId)));
    case "ckBest":
      return plainCheckersMove(bestCheckersMove(unpackCheckers(msg.pos), msg.depth, msg.ms));
    case "ckScore": {
      const scores = scoreCheckersMoves(unpackCheckers(msg.pos), msg.depth, msg.ms);
      return [...scores.entries()].map(([m, score]) => ({ path: m.path, score }));
    }
    default:
      throw new Error("Unknown AI request: " + msg.op);
  }
}
