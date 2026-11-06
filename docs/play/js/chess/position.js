// Chess rules. A direct port of the Android app's engine/Position.kt, so the bots and lessons behave the same.
// Pieces are signed ints: positive White, negative Black, 0 empty. |value| is the piece type.

export const EMPTY = 0, PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;

export const Piece = {
  type: (p) => Math.abs(p),
  isWhite: (p) => p > 0,
  isBlack: (p) => p < 0,
  make: (type, white) => (white ? type : -type),
  /** Classic kid-friendly point values (pawn = 1). */
  points(type) {
    switch (type) {
      case PAWN: return 1;
      case KNIGHT: case BISHOP: return 3;
      case ROOK: return 5;
      case QUEEN: return 9;
      default: return 0;
    }
  },
  name(type) {
    return ["", "Pawn", "Knight", "Bishop", "Rook", "Queen", "King"][type] || "";
  },
  letter(type) {
    return ["?", "P", "N", "B", "R", "Q", "K"][type] || "?";
  },
  fromLetter(c) {
    const type = { P: PAWN, N: KNIGHT, B: BISHOP, R: ROOK, Q: QUEEN, K: KING }[c.toUpperCase()];
    if (!type) return EMPTY;
    return Piece.make(type, c === c.toUpperCase());
  },
};

/** Squares are 0..63 with a1 = 0, h1 = 7, a8 = 56, h8 = 63. */
export const NONE = -1;
export const Square = {
  of: (file, rank) => rank * 8 + file,
  file: (sq) => sq & 7,
  rank: (sq) => sq >> 3,
  onBoard: (file, rank) => file >= 0 && file <= 7 && rank >= 0 && rank <= 7,
  isLight: (sq) => (Square.file(sq) + Square.rank(sq)) % 2 === 1,
  name: (sq) => String.fromCharCode(97 + Square.file(sq)) + (Square.rank(sq) + 1),
  parse(name) {
    if (name.length !== 2) return NONE;
    const f = name.charCodeAt(0) - 97;
    const r = name.charCodeAt(1) - 49;
    return Square.onBoard(f, r) ? Square.of(f, r) : NONE;
  },
};

export const CAPTURE = 1, EN_PASSANT = 2, CASTLE = 4, DOUBLE_PUSH = 8;

export function move(from, to, promotion = 0, flags = 0) {
  return { from, to, promotion, flags };
}
export const isCapture = (m) => (m.flags & CAPTURE) !== 0;
export const isEnPassant = (m) => (m.flags & EN_PASSANT) !== 0;
export const isCastle = (m) => (m.flags & CASTLE) !== 0;
export const isDoublePush = (m) => (m.flags & DOUBLE_PUSH) !== 0;
export function uci(m) {
  const promo = m.promotion ? Piece.letter(m.promotion).toLowerCase() : "";
  return Square.name(m.from) + Square.name(m.to) + promo;
}

export const WHITE_KINGSIDE = 1, WHITE_QUEENSIDE = 2, BLACK_KINGSIDE = 4, BLACK_QUEENSIDE = 8;
export const START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

const PROMOTION_TYPES = [QUEEN, ROOK, BISHOP, KNIGHT];

// Directions 0..3 are straight (rook), 4..7 are diagonal (bishop).
const DIRECTIONS = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]];

const RAYS = DIRECTIONS.map(([df, dr]) => {
  const perSquare = [];
  for (let sq = 0; sq < 64; sq++) {
    const out = [];
    let f = Square.file(sq) + df, r = Square.rank(sq) + dr;
    while (Square.onBoard(f, r)) {
      out.push(Square.of(f, r));
      f += df; r += dr;
    }
    perSquare.push(out);
  }
  return perSquare;
});

function stepTargets(offsets) {
  const out = [];
  for (let sq = 0; sq < 64; sq++) {
    const list = [];
    for (const [df, dr] of offsets) {
      const f = Square.file(sq) + df, r = Square.rank(sq) + dr;
      if (Square.onBoard(f, r)) list.push(Square.of(f, r));
    }
    out.push(list);
  }
  return out;
}

