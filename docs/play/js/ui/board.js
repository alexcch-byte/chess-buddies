// The game board, shared by chess and checkers. Ported from the app's ChessBoard.kt, CheckersBoard.kt and BoardDrawing.kt.
// Tap a piece then a square, or drag. Everything is drawn as SVG on an 800 x 800 grid (cells of 100).
import { h, fromMarkup } from "./dom.js";
import { pieceUse, checkerUse, STAR_POINTS } from "./art.js";
import { Square, KING, ROOK, isCastle, isEnPassant } from "../chess/position.js";

export const THEMES = [
  { id: "meadow", label: "Meadow", light: "#F3E7C9", dark: "#7DB46C" },
  { id: "ocean", label: "Ocean", light: "#E6F1FB", dark: "#6A9FD4" },
  { id: "candy", label: "Candy", light: "#FDE7EF", dark: "#E08DB0" },
  { id: "wood", label: "Wood", light: "#F0D9B5", dark: "#B58863" },
];
export const themeById = (id) => THEMES.find((t) => t.id === id) ?? THEMES[0];

const CELL = 100;
const SELECT = "#FFE066";
const HINT = "#00B894";
const DANGER = "#FF4D4D";
const DOT = "rgba(0,0,0,0.2)";
const SLOP = 8; // pixels before a press becomes a drag
const CHESS_MS = 320;
const HOP_MS = 260;

const ease = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);

const topLeft = (sq, flipped) => {
  const col = flipped ? 7 - Square.file(sq) : Square.file(sq);
  const row = flipped ? Square.rank(sq) : 7 - Square.rank(sq);
  return [col * CELL, row * CELL];
};
const center = (sq, flipped) => {
  const [x, y] = topLeft(sq, flipped);
  return [x + CELL / 2, y + CELL / 2];
};
const between = (a, b) => Square.of((Square.file(a) + Square.file(b)) / 2, (Square.rank(a) + Square.rank(b)) / 2);

function arrowMarkup(squares, flipped) {
  if (squares.length < 2) return "";
  const pts = squares.map((s) => center(s, flipped));
  const head = CELL * 0.38;
  let out = "";
  for (let i = 0; i < pts.length - 1; i++) {
    const [ax, ay] = pts[i], [bx, by] = pts[i + 1];
    const last = i === pts.length - 2;
    const angle = Math.atan2(by - ay, bx - ax);
    const ex = last ? bx - Math.cos(angle) * head * 0.8 : bx;
    const ey = last ? by - Math.sin(angle) * head * 0.8 : by;
    out += `<line x1="${ax}" y1="${ay}" x2="${ex}" y2="${ey}" stroke="${HINT}" stroke-opacity="0.85" stroke-width="${CELL * 0.16}" stroke-linecap="round"/>`;
    if (last) {
      const rot = (x, y) => [bx + x * Math.cos(angle) - y * Math.sin(angle), by + x * Math.sin(angle) + y * Math.cos(angle)];
      const [p1x, p1y] = rot(-head, -head * 0.7);
      const [p2x, p2y] = rot(-head, head * 0.7);
      out += `<polygon points="${bx},${by} ${p1x},${p1y} ${p2x},${p2y}" fill="${HINT}" fill-opacity="0.85"/>`;
    }
  }
  return out;
}

/**
 * kind: "chess" or "checkers".
 * handlers: { onMove, onSelect, onIllegal }. Chess: onMove(from, to, dragged). Checkers: onMove(move, animate).
 */
