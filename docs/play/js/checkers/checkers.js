// American / English checkers rules. A direct port of checkers/Checkers.kt.
// Pieces are signed ints like the chess engine: positive Red, negative Black. Red starts at the bottom and moves up.
import { Square, NONE } from "../chess/position.js";

export const Checker = {
  EMPTY: 0, RED: 1, RED_KING: 2, BLACK: -1, BLACK_KING: -2,
  isRed: (p) => p > 0,
  isKing: (p) => Math.abs(p) === 2,
  crown: (p) => (p > 0 ? 2 : -2),
  /** The rank a man must reach to be crowned. */
  kingRow: (red) => (red ? 7 : 0),
  sideName: (red) => (red ? "Red" : "Black"),
};

/** One checkers move. A jump can hop several times; path lists every square landed on. */
export function cmove(path, captured = []) {
  return { path, captured, from: path[0], to: path[path.length - 1], isCapture: captured.length > 0 };
}
/** e.g. "c3-d4" or "c1xe3xg5". */
export const cnotation = (m) => m.path.map(Square.name).join(m.isCapture ? "x" : "-");

const MAN_UP = [[-1, 1], [1, 1]];
const MAN_DOWN = [[-1, -1], [1, -1]];
const ALL = [...MAN_UP, ...MAN_DOWN];
const directions = (piece) => (Checker.isKing(piece) ? ALL : piece > 0 ? MAN_UP : MAN_DOWN);

/** Checkers live on the dark squares, where file + rank is even (a1 is dark). */
export const isDark = (sq) => (Square.file(sq) + Square.rank(sq)) % 2 === 0;

/** An immutable checkers position. */
export class CheckersPosition {
  constructor(board, redToMove, quietPlies) {
    this.board = board;
    this.redToMove = redToMove;
    /** Plies in a row with only king moves and no captures, for the draw rule. */
    this.quietPlies = quietPlies;
  }

  get(sq) { return this.board[sq]; }

  /** Every legal move. Jumping is compulsory, so if any jump exists only jumps are returned. */
  legalMoves() {
    const jumps = this.captureMoves();
    return jumps.length ? jumps : this.simpleMoves();
  }

  captureMoves() {
    const out = [];
    for (let sq = 0; sq < 64; sq++) {
      const p = this.board[sq];
      if (p !== 0 && (p > 0) === this.redToMove) this.#collectJumps(sq, out);
    }
    return out;
  }

  simpleMoves() {
    const out = [];
    for (let sq = 0; sq < 64; sq++) {
      const p = this.board[sq];
      if (p === 0 || (p > 0) !== this.redToMove) continue;
      for (const [df, dr] of directions(p)) {
        const f = Square.file(sq) + df, r = Square.rank(sq) + dr;
        if (Square.onBoard(f, r) && this.board[Square.of(f, r)] === 0) out.push(cmove([sq, Square.of(f, r)]));
      }
    }
    return out;
  }

  #collectJumps(origin, out) {
    const piece = this.board[origin];
    // The jumping piece has left its square, so a king may loop back through it.
    const b = this.board.slice();
    b[origin] = 0;

    const dfs = (sq, path, captured) => {
      // A man that reaches the far row is crowned and its turn ends right there.
      if (path.length > 1 && !Checker.isKing(piece) && Square.rank(sq) === Checker.kingRow(piece > 0)) {
        out.push(cmove(path, captured));
        return;
      }
      let extended = false;
      for (const [df, dr] of directions(piece)) {
        const lf = Square.file(sq) + 2 * df, lr = Square.rank(sq) + 2 * dr;
        if (!Square.onBoard(lf, lr)) continue;
        const mid = Square.of(Square.file(sq) + df, Square.rank(sq) + dr);
        const land = Square.of(lf, lr);
        const m = b[mid];
        if (m === 0 || (m > 0) === (piece > 0) || captured.includes(mid) || b[land] !== 0) continue;
        extended = true;
        dfs(land, [...path, land], [...captured, mid]);
      }
      if (!extended && path.length > 1) out.push(cmove(path, captured));
    };