const KNIGHT_TARGETS = stepTargets([[1, 2], [2, 1], [2, -1], [1, -2], [-1, -2], [-2, -1], [-2, 1], [-1, 2]]);
const KING_TARGETS = stepTargets([[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]]);

const CASTLE_MASK = new Array(64).fill(15);
CASTLE_MASK[4] = 15 & ~(WHITE_KINGSIDE | WHITE_QUEENSIDE);
CASTLE_MASK[0] = 15 & ~WHITE_QUEENSIDE;
CASTLE_MASK[7] = 15 & ~WHITE_KINGSIDE;
CASTLE_MASK[60] = 15 & ~(BLACK_KINGSIDE | BLACK_QUEENSIDE);
CASTLE_MASK[56] = 15 & ~BLACK_QUEENSIDE;
CASTLE_MASK[63] = 15 & ~BLACK_KINGSIDE;

/**
 * An immutable chess position. makeMove returns a new Position.
 * Positions don't need kings: lesson boards often hold a single piece. Without a king,
 * moves for that side are never filtered for check.
 */
export class Position {
  constructor(board, whiteToMove, castling, epSquare, halfmoveClock, fullmoveNumber) {
    this.board = board;
    this.whiteToMove = whiteToMove;
    this.castling = castling;
    this.epSquare = epSquare;
    this.halfmoveClock = halfmoveClock;
    this.fullmoveNumber = fullmoveNumber;
  }

  get(sq) { return this.board[sq]; }

  kingSquare(white) {
    const king = Piece.make(KING, white);
    for (let sq = 0; sq < 64; sq++) if (this.board[sq] === king) return sq;
    return NONE;
  }

  inCheck(white = this.whiteToMove) {
    const k = this.kingSquare(white);
    return k !== NONE && this.isAttacked(k, !white);
  }

  /** True if any piece of the given color attacks sq. */
  isAttacked(sq, byWhite) {
    return this.attackers(sq, byWhite, true).length > 0;
  }

  /** Squares holding pieces of the given color that attack sq. */
  attackers(sq, byWhite, stopAtFirst = false) {
    const board = this.board;
    const result = [];
    const sign = byWhite ? 1 : -1;
    const f = Square.file(sq);
    const pawnRank = Square.rank(sq) + (byWhite ? -1 : 1);
    if (pawnRank >= 0 && pawnRank <= 7) {
      if (f > 0 && board[Square.of(f - 1, pawnRank)] === sign * PAWN) { result.push(Square.of(f - 1, pawnRank)); if (stopAtFirst) return result; }
      if (f < 7 && board[Square.of(f + 1, pawnRank)] === sign * PAWN) { result.push(Square.of(f + 1, pawnRank)); if (stopAtFirst) return result; }
    }
    for (const t of KNIGHT_TARGETS[sq]) if (board[t] === sign * KNIGHT) { result.push(t); if (stopAtFirst) return result; }
    for (const t of KING_TARGETS[sq]) if (board[t] === sign * KING) { result.push(t); if (stopAtFirst) return result; }
    for (let d = 0; d < 8; d++) {
      const straight = d < 4;
      for (const t of RAYS[d][sq]) {
        const p = board[t];
        if (p === 0) continue;
        if (p === sign * QUEEN || p === sign * (straight ? ROOK : BISHOP)) {
          result.push(t);
          if (stopAtFirst) return result;
        }
        break;
      }
    }
    return result;
  }