export class Board {
  constructor(kind, handlers) {
    this.kind = kind;
    this.handlers = handlers;
    this.props = null;
    this.legal = [];
    this.legalFor = null;
    this.selected = null;   // chess: selected square
    this.path = [];         // checkers: squares chosen so far
    this.dragFrom = null;
    this.drag = null;       // { x, y } in board units
    this.animId = null;
    this.animating = false;
    this.raf = 0;
    this.press = null;

    this.svg = fromMarkup(
      `<svg class="board-svg" viewBox="0 0 800 800" role="img" aria-label="${kind === "chess" ? "Chess" : "Checkers"} board" style="touch-action:none"></svg>`);
    this.el = h("div", { class: "board" }, this.svg);
    this.svg.addEventListener("pointerdown", (e) => this.#down(e));
    this.svg.addEventListener("pointermove", (e) => this.#move(e));
    this.svg.addEventListener("pointerup", (e) => this.#up(e));
    this.svg.addEventListener("pointercancel", () => this.#cancel());
  }

  /** props: { position, theme, flipped, interactive, showLegalMoves, showCoordinates, decor, animation } */
  update(props) {
    const positionChanged = !this.props || this.props.position !== props.position;
    const interactiveChanged = !this.props || this.props.interactive !== props.interactive;
    this.props = props;
    if (positionChanged) this.legal = props.position.legalMoves();
    if (positionChanged || interactiveChanged) { this.selected = null; this.path = []; this.dragFrom = null; this.drag = null; }
    const anim = props.animation;
    if (anim && anim.id !== this.animId) {
      this.animId = anim.id;
      this.#startAnimation(anim);
    } else if (!anim) {
      this.animId = null;
      this.#stopAnimation();
    }
    this.#render();
  }

  // ----- geometry -----
  #squareAt(e) {
    const r = this.svg.getBoundingClientRect();
    const x = ((e.clientX - r.left) / r.width) * 800;
    const y = ((e.clientY - r.top) / r.height) * 800;
    if (x < 0 || y < 0 || x >= 800 || y >= 800) return { sq: null, x, y };
    const col = Math.floor(x / CELL), row = Math.floor(y / CELL);
    const flipped = this.props.flipped;
    const file = flipped ? 7 - col : col;
    const rank = flipped ? row : 7 - row;
    return { sq: Square.of(file, rank), x, y };
  }

  // ----- pointer handling -----
  #down(e) {
    if (!this.props || !this.props.interactive || this.animating) return;
    e.preventDefault();
    const { sq, x, y } = this.#squareAt(e);
    if (sq === null) return;
    try { this.svg.setPointerCapture(e.pointerId); } catch { /* not an active pointer (e.g. a synthetic event) */ }
    const pos = this.props.position;
    const piece = pos.get(sq);
    const own = piece !== 0 && (piece > 0) === (this.kind === "chess" ? pos.whiteToMove : pos.redToMove);
    this.press = { id: e.pointerId, sq, startX: e.clientX, startY: e.clientY, dragging: false, own, last: { sq, x, y } };

    if (this.kind === "chess") {
      const previous = this.selected;
      let castleTarget = null;
      if (previous !== null && own && Math.abs(pos.get(previous)) === KING && Math.abs(piece) === ROOK) {
        const m = this.legal.find((l) => l.from === previous && isCastle(l) && (l.to > previous) === (sq > previous));
        castleTarget = m ? m.to : null;
      }
      this.press.previous = previous;
      this.press.castleTarget = castleTarget;
      if (own && castleTarget === null) { this.selected = sq; this.handlers.onSelect?.(); this.#render(); }
    } else {
      const before = this.path;
      const midJump = before.length > 1;
      this.press.before = before;
      this.press.midJump = midJump;
      this.press.canMove = this.legal.some((m) => m.from === sq);
      if (own && !midJump) { this.path = [sq]; this.handlers.onSelect?.(); this.#render(); }
    }
  }

  #move(e) {
    const p = this.press;
    if (!p || e.pointerId !== p.id) return;
    const { sq, x, y } = this.#squareAt(e);
    p.last = { sq, x, y };
    const moved = Math.hypot(e.clientX - p.startX, e.clientY - p.startY);
    const canDrag = this.kind === "chess" ? p.own : p.own && !p.midJump && p.canMove;
    if (canDrag && !p.dragging && moved > SLOP) {
      p.dragging = true;
      this.dragFrom = p.sq;
      this.#render();
    }
    if (p.dragging) {
      e.preventDefault();
      this.drag = { x, y };
      this.#updateDragLayer();
    }
  }

  #up(e) {
    const p = this.press;
    if (!p || e.pointerId !== p.id) return;
    this.press = null;
    const target = p.last.sq;
    if (this.kind === "chess") this.#upChess(p, target);
    else this.#upCheckers(p, target);
  }

  #cancel() {
    this.press = null;
    this.dragFrom = null;
    this.drag = null;
    this.#render();
  }

  #upChess(p, target) {
    const attempt = (from, to, dragged) => {
      this.selected = null;
      this.dragFrom = null;
      this.drag = null;
      if (this.legal.some((m) => m.from === from && m.to === to)) this.handlers.onMove(from, to, dragged);
      else { this.#render(); this.handlers.onIllegal?.(from, to); }
    };
    if (p.dragging) {
      this.dragFrom = null; this.drag = null;
      if (target !== null && target !== p.sq) attempt(p.sq, target, true);
      else this.#render(); // dropped back where it started: stays selected
      return;
    }
    if (p.castleTarget !== null) attempt(p.previous, p.castleTarget, false);
    else if (p.own) { if (p.previous === p.sq) this.selected = null; this.#render(); }
    else if (p.previous !== null && p.previous !== undefined && target === p.sq) attempt(p.previous, p.sq, false);
    else { this.selected = null; this.#render(); }
  }

  #matching(path) {
    return this.legal.filter((m) => m.path.length > path.length && path.every((s, i) => m.path[i] === s));
  }

  #chooseTarget(target, dragged) {
    const current = this.path;
    if (current.length === 0) return;
    const candidates = this.#matching(current);
    const viaHop = candidates.filter((m) => m.path[current.length] === target);
    const animate = !dragged && current.length === 1;
    if (viaHop.length) {
      const next = [...current, target];
      const complete = viaHop.find((m) => m.path.length === next.length);
      if (complete) { this.path = []; this.handlers.onMove(complete, animate); }
      else { this.path = next; this.#render(); } // part way through a multi-jump
      return;
    }
    const toEnd = candidates.filter((m) => m.to === target).sort((a, b) => b.captured.length - a.captured.length)[0];
    if (toEnd) { this.path = []; this.handlers.onMove(toEnd, animate); return; }
    this.handlers.onIllegal?.(current[0], target);
    if (current.length === 1) this.path = [];
    this.#render();
  }

  #upCheckers(p, target) {
    if (p.dragging) {
      this.dragFrom = null; this.drag = null;
      if (target !== null && target !== p.sq) this.#chooseTarget(target, true);
      else this.#render(); // dropped back where it started: stays selected
      return;
    }
    if (p.own && !p.midJump) {
      if (p.before.length === 1 && p.before[0] === p.sq) { this.path = []; this.#render(); }
      else if (!p.canMove) { this.handlers.onIllegal?.(p.sq, p.sq); this.#render(); }
      return;
    }
    if (p.before.length > 0 && target === p.sq) { this.#chooseTarget(p.sq, false); return; }
    if (!p.midJump) { this.path = []; this.#render(); }
  }

  // ----- animation -----
  #startAnimation(anim) {
    this.#stopAnimation();
    this.animating = true;
    const hops = this.kind === "checkers" ? anim.move.path.length - 1 : 1;
    const duration = this.kind === "checkers" ? HOP_MS * hops : CHESS_MS;
    const begin = performance.now();
    this.animT = 0;
    const tick = (now) => {
      const raw = Math.min(1, (now - begin) / duration);
      this.animT = ease(raw);
      this.#updateAnimLayer();
      if (raw < 1) this.raf = requestAnimationFrame(tick);
      else { this.animating = false; this.raf = 0; this.#render(); }
    };
    this.raf = requestAnimationFrame(tick);
  }

  #stopAnimation() {
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
    this.animating = false;
  }

  /** Every chess piece that slides during the animation; castling moves the rook too. */
  #chessSlides(anim) {
    const m = anim.move;
    const list = [{ piece: anim.before.get(m.from), from: m.from, to: m.to }];
    if (isCastle(m)) {
      const [rf, rt] = m.to > m.from ? [m.from + 3, m.from + 1] : [m.from - 4, m.from - 1];
      list.push({ piece: anim.before.get(rf), from: rf, to: rt });
    }
    return list;
  }

  #pieceMarkup(piece, x, y, scale, opacity = 1) {
    const op = opacity !== 1 ? ` opacity="${opacity}"` : "";
    if (this.kind === "chess") return `<g transform="translate(${x} ${y}) scale(${scale})"${op}>${pieceUse(piece)}</g>`;
    return `<g transform="translate(${x} ${y}) scale(${scale})"${op}>${checkerUse(piece)}</g>`;
  }

  #animLayerMarkup() {
    const anim = this.props.animation;
    if (!anim || !this.animating) return "";
    const t = this.animT ?? 0;
    const flipped = this.props.flipped;
    let out = "";
    if (this.kind === "chess") {
      const captured = anim.before.get(anim.move.to);
      if (captured !== 0) { const [x, y] = topLeft(anim.move.to, flipped); out += this.#pieceMarkup(captured, x + 6, y + 3, 0.88, 1 - t); }
      if (isEnPassant(anim.move)) {
        const victim = anim.move.to + (anim.before.get(anim.move.from) > 0 ? -8 : 8);
        const [x, y] = topLeft(victim, flipped);
        out += this.#pieceMarkup(anim.before.get(victim), x + 6, y + 3, 0.88, 1 - t);
      }
      for (const s of this.#chessSlides(anim)) {
        const [ax, ay] = topLeft(s.from, flipped), [bx, by] = topLeft(s.to, flipped);
        out += this.#pieceMarkup(s.piece, ax + (bx - ax) * t + 6, ay + (by - ay) * t + 3, 0.88);
      }
    } else {
      const m = anim.move;
      const n = m.path.length - 1;
      const x = t * n;
      const seg = Math.min(Math.floor(x), n - 1);
      const local = x - seg;
      m.captured.forEach((c, i) => {
        const alpha = x < i + 0.5 ? 1 : Math.max(0, Math.min(1, 1 - (x - i - 0.5) * 2));
        if (alpha > 0) { const [cx, cy] = center(c, flipped); out += this.#pieceMarkup(anim.before.get(c), cx, cy, 1, alpha); }
      });
      const [fx, fy] = center(m.path[seg], flipped), [tx, ty] = center(m.path[seg + 1], flipped);
      const lift = m.isCapture ? Math.sin(local * Math.PI) * CELL * 0.3 : 0;
      out += this.#pieceMarkup(anim.before.get(m.from), fx + (tx - fx) * local, fy + (ty - fy) * local - lift, 1 + (lift / CELL) * 0.4);
    }
    return out;
  }

  #updateAnimLayer() {
    const layer = this.svg.querySelector("#anim");
    if (layer) layer.innerHTML = this.#animLayerMarkup();
  }

  #dragMarkup() {
    if (this.dragFrom === null || !this.drag) return "";
    const piece = this.props.position.get(this.dragFrom);
    if (this.kind === "chess") {
      return `<g transform="translate(${this.drag.x - 60} ${this.drag.y - 102}) scale(1.2)">${pieceUse(piece)}</g>`;
    }
    return `<g transform="translate(${this.drag.x} ${this.drag.y - CELL * 0.35}) scale(1.2)">${checkerUse(piece)}</g>`;
  }

  #updateDragLayer() {
    const layer = this.svg.querySelector("#drag");
    if (layer) layer.innerHTML = this.#dragMarkup();
  }

