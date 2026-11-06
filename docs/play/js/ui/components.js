// Reusable pieces of UI: buttons, cards, the coach bubble, dialogs, confetti. Ported from Common.kt and CartoonCharacters.kt.
import { h } from "./dom.js";
import { sirHops, starShape, pieceIcon } from "./art.js";
import { QUEEN, ROOK, BISHOP, KNIGHT, Piece } from "../chess/position.js";

/** A chunky, colorful button with a 3D-ish bottom edge. color: purple|green|orange|blue|red|gold|grey. */
export function kidButton({ text, onClick, color = "purple", emoji = null, enabled = true, small = false, wide = false }) {
  const b = h("button", {
    class: `btn ${color}${small ? " small" : ""}${wide ? " wide" : ""}`,
    type: "button",
    disabled: !enabled,
    onclick: (e) => { e.stopPropagation(); if (enabled) onClick(); },
  },
  emoji ? h("span", { class: "btn-emoji", "aria-hidden": "true" }, emoji) : null,
  h("span", null, text));
  return b;
}

/** Round icon button used in top bars. */
export function roundIconButton(emoji, onClick, label) {
  return h("button", { class: "round-btn", type: "button", "aria-label": label, onclick: onClick }, emoji);
}

export function topBar(title, onBack, trailing = null) {
  return h("header", { class: "topbar" },
    roundIconButton("⬅️", onBack, "Back"),
    h("h1", null, title),
    trailing);
}

export const card = (children, { color = null, cls = "" } = {}) =>
  h("div", { class: `card ${cls}`, style: color ? { background: color } : null }, children);

/** A pill showing a count, such as stars or trophies. */
export const countPill = (emoji, text) => h("div", { class: "pill" }, h("span", { class: "pill-emoji" }, emoji), h("span", null, text));

export const sectionLabel = (text) => h("div", { class: "section-label" }, text);

/** Small rotated badge ribbon, e.g. "Next!". */
export const badge = (text, color) => h("div", { class: `badge ${color}` }, text);

/** Row of stars, `filled` of them gold. */
export function starRow(filled, total = 3, cls = "star") {
  return h("div", { class: "star-row" }, Array.from({ length: total }, (_, i) => starShape(i < filled, cls)));
}

/**
 * Sir Hops with a speech bubble. Returns { el, set(text, mood) } so the text can change in place
 * (and fade in only when it really changed).
 */
export function createCoach({ compact = false } = {}) {
  const avatarBox = h("div", { class: "coach-avatar" });
  const moodEmoji = h("span", { class: "coach-mood" });
  const bubble = h("div", { class: "coach-bubble" });
  const el = h("div", { class: `coach${compact ? " compact" : ""}`, "aria-live": "polite" },
    h("div", { class: "coach-circle" }, avatarBox, moodEmoji),
    h("div", { class: "coach-tail" }),
    bubble);
  let lastText = null, lastMood = null;
  return {
    el,
    set(text, mood = "normal") {
      if (mood !== lastMood) {
        lastMood = mood;
        el.dataset.mood = mood;
        avatarBox.replaceChildren(sirHops({ happy: mood === "happy" }));
        moodEmoji.textContent = mood === "happy" ? "🎉" : mood === "oops" ? "😮" : "";
      }
      if (text !== lastText) {
        lastText = text;
        bubble.textContent = text;
        bubble.classList.remove("fade-in");
        void bubble.offsetWidth; // restart the animation
        bubble.classList.add("fade-in");
      }
    },
  };
}

// ---------- Dialogs ----------

/** Shows content in a centered card over a dimmed backdrop. Returns a close() function. */
export function openDialog(content, { onDismiss = null } = {}) {
  const overlay = h("div", { class: "overlay", role: "dialog", "aria-modal": "true" }, h("div", { class: "dialog card" }, content));
  const close = () => overlay.remove();
  overlay.addEventListener("pointerdown", (e) => { if (e.target === overlay) { close(); onDismiss?.(); } });
  document.body.append(overlay);
  return close;
}

export function confirmDialog({ title, message, confirmText, onConfirm, dismissText = "Cancel" }) {
  let close;
  const body = h("div", { class: "dialog-body" },
    h("h2", null, title),
    h("p", null, message),
    h("div", { class: "row" },
      kidButton({ text: dismissText, color: "grey", small: true, onClick: () => close() }),
      kidButton({ text: confirmText, color: "red", small: true, onClick: () => { close(); onConfirm(); } })));
  close = openDialog(body);
  return close;
}

/** Lets the child pick what a pawn turns into. Returns close(). */
export function promotionDialog({ white, onPick, onDismiss }) {
  let close;
  const choices = [QUEEN, ROOK, BISHOP, KNIGHT].map((type) =>
    h("button", {
      class: `promo-choice${type === QUEEN ? " queen" : ""}`, type: "button",
      onclick: () => { close(); onPick(type); },
    }, pieceIcon(Piece.make(type, white), "icon big"), h("span", null, Piece.name(type))));
  close = openDialog(h("div", { class: "dialog-body" },
    h("h2", null, "Your pawn made it! 🎉"),
    h("p", null, "What should it become?"),
    h("div", { class: "row" }, choices)), { onDismiss });
  return close;
}

