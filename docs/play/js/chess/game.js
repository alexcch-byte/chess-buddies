// Game record and move descriptions. Ports of engine/Game.kt and engine/Notation.kt.
import { Piece, Square, PAWN, isCastle, isCapture, isEnPassant, Position } from "./position.js";

export const EndReason = Object.freeze({
  CHECKMATE: "CHECKMATE",
  STALEMATE: "STALEMATE",
  INSUFFICIENT_MATERIAL: "INSUFFICIENT_MATERIAL",
  FIFTY_MOVES: "FIFTY_MOVES",
  REPETITION: "REPETITION",
});

/** An immutable game record: every position from the start, plus the moves between them. */
export class Game {
  constructor(start = Position.start(), positions = null, moves = null) {
    this.positions = positions ?? [start];
    this.moves = moves ?? [];
    this._outcome = undefined;
  }

  get position() { return this.positions[this.positions.length - 1]; }
  get lastMove() { return this.moves.length ? this.moves[this.moves.length - 1] : null; }

  /** { whiteWins: boolean|null, reason } or null while the game goes on. Computed lazily. */
  get outcome() {
    if (this._outcome === undefined) this._outcome = this.#computeOutcome();
    return this._outcome;
  }

  get isOver() { return this.outcome !== null; }

  play(m) {
    return new Game(null, [...this.positions, this.position.makeMove(m)], [...this.moves, m]);
  }

  /** Takes back up to count moves (never past the starting position). */
  undo(count = 1) {
    const n = Math.min(count, this.moves.length);
    if (n === 0) return this;
    return new Game(null, this.positions.slice(0, this.positions.length - n), this.moves.slice(0, this.moves.length - n));
  }

  #computeOutcome() {
    const pos = this.position;
    if (!pos.hasLegalMove()) {
      return pos.inCheck()
        ? { whiteWins: !pos.whiteToMove, reason: EndReason.CHECKMATE }
        : { whiteWins: null, reason: EndReason.STALEMATE };
    }
    if (pos.isInsufficientMaterial()) return { whiteWins: null, reason: EndReason.INSUFFICIENT_MATERIAL };
    if (pos.halfmoveClock >= 100) return { whiteWins: null, reason: EndReason.FIFTY_MOVES };
    const key = pos.repetitionKey();
    if (this.positions.filter((p) => p.repetitionKey() === key).length >= 3) {
      return { whiteWins: null, reason: EndReason.REPETITION };
    }
    return null;
  }
}

const fileLetter = (sq) => String.fromCharCode(97 + Square.file(sq));

export const Notation = {
  /** Standard algebraic notation, e.g. "Nf3", "exd5", "O-O", "e8=Q#". */
  san(pos, m) {
    let s = "";
    const type = Math.abs(pos.get(m.from));
    if (isCastle(m)) {
      s += m.to > m.from ? "O-O" : "O-O-O";
    } else {
      if (type === PAWN) {
        if (isCapture(m)) s += fileLetter(m.from);
      } else {
        s += Piece.letter(type);
        const rivals = pos.legalMoves().filter(
          (o) => o.to === m.to && o.from !== m.from && Math.abs(pos.get(o.from)) === type);
        if (rivals.length) {
          const sameFile = rivals.some((o) => Square.file(o.from) === Square.file(m.from));
          const sameRank = rivals.some((o) => Square.rank(o.from) === Square.rank(m.from));
          if (!sameFile) s += fileLetter(m.from);
          else if (!sameRank) s += String(Square.rank(m.from) + 1);
          else s += Square.name(m.from);
        }
      }
      if (isCapture(m)) s += "x";
      s += Square.name(m.to);
      if (m.promotion) s += "=" + Piece.letter(m.promotion);
    }
    const after = pos.makeMove(m);
    if (after.inCheck()) s += after.hasLegalMove() ? "+" : "#";
    return s;
  },

  /** A sentence a child can follow, e.g. "Knight from g1 to f3" or "Pawn takes Bishop on e5". */
  describe(pos, m) {
    const type = Math.abs(pos.get(m.from));
    const name = Piece.name(type);
    if (isCastle(m)) return m.to > m.from ? "Castle on the King's side" : "Castle on the Queen's side";
    const target = Square.name(m.to);
    let base;
    if (isEnPassant(m)) base = `Pawn takes Pawn en passant on ${target}`;
    else if (isCapture(m)) base = `${name} takes ${Piece.name(Math.abs(pos.get(m.to)))} on ${target}`;
    else base = `${name} from ${Square.name(m.from)} to ${target}`;
    return m.promotion ? `${base} and becomes a ${Piece.name(m.promotion)}` : base;
  },
};