  // ----- drawing -----
  #render() {
    const { position, flipped, showLegalMoves, showCoordinates } = this.props;
    const theme = themeById(this.props.theme);
    const decor = this.props.decor ?? {};
    const chess = this.kind === "chess";
    const rect = (sq, fill, op) => { const [x, y] = topLeft(sq, flipped); return `<rect x="${x}" y="${y}" width="${CELL}" height="${CELL}" fill="${fill}" fill-opacity="${op}"/>`; };

    let s = `<defs><radialGradient id="check-glow"><stop offset="0" stop-color="${DANGER}" stop-opacity="0.95"/><stop offset="1" stop-color="${DANGER}" stop-opacity="0"/></radialGradient></defs>`;
    s += `<rect width="800" height="800" fill="${theme.light}"/>`;
    for (let sq = 0; sq < 64; sq++) if (!Square.isLight(sq)) s += rect(sq, theme.dark, 1);

    // Last move, hint, wrong answer and selection highlights.
    if (decor.lastMove) {
      if (chess) { s += rect(decor.lastMove.from, SELECT, 0.38); s += rect(decor.lastMove.to, SELECT, 0.5); }
      else decor.lastMove.path.forEach((q, i) => { s += rect(q, SELECT, i === 0 ? 0.38 : 0.5); });
    }
    if (decor.hint) {
      const squares = chess ? [decor.hint.from, decor.hint.to] : decor.hint.path;
      s += `<g class="hint-pulse">${squares.map((q) => rect(q, HINT, 0.55)).join("")}</g>`;
    }
    if (decor.wrongSquare !== null && decor.wrongSquare !== undefined) s += rect(decor.wrongSquare, DANGER, 0.45);
    let focus = null;
    if (chess) {
      focus = this.selected ?? this.dragFrom;
      if (focus !== null) s += rect(focus, SELECT, 0.75);
    } else {
      const selectedPath = this.dragFrom !== null ? [this.dragFrom] : this.path;
      selectedPath.forEach((q, i) => { s += rect(q, SELECT, i === selectedPath.length - 1 ? 0.75 : 0.4); });
    }