// ---------- Celebration ----------

const CONFETTI_COLORS = ["#6C5CE7", "#00B894", "#FF9F43", "#FD79A8", "#0984E3", "#FFC93C"];

/** A burst of falling confetti that plays once. */
export function confetti() {
  const canvas = h("canvas", { class: "confetti", "aria-hidden": "true" });
  document.body.append(canvas);
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const w = (canvas.width = Math.floor(window.innerWidth * dpr));
  const hgt = (canvas.height = Math.floor(window.innerHeight * dpr));
  const ctx = canvas.getContext("2d");
  const bits = Array.from({ length: 90 }, (_, i) => ({
    x: Math.random(), delay: Math.random() * 0.35, speed: 0.7 + Math.random() * 0.6,
    drift: (Math.random() - 0.5) * 0.25, spin: (Math.random() - 0.5) * 900,
    color: CONFETTI_COLORS[i % CONFETTI_COLORS.length], wide: Math.random() < 0.5,
  }));
  const bit = Math.max(6, Math.min(w, hgt) * 0.018);
  const begin = performance.now();
  const frame = (now) => {
    const p = Math.min(1, (now - begin) / 3200);
    ctx.clearRect(0, 0, w, hgt);
    for (const c of bits) {
      const local = Math.max(0, Math.min(1, (p - c.delay) / (1 - c.delay)));
      if (local <= 0) continue;
      const x = (c.x + c.drift * local) * w;
      const y = -bit * 2 + local * c.speed * (hgt + bit * 4);
      ctx.save();
      ctx.translate(x, y);
      ctx.rotate((c.spin * local * Math.PI) / 180);
      ctx.globalAlpha = 1 - local * 0.3;
      ctx.fillStyle = c.color;
      const sw = c.wide ? bit * 1.6 : bit, sh = c.wide ? bit * 0.8 : bit;
      ctx.fillRect(-sw / 2, -sh / 2, sw, sh);
      ctx.restore();
    }
    if (p < 1) requestAnimationFrame(frame); else canvas.remove();
  };
  requestAnimationFrame(frame);
}

// ---------- Layout ----------

/**
 * Sizes the board the way the app does: beside the side panel on wide screens, above it on narrow ones.
 * Sets --board on `root` and toggles .wide / .narrow. Returns a function that stops watching.
 */
export function fitBoard(root, { narrowHeightRatio, onChange = null }) {
  let lastWide = null;
  const apply = () => {
    const w = root.clientWidth, hgt = root.clientHeight;
    if (!w || !hgt) return;
    const wide = w > hgt;
    const size = wide ? Math.min(hgt, w * 0.58) : Math.min(w, hgt * narrowHeightRatio);
    root.style.setProperty("--board", `${Math.floor(size)}px`);
    root.classList.toggle("wide", wide);
    root.classList.toggle("narrow", !wide);
    if (wide !== lastWide) { lastWide = wide; onChange?.(wide); } // the screen re-arranges its pieces
  };
  const ro = new ResizeObserver(apply);
  ro.observe(root);
  requestAnimationFrame(apply);
  return () => ro.disconnect();
}

/** Kid-friendly cheers for the victory banner. */
const CHEERS = {
  3: ["SUPERSTAR! ⭐⭐⭐", "HIGH FIVE! 🖐️", "YOU'RE AMAZING! 🎉", "SIR HOPS IS SO PROUD! 🐴", "WOW, LOOK AT YOU GO! 🚀", "PUZZLE CHAMPION! 🏆"],
  2: ["GREAT JOB! ⭐⭐", "YOU DID IT! 🌟", "SO CLOSE TO PERFECT! 🎈", "WAY TO GO, BUDDY! 🐾"],
  1: ["YOU SOLVED IT! ⭐", "NICE WORK! 👏", "YOU'RE GETTING BETTER! 🌈"],
};
export const cheerForStars = (stars, seed) => { const list = CHEERS[stars] ?? CHEERS[1]; return list[seed % list.length]; };

/** Big, colorful celebration banner shown when a challenge is solved. */
export function victoryBanner({ stars, seed, onNext, onRetry, nextLabel }) {
  return h("div", { class: `victory${stars === 3 ? " three" : ""}` },
    h("div", { class: "victory-top" },
      h("div", { class: "victory-avatar" }, sirHops({ happy: true })),
      h("div", null, starRow(stars, 3, "star big"), h("div", { class: "cheer" }, cheerForStars(stars, seed)))),
    h("div", { class: "row" },
      onRetry && stars < 3 ? kidButton({ text: "Try again", emoji: "🔄", color: "blue", small: true, onClick: onRetry }) : null,
      kidButton({ text: nextLabel, emoji: "🎉", color: "gold", onClick: onNext })));
}