  /** Squares the piece on sq attacks (or would capture on), regardless of whose turn it is. */
  attackedBy(sq) {
    const board = this.board;
    const p = board[sq];
    if (p === 0) return [];
    const result = [];
    const f = Square.file(sq), r = Square.rank(sq);
    switch (Math.abs(p)) {
      case PAWN: {
        const tr = r + (p > 0 ? 1 : -1);
        if (tr >= 0 && tr <= 7) {
          if (f > 0) result.push(Square.of(f - 1, tr));
          if (f < 7) result.push(Square.of(f + 1, tr));
        }
        break;
      }
      case KNIGHT: result.push(...KNIGHT_TARGETS[sq]); break;
      case KING: result.push(...KING_TARGETS[sq]); break;
      default: {
        const t = Math.abs(p);
        const from = t === BISHOP ? 4 : 0;
        const to = t === ROOK ? 4 : 8;
        for (let d = from; d < to; d++) {
          for (const s of RAYS[d][sq]) {
            result.push(s);
            if (board[s] !== 0) break;
          }
        }
      }
    }
    return result;
  }

  pseudoLegalMoves(capturesOnly = false) {
    const moves = [];
    const white = this.whiteToMove;
    for (let sq = 0; sq < 64; sq++) {
      const p = this.board[sq];
      if (p === 0 || (p > 0) !== white) continue;
      switch (Math.abs(p)) {
        case PAWN: this.#genPawn(sq, white, capturesOnly, moves); break;
        case KNIGHT: this.#genSteps(sq, white, KNIGHT_TARGETS[sq], capturesOnly, moves); break;
        case BISHOP: this.#genSlides(sq, white, 4, 8, capturesOnly, moves); break;
        case ROOK: this.#genSlides(sq, white, 0, 4, capturesOnly, moves); break;
        case QUEEN: this.#genSlides(sq, white, 0, 8, capturesOnly, moves); break;
        case KING:
          this.#genSteps(sq, white, KING_TARGETS[sq], capturesOnly, moves);
          if (!capturesOnly) this.#genCastles(sq, white, moves);
          break;
      }
    }
    return moves;
  }