    // King in check glows red.
    if (chess && position.inCheck() && !this.animating) {
      const [cx, cy] = center(position.kingSquare(position.whiteToMove), flipped);
      s += `<circle cx="${cx}" cy="${cy}" r="72" fill="url(#check-glow)"/>`;
    }

    if (showCoordinates) {
      for (let i = 0; i < 8; i++) {
        const rank = flipped ? i : 7 - i;
        const leftSq = Square.of(flipped ? 7 : 0, rank);
        s += `<text x="5" y="${i * CELL + 19}" font-size="17" font-weight="700" fill="${Square.isLight(leftSq) ? theme.dark : theme.light}">${rank + 1}</text>`;
        const file = flipped ? 7 - i : i;
        const bottomSq = Square.of(file, flipped ? 7 : 0);
        s += `<text x="${(i + 1) * CELL - 5}" y="${8 * CELL - 5}" text-anchor="end" font-size="17" font-weight="700" fill="${Square.isLight(bottomSq) ? theme.dark : theme.light}">${String.fromCharCode(97 + file)}</text>`;
      }
    }

    for (const q of decor.stars ?? []) {
      const [cx, cy] = center(q, flipped);
      s += `<g transform="translate(${cx} ${cy})"><g class="star-pulse"><polygon points="${STAR_POINTS}" transform="scale(36)" fill="#FFC93C" stroke="#D98A00" stroke-width="0.111" stroke-linejoin="round"/></g></g>`;
    }
    // Danger rings go under the pieces so the piece stays easy to see.
    for (const q of decor.danger ?? []) {
      const [cx, cy] = center(q, flipped);
      s += `<g class="danger-pulse"><circle cx="${cx}" cy="${cy}" r="46" fill="${DANGER}" fill-opacity="0.16"/><circle cx="${cx}" cy="${cy}" r="45" fill="none" stroke="${DANGER}" stroke-width="3.5"/></g>`;
    }