    dfs(origin, [origin], []);
  }

  makeMove(m) {
    const b = this.board.slice();
    const p = b[m.from];
    b[m.from] = 0;
    for (const c of m.captured) b[c] = 0;
    b[m.to] = !Checker.isKing(p) && Square.rank(m.to) === Checker.kingRow(p > 0) ? Checker.crown(p) : p;
    const quiet = m.isCapture || !Checker.isKing(p) ? 0 : this.quietPlies + 1;
    return new CheckersPosition(b, !this.redToMove, quiet);
  }

  /** Hands the turn to the other side without moving (used by lessons where only one side plays). */
  passTurn() { return new CheckersPosition(this.board, !this.redToMove, this.quietPlies); }

  pieceCount(red) {
    let n = 0;
    for (const p of this.board) if (p !== 0 && (p > 0) === red) n++;
    return n;
  }
  count(piece) { let n = 0; for (const p of this.board) if (p === piece) n++; return n; }

  /** Pieces of the given side that the other side could jump if it were their turn. */
  piecesInDanger(red) {
    const asOpponent = this.redToMove === red ? this.passTurn() : this;
    return new Set(asOpponent.captureMoves().flatMap((m) => m.captured));
  }

  /** Identifies the position for repetition checks and lesson searches. */
  key() {
    let s = "";
    for (const p of this.board) s += p === 1 ? "r" : p === 2 ? "R" : p === -1 ? "b" : p === -2 ? "B" : ".";
    return s + (this.redToMove ? "r" : "b");
  }

  static start() {
    const b = new Int8Array(64);
    for (let sq = 0; sq < 64; sq++) {
      if (!isDark(sq)) continue;
      const rank = Square.rank(sq);
      if (rank <= 2) b[sq] = Checker.RED;
      else if (rank >= 5) b[sq] = Checker.BLACK;
    }
    return new CheckersPosition(b, true, 0);
  }

  /**
   * Builds a position from space-separated square names, e.g. setup({ red: "c3 e3", black: "d4" }).
   * Every square must be a dark square.
   */
  static setup({ red = "", black = "", redKings = "", blackKings = "", redToMove = true } = {}) {
    const b = new Int8Array(64);
    const place = (names, piece) => {
      for (const name of names.split(" ").filter((n) => n.trim())) {
        const sq = Square.parse(name);
        if (sq === NONE || !isDark(sq)) throw new Error(`${name} is not a dark square`);
        b[sq] = piece;
      }
    };
    place(red, Checker.RED);
    place(black, Checker.BLACK);
    place(redKings, Checker.RED_KING);
    place(blackKings, Checker.BLACK_KING);
    return new CheckersPosition(b, redToMove, 0);
  }
}

export const CheckersEndReason = Object.freeze({
  NO_PIECES: "NO_PIECES", NO_MOVES: "NO_MOVES", DRAW_QUIET: "DRAW_QUIET", DRAW_REPETITION: "DRAW_REPETITION",
});

/** 40 moves each with only kings moving and nothing captured. */
export const DRAW_PLIES = 80;

/** An immutable checkers game record. */
export class CheckersGame {
  constructor(start = CheckersPosition.start(), positions = null, moves = null) {
    this.positions = positions ?? [start];
    this.moves = moves ?? [];
    this._outcome = undefined;
  }

  get position() { return this.positions[this.positions.length - 1]; }
  get lastMove() { return this.moves.length ? this.moves[this.moves.length - 1] : null; }

  /** { redWins: boolean|null, reason } or null while the game goes on. */
  get outcome() {
    if (this._outcome === undefined) this._outcome = this.#computeOutcome();
    return this._outcome;
  }
  get isOver() { return this.outcome !== null; }

  play(m) { return new CheckersGame(null, [...this.positions, this.position.makeMove(m)], [...this.moves, m]); }

  undo(count = 1) {
    const n = Math.min(count, this.moves.length);
    if (n === 0) return this;
    return new CheckersGame(null, this.positions.slice(0, this.positions.length - n), this.moves.slice(0, this.moves.length - n));
  }

  #computeOutcome() {
    const pos = this.position;
    if (pos.legalMoves().length === 0) {
      const reason = pos.pieceCount(pos.redToMove) === 0 ? CheckersEndReason.NO_PIECES : CheckersEndReason.NO_MOVES;
      return { redWins: !pos.redToMove, reason };
    }
    if (pos.quietPlies >= DRAW_PLIES) return { redWins: null, reason: CheckersEndReason.DRAW_QUIET };
    const key = pos.key();
    if (this.positions.filter((p) => p.key() === key).length >= 3) return { redWins: null, reason: CheckersEndReason.DRAW_REPETITION };
    return null;
  }
}