  #genPawn(sq, white, capturesOnly, moves) {
    const board = this.board;
    const r = Square.rank(sq);
    const lastRank = white ? 7 : 0;
    if (r === lastRank) return;
    const dir = white ? 8 : -8;
    const startRank = white ? 1 : 6;
    const one = sq + dir;
    const promoting = Square.rank(one) === lastRank;
    if (board[one] === 0) {
      if (promoting) {
        for (const type of PROMOTION_TYPES) moves.push(move(sq, one, type, 0));
      } else if (!capturesOnly) {
        moves.push(move(sq, one));
        if (r === startRank && board[one + dir] === 0) moves.push(move(sq, one + dir, 0, DOUBLE_PUSH));
      }
    }
    const f = Square.file(sq);
    for (const df of [-1, 1]) {
      if (f + df < 0 || f + df > 7) continue;
      const t = one + df;
      const q = board[t];
      if (q !== 0 && (q > 0) !== white) {
        if (promoting) for (const type of PROMOTION_TYPES) moves.push(move(sq, t, type, CAPTURE));
        else moves.push(move(sq, t, 0, CAPTURE));
      } else if (q === 0 && t === this.epSquare) {
        moves.push(move(sq, t, 0, CAPTURE | EN_PASSANT));
      }
    }
  }

  #genSteps(sq, white, targets, capturesOnly, moves) {
    for (const t of targets) {
      const q = this.board[t];
      if (q === 0) {
        if (!capturesOnly) moves.push(move(sq, t));
      } else if ((q > 0) !== white) {
        moves.push(move(sq, t, 0, CAPTURE));
      }
    }
  }

  #genSlides(sq, white, dirFrom, dirTo, capturesOnly, moves) {
    for (let d = dirFrom; d < dirTo; d++) {
      for (const t of RAYS[d][sq]) {
        const q = this.board[t];
        if (q === 0) {
          if (!capturesOnly) moves.push(move(sq, t));
          continue;
        }
        if ((q > 0) !== white) moves.push(move(sq, t, 0, CAPTURE));
        break;
      }
    }
  }

  #genCastles(sq, white, moves) {
    const board = this.board;
    const home = white ? 4 : 60;
    if (sq !== home) return;
    const rook = Piece.make(ROOK, white);
    const kingside = white ? WHITE_KINGSIDE : BLACK_KINGSIDE;
    const queenside = white ? WHITE_QUEENSIDE : BLACK_QUEENSIDE;
    const enemy = !white;
    if ((this.castling & kingside) !== 0 &&
        board[home + 1] === 0 && board[home + 2] === 0 && board[home + 3] === rook &&
        !this.isAttacked(home, enemy) && !this.isAttacked(home + 1, enemy) && !this.isAttacked(home + 2, enemy)) {
      moves.push(move(home, home + 2, 0, CASTLE));
    }
    if ((this.castling & queenside) !== 0 &&
        board[home - 1] === 0 && board[home - 2] === 0 && board[home - 3] === 0 && board[home - 4] === rook &&
        !this.isAttacked(home, enemy) && !this.isAttacked(home - 1, enemy) && !this.isAttacked(home - 2, enemy)) {
      moves.push(move(home, home - 2, 0, CASTLE));
    }
  }

  /** True if the side that just moved (i.e. not whiteToMove) left its own king attacked. */
  moverLeftKingInCheck() { return this.inCheck(!this.whiteToMove); }

  isLegal(m) { return !this.makeMove(m).moverLeftKingInCheck(); }

  legalMoves() {
    const pseudo = this.pseudoLegalMoves();
    if (this.kingSquare(this.whiteToMove) === NONE) return pseudo;
    return pseudo.filter((m) => this.isLegal(m));
  }

  hasLegalMove() {
    const pseudo = this.pseudoLegalMoves();
    if (this.kingSquare(this.whiteToMove) === NONE) return pseudo.length > 0;
    return pseudo.some((m) => this.isLegal(m));
  }

  isCheckmate() { return this.inCheck() && !this.hasLegalMove(); }
  isStalemate() { return !this.inCheck() && !this.hasLegalMove(); }

  /** Finds the legal move matching a UCI string such as "e2e4" or "e7e8q". */
  findMove(u) {
    const from = Square.parse(u.slice(0, 2));
    const to = Square.parse(u.slice(2, 4));
    const promo = u.length > 4 ? Math.abs(Piece.fromLetter(u[4])) : 0;
    return this.legalMoves().find((m) => m.from === from && m.to === to && m.promotion === promo) || null;
  }

  makeMove(m) {
    const b = this.board.slice();
    const p = b[m.from];
    const white = p > 0;
    const captured = b[m.to];
    b[m.to] = m.promotion !== 0 ? Piece.make(m.promotion, white) : p;
    b[m.from] = 0;
    if (isEnPassant(m)) b[m.to + (white ? -8 : 8)] = 0;
    if (isCastle(m)) {
      if (m.to > m.from) { b[m.from + 1] = b[m.from + 3]; b[m.from + 3] = 0; }
      else { b[m.from - 1] = b[m.from - 4]; b[m.from - 4] = 0; }
    }
    const newCastling = this.castling & CASTLE_MASK[m.from] & CASTLE_MASK[m.to];
    const newEp = isDoublePush(m) ? (m.from + m.to) / 2 : NONE;
    const newHalf = Math.abs(p) === PAWN || captured !== 0 ? 0 : this.halfmoveClock + 1;
    const newFull = !this.whiteToMove ? this.fullmoveNumber + 1 : this.fullmoveNumber;
    return new Position(b, !this.whiteToMove, newCastling, newEp, newHalf, newFull);
  }

  /** Hands the turn to the other side without moving (used by lessons where only one side plays). */
  passTurn() {
    return new Position(this.board, !this.whiteToMove, this.castling, NONE, this.halfmoveClock, this.fullmoveNumber);
  }

  count(piece) { let n = 0; for (const p of this.board) if (p === piece) n++; return n; }

  /** Number of non-king pieces the given side has. */
  pieceCount(white) {
    let n = 0;
    for (const p of this.board) if (p !== 0 && (p > 0) === white && Math.abs(p) !== KING) n++;
    return n;
  }

  /** Total kid-style points (pawn = 1 ... queen = 9) for one side. */
  materialPoints(white) {
    let n = 0;
    for (const p of this.board) if (p !== 0 && (p > 0) === white) n += Piece.points(Math.abs(p));
    return n;
  }

  squaresOf(piece) {
    const out = [];
    for (let sq = 0; sq < 64; sq++) if (this.board[sq] === piece) out.push(sq);
    return out;
  }

  isInsufficientMaterial() {
    let minors = 0, lightBishops = 0, darkBishops = 0, knights = 0;
    for (let sq = 0; sq < 64; sq++) {
      switch (Math.abs(this.board[sq])) {
        case PAWN: case ROOK: case QUEEN: return false;
        case KNIGHT: minors++; knights++; break;
        case BISHOP:
          minors++;
          if (Square.isLight(sq)) lightBishops++; else darkBishops++;
          break;
      }
    }
    if (minors <= 1) return true;
    // Only bishops, all on the same color: no mate is possible.
    return knights === 0 && (lightBishops === 0 || darkBishops === 0);
  }

  /** Identifies the position for threefold-repetition checks. */
  repetitionKey() { return this.toFen().split(" ").slice(0, 4).join(" "); }

  toFen() {
    let s = "";
    for (let rank = 7; rank >= 0; rank--) {
      let empty = 0;
      for (let file = 0; file < 8; file++) {
        const p = this.board[Square.of(file, rank)];
        if (p === 0) { empty++; continue; }
        if (empty > 0) { s += empty; empty = 0; }
        const c = Piece.letter(Math.abs(p));
        s += p > 0 ? c : c.toLowerCase();
      }
      if (empty > 0) s += empty;
      if (rank > 0) s += "/";
    }
    s += this.whiteToMove ? " w " : " b ";
    let c = "";
    if (this.castling & WHITE_KINGSIDE) c += "K";
    if (this.castling & WHITE_QUEENSIDE) c += "Q";
    if (this.castling & BLACK_KINGSIDE) c += "k";
    if (this.castling & BLACK_QUEENSIDE) c += "q";
    s += c || "-";
    s += " " + (this.epSquare === NONE ? "-" : Square.name(this.epSquare));
    s += " " + this.halfmoveClock + " " + this.fullmoveNumber;
    return s;
  }

  toString() { return this.toFen(); }

  static start() { return Position.fromFen(START_FEN); }

  static fromFen(fen) {
    const parts = fen.trim().split(/\s+/);
    const board = new Int8Array(64);
    const ranks = parts[0].split("/");
    if (ranks.length !== 8) throw new Error("FEN needs 8 ranks: " + fen);
    ranks.forEach((row, i) => {
      const rank = 7 - i;
      let file = 0;
      for (const c of row) {
        if (c >= "0" && c <= "9") {
          file += Number(c);
        } else {
          if (file >= 8) throw new Error("Too many squares in rank: " + fen);
          board[Square.of(file, rank)] = Piece.fromLetter(c);
          file++;
        }
      }
      if (file !== 8) throw new Error(`Bad rank '${row}' in FEN: ${fen}`);
    });
    const white = (parts[1] ?? "w") === "w";
    const c = parts[2] ?? "-";
    let castling = 0;
    if (c.includes("K")) castling |= WHITE_KINGSIDE;
    if (c.includes("Q")) castling |= WHITE_QUEENSIDE;
    if (c.includes("k")) castling |= BLACK_KINGSIDE;
    if (c.includes("q")) castling |= BLACK_QUEENSIDE;
    const epPart = parts[3] ?? "-";
    const ep = epPart === "-" ? NONE : Square.parse(epPart);
    const half = parseInt(parts[4] ?? "0", 10) || 0;
    const full = parseInt(parts[5] ?? "1", 10) || 1;
    return new Position(board, white, castling, ep, half, full);
  }
}