    // Pieces. While a move animates, the moving pieces are drawn on a separate layer.
    const anim = this.props.animation;
    const animating = this.animating && anim;
    const hidden = new Set();
    if (animating) {
      if (chess) this.#chessSlides(anim).forEach((m) => hidden.add(m.to));
      else hidden.add(anim.move.to);
    }
    if (chess) {
      for (let q = 0; q < 64; q++) {
        const p = position.get(q);
        if (p === 0 || hidden.has(q) || q === this.dragFrom) continue;
        const [x, y] = topLeft(q, flipped);
        s += this.#pieceMarkup(p, x + 6, y + 3, 0.88);
      }
    } else {
      const jumpedSoFar = new Set();
      for (let i = 0; i < this.path.length - 1; i++) jumpedSoFar.add(between(this.path[i], this.path[i + 1]));
      for (let q = 0; q < 64; q++) {
        const p = position.get(q);
        if (p === 0 || q === this.dragFrom || hidden.has(q)) continue;
        if (this.path.length > 1 && q === this.path[0]) continue;
        const [cx, cy] = center(q, flipped);
        s += this.#pieceMarkup(p, cx, cy, 1, jumpedSoFar.has(q) ? 0.35 : 1);
      }
      if (this.path.length > 1) {
        const [cx, cy] = center(this.path[this.path.length - 1], flipped);
        s += this.#pieceMarkup(position.get(this.path[0]), cx, cy, 1);
      }
    }

    // Where the selected piece can go next.
    if (showLegalMoves) {
      let targets = [];
      if (chess) {
        const from = focus ?? decor.previewSquare ?? null;
        if (from !== null) {
          targets = focus !== null
            ? this.legal.filter((m) => m.from === from).map((m) => m.to)
            : this.#previewTargets(position, from);
        }
        for (const q of new Set(targets)) s += this.#marker(q, position.get(q) !== 0, flipped);
      } else {
        const dotsPath = this.path.length ? this.path : decor.previewSquare !== null && decor.previewSquare !== undefined ? [decor.previewSquare] : [];
        if (dotsPath.length) {
          const moves = this.path.length ? this.#matching(dotsPath) : this.#previewMoves(position, dotsPath[0]);
          for (const q of new Set(moves.map((m) => m.path[dotsPath.length]))) s += this.#marker(q, false, flipped);
        }
      }
    }

    if (decor.hint) s += arrowMarkup(chess ? [decor.hint.from, decor.hint.to] : decor.hint.path, flipped);

    s += `<g id="anim">${animating ? this.#animLayerMarkup() : ""}</g>`;
    s += `<g id="drag">${this.#dragMarkup()}</g>`;
    this.svg.innerHTML = s;
  }

  #marker(sq, occupied, flipped) {
    const [cx, cy] = center(sq, flipped);
    return occupied
      ? `<circle cx="${cx}" cy="${cy}" r="46" fill="none" stroke="${DOT}" stroke-width="8"/>`
      : `<circle cx="${cx}" cy="${cy}" r="16" fill="${DOT}"/>`;
  }

  /** Targets for a piece even if it isn't that side's turn (used to demonstrate how a piece moves). */
  #previewTargets(position, sq) {
    const p = position.get(sq);
    if (p === 0) return [];
    const pos = (p > 0) === position.whiteToMove ? position : position.passTurn();
    return pos.legalMoves().filter((m) => m.from === sq).map((m) => m.to);
  }

  #previewMoves(position, sq) {
    const p = position.get(sq);
    if (p === 0) return [];
    const pos = (p > 0) === position.redToMove ? position : position.passTurn();
    return pos.legalMoves().filter((m) => m.from === sq);
  }

  destroy() {
    this.#stopAnimation();
  }
}
