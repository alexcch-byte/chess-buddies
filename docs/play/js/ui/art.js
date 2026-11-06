// Vector art: chess pieces, checkers, the animal opponents and Sir Hops. Ported from the Android app's
// PieceArt.kt, CheckerArt.kt and CartoonCharacters.kt, which are all hand-drawn paths, so they carry over exactly.
import { fromMarkup } from "./dom.js";
import { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING } from "../chess/position.js";
import { Checker } from "../checkers/checkers.js";

// ---------- Chess pieces (100 x 100 grid) ----------

const ellipsePath = (cx, cy, rx, ry) => `M${cx - rx},${cy} a${rx},${ry} 0 1,0 ${2 * rx},0 a${rx},${ry} 0 1,0 ${-2 * rx},0 Z`;

const BASE_PARTS = [
  ["shadow", ellipsePath(50, 91.5, 33, 5)],
  ["shape", "M17,91.5 C17,87.5 21,84.5 25,84.5 L75,84.5 C79,84.5 83,87.5 83,91.5 C83,92.5 80.5,93 76,93 L24,93 C19.5,93 17,92.5 17,91.5 Z"],
  ["shape", "M23,78.5 L77,78.5 C79.5,78.5 81,80.5 81,83 L81,84.5 L19,84.5 L19,83 C19,80.5 20.5,78.5 23,78.5 Z"],
  ["line", "M25,84.5 L75,84.5"],
  ["hline", "M23,80.5 L46,80.5"],
];

const PIECE_PARTS = {
  [PAWN]: [
    ["shape", "M28,78.5 C32,67 38,62 41,55 L59,55 C62,62 68,67 72,78.5 Z"],
    ["shape", ellipsePath(50, 54, 16.5, 4.5)],
    ["shape", ellipsePath(50, 36, 14.5, 14.5)],
    ["hdot", ellipsePath(45, 30.5, 4.5, 3)],
    ["hline", "M35,74 C36,66 41,61 43,56"],
  ],
  [ROOK]: [
    ["shape", "M28,78.5 L32,39 L68,39 L72,78.5 Z"],
    ["shape", "M24,39 L76,39 C78,39 79,37 79,35 L79,34 L21,34 L21,35 C21,37 22,39 24,39 Z"],
    ["shape", "M22,34 L22,19 L34,19 L34,26 L44,26 L44,19 L56,19 L56,26 L66,26 L66,19 L78,19 L78,34 Z"],
    ["dot", "M46,60 L46,51 C46,47.5 54,47.5 54,51 L54,60 Z"],
    ["hline", "M34,74 L37,42"],
    ["hline", "M25,22 L31,22"],
  ],
  [BISHOP]: [
    ["shape", "M29,78.5 C33,67 39,63 41,56 L59,56 C61,63 67,67 71,78.5 Z"],
    ["shape", ellipsePath(50, 55.5, 16, 4)],
    ["shape", "M50,16 C64,26 69,38 65,49 C63,54 58,56 50,56 C42,56 37,54 35,49 C31,38 36,26 50,16 Z"],
    ["line", "M57,28 C53,33 46,38 43,44"],
    ["accent", "M56,28 C52,33 46,38 44,43"],
    ["shape", ellipsePath(50, 13.5, 4.5, 4.5)],
    ["hdot", ellipsePath(48.5, 12, 1.5, 1.5)],
    ["hline", "M38,46 C35,39 38,29 46,21"],
  ],
  [KNIGHT]: [
    ["shape", "M29,78.5 C27,67 31,59 40,54 C34,57 26,58 20,53 C15,48 16,42 22,38 C27,35 30,30 33,23 C35,19 37,13 40,8 L47,15 C50,12 53,10 56,8 L56,16 C68,19 76,30 75,48 C74,62 71,71 72,78.5 Z"],
    ["line", "M56,18 C65,24 69,34 68,48 C67,61 65,70 66,78.5"],
    ["accent", "M52,28 C58,33 60,40 60,49"],
    ["accent", "M40,54 C36,49 37,42 42,39"],
    ["dot", ellipsePath(34, 30, 3.5, 4)],
    ["hdot", ellipsePath(33, 28.5, 1.4, 1.4)],
    ["dot", ellipsePath(20, 46, 1.6, 1.2)],
    ["accent", "M21,50 C24,52 27,51 29,49"],
    ["hline", "M43,15 C46,20 44,25 39,28"],
    ["hline", "M70,36 C71,48 69,63 69,75"],
  ],
  [QUEEN]: [
    ["shape", "M28,78.5 C32,67 37,60 38,51 L62,51 C63,60 68,67 72,78.5 Z"],
    ["shape", ellipsePath(50, 51.5, 18, 4.5)],
    ["shape", "M29,51 L21,24 L35,37 L37,18 L45,34 L50,14 L55,34 L63,18 L65,37 L79,24 L71,51 Z"],
    ["shape", ellipsePath(21, 23, 4.2, 4.2)],
    ["shape", ellipsePath(37, 17, 4.2, 4.2)],
    ["shape", ellipsePath(50, 13, 4.5, 4.5)],
    ["shape", ellipsePath(63, 17, 4.2, 4.2)],
    ["shape", ellipsePath(79, 23, 4.2, 4.2)],
    ["hdot", ellipsePath(19.5, 21.5, 1.3, 1.3)],
    ["hdot", ellipsePath(35.5, 15.5, 1.3, 1.3)],
    ["hdot", ellipsePath(48.5, 11.5, 1.4, 1.4)],
    ["accent", "M50,56 L50,74"],
    ["hline", "M34,74 C36,65 41,59 42,52"],
  ],
  [KING]: [
    ["shape", "M28,78.5 C32,67 37,60 38,51 L62,51 C63,60 68,67 72,78.5 Z"],
    ["shape", ellipsePath(50, 51.5, 19, 4.5)],
    ["shape", "M27,51 C21,40 26,28 38,30 C42,25 58,25 62,30 C74,28 79,40 73,51 Z"],
    ["line", "M50,26 L50,51"],
    ["shape", "M47,7 h6 v6 h6 v5 h-6 v7 h-6 v-7 h-6 v-5 h6 Z"],
    ["jewel", ellipsePath(50, 15.5, 2.2, 2.2)],
    ["hline", "M34,74 C36,65 41,59 42,52"],
    ["hline", "M27,37 C29,32 34,31 38,32"],
  ],
};

const PALETTE = {
  w: { fill: "url(#grad-w)", line: "#2A2430", accent: "#4E4656", hl: "rgba(255,255,255,0.8)", hdot: "#FFFFFF" },
  b: { fill: "url(#grad-b)", line: "#14121A", accent: "#A69DC2", hl: "rgba(185,178,215,0.45)", hdot: "rgba(185,178,215,0.45)" },
};

function pieceGroup(type, side) {
  const c = PALETTE[side];
  const parts = [...BASE_PARTS, ...PIECE_PARTS[type]].map(([kind, d]) => {
    switch (kind) {
      case "shadow": return `<path d="${d}" fill="rgba(0,0,0,0.22)"/>`;
      case "shape": return `<path d="${d}" fill="${c.fill}" stroke="${c.line}" stroke-width="3.2" stroke-linejoin="round" stroke-linecap="round"/>`;
      case "line": return `<path d="${d}" fill="none" stroke="${c.line}" stroke-width="3.2" stroke-linejoin="round" stroke-linecap="round"/>`;
      case "accent": return `<path d="${d}" fill="none" stroke="${c.accent}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>`;
      case "hline": return `<path d="${d}" fill="none" stroke="${c.hl}" stroke-width="2.2" stroke-linecap="round"/>`;
      case "dot": return `<path d="${d}" fill="${c.line}"/>`;
      case "hdot": return `<path d="${d}" fill="${c.hdot}"/>`;
      case "jewel": return `<path d="${d}" fill="#FFC93C" stroke="${c.line}" stroke-width="1.2"/>`;
      default: return "";
    }
  });
  return `<g id="p${type}${side}">${parts.join("")}</g>`;
}

// ---------- Checkers (radius 40, centred on 0,0) ----------

function checkerGroup(id, fill, rim, shine, dark, king) {
  const r = 40;
  const crownPts = [[-0.62, 0.42], [-0.72, -0.32], [-0.32, 0.02], [0, -0.52], [0.32, 0.02], [0.72, -0.32], [0.62, 0.42]];
  const size = r * 0.62;
  const poly = (dy) => crownPts.map(([x, y]) => `${(x * size).toFixed(2)},${(y * size + dy).toFixed(2)}`).join(" ");
  const crown = king
    ? `<polygon points="${poly(size * 0.08)}" fill="rgba(0,0,0,0.25)"/>` +
      `<polygon points="${poly(0)}" fill="#FFC93C" stroke="#996700" stroke-width="${(0.12 * size).toFixed(2)}" stroke-linejoin="round"/>` +
      [[-0.72, -0.32], [0, -0.52], [0.72, -0.32]].map(([x, y]) => {
        const cx = x * size, cy = y * size;
        return `<circle cx="${cx}" cy="${cy}" r="${size * 0.13}" fill="#FFC93C" stroke="#996700" stroke-width="${size * 0.05}"/>` +
          `<circle cx="${cx - size * 0.035}" cy="${cy - size * 0.035}" r="${size * 0.045}" fill="#FFF2B2"/>`;
      }).join("")
    : "";
  return `<g id="${id}">` +
    `<circle cx="0" cy="${r * 0.12}" r="${r * 1.05}" fill="rgba(0,0,0,0.12)"/>` +
    `<circle cx="0" cy="${r * 0.07}" r="${r * 0.98}" fill="rgba(0,0,0,0.22)"/>` +
    `<circle r="${r}" fill="${dark}"/><circle r="${r * 0.96}" fill="${rim}"/><circle r="${r * 0.88}" fill="${fill}"/>` +
    `<circle r="${r * 0.68}" fill="none" stroke="${rim}" stroke-opacity="0.65" stroke-width="${r * 0.065}"/>` +
    `<circle r="${r * 0.5}" fill="${dark}" fill-opacity="0.35"/>` +
    `<circle r="${r * 0.44}" fill="none" stroke="${rim}" stroke-opacity="0.55" stroke-width="${r * 0.05}"/>` +
    `<circle cx="${-r * 0.16}" cy="${-r * 0.16}" r="${r * 0.28}" fill="${shine}" fill-opacity="0.4"/>` +
    crown + `</g>`;
}

const CHECKER_IDS = { [Checker.RED]: "ckr", [Checker.RED_KING]: "ckrk", [Checker.BLACK]: "ckb", [Checker.BLACK_KING]: "ckbk" };
export const checkerId = (piece) => CHECKER_IDS[piece];

// ---------- Characters (100 x 100) ----------

const P = (d, fill, stroke, sw = 2.8) =>
  `<path d="${d}" fill="${fill}"${stroke ? ` stroke="${stroke}" stroke-width="${sw}" stroke-linejoin="round" stroke-linecap="round"` : ""}/>`;
// Filled ellipse (EF) and its outline (ES) are separate elements, matching how the app fills then strokes.
const EF = (x, y, w, h, fill, op = 1) => `<ellipse cx="${x + w / 2}" cy="${y + h / 2}" rx="${w / 2}" ry="${h / 2}" fill="${fill}"${op !== 1 ? ` fill-opacity="${op}"` : ""}/>`;
const ES = (x, y, w, h, stroke, sw) => `<ellipse cx="${x + w / 2}" cy="${y + h / 2}" rx="${w / 2}" ry="${h / 2}" fill="none" stroke="${stroke}" stroke-width="${sw}"/>`;
const C = (cx, cy, r, fill) => `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${fill}"/>`;
const CS = (cx, cy, r, stroke, sw) => `<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${stroke}" stroke-width="${sw}"/>`;
const L = (x1, y1, x2, y2, color, sw) => `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="${sw}" stroke-linecap="round"/>`;
const Q = (x1, y1, cx, cy, x2, y2, color, sw) => `<path d="M${x1},${y1} Q${cx},${cy} ${x2},${y2}" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round"/>`;

const CHARACTERS = {
  bunny() {
    const dark = "#3A2D32";
    return [
      P("M34,42 C26,26 22,6 34,4 C44,2 45,22 41,42 Z", "#FFF9F5", dark),
      P("M33,38 C29,26 26,11 34,9 C41,8 41,23 38,38 Z", "#FFBCCB"),
      P("M64,42 C72,26 76,6 64,4 C54,2 53,22 57,42 Z", "#FFF9F5", dark),
      P("M65,38 C69,26 72,11 64,9 C57,8 57,23 60,38 Z", "#FFBCCB"),
      EF(14, 32, 72, 60, "#FFF9F5"), ES(14, 32, 72, 60, dark, 2.8),
      EF(21, 62, 14, 10, "#FFB4C4", 0.65), EF(65, 62, 14, 10, "#FFB4C4", 0.65),
      EF(31.5, 49, 11, 14, dark), C(35, 53, 2.8, "#fff"), C(39, 58, 1.4, "#fff"),
      EF(57.5, 49, 11, 14, dark), C(61, 53, 2.8, "#fff"), C(65, 58, 1.4, "#fff"),
      P("M50,65 L46,61 L54,61 Z", "#FF7B9B"),
      Q(46, 65, 41, 72, 36, 69, dark, 2.2), Q(54, 65, 59, 72, 64, 69, dark, 2.2),
      `<rect x="47" y="66" width="6" height="5" rx="1.5" fill="#fff" stroke="${dark}" stroke-width="1.8"/>`,
      L(50, 66, 50, 71, dark, 1.4),
      P("M50,91 L40,84 L40,98 Z", "#58C6FF", dark, 2), P("M50,91 L60,84 L60,98 Z", "#58C6FF", dark, 2),
      C(50, 91, 3.5, "#FFDD44"), CS(50, 91, 3.5, dark, 1.8),
    ].join("");
  },
  kitten() {
    const dark = "#38241C", ginger = "#FFA54A", stripe = "#D96E14";
    return [
      P("M20,48 L26,16 L46,38 Z", ginger, dark), P("M26,42 L30,22 L42,36 Z", "#FFB7B2"),
      P("M80,48 L74,16 L54,38 Z", ginger, dark), P("M74,42 L70,22 L58,36 Z", "#FFB7B2"),
      EF(15, 32, 70, 56, ginger), ES(15, 32, 70, 56, dark, 2.8),
      L(50, 33, 50, 43, stripe, 2.8), L(43, 36, 45, 44, stripe, 2.4), L(57, 36, 55, 44, stripe, 2.4),
      EF(33, 55, 34, 26, "#FFF7EE"),
      EF(19.5, 60.5, 13, 9, "#FF7B9B", 0.5), EF(67.5, 60.5, 13, 9, "#FF7B9B", 0.5),
      EF(29.5, 46.5, 13, 15, "#2ECC71"), ES(29.5, 46.5, 13, 15, dark, 2), EF(31.5, 47.5, 9, 13, "#1B4D3E"), C(34, 51, 2.8, "#fff"), C(38, 56, 1.3, "#fff"),
      EF(57.5, 46.5, 13, 15, "#2ECC71"), ES(57.5, 46.5, 13, 15, dark, 2), EF(59.5, 47.5, 9, 13, "#1B4D3E"), C(62, 51, 2.8, "#fff"), C(66, 56, 1.3, "#fff"),
      P("M50,65 L46,61 L54,61 Z", "#FF758F"),
      Q(46, 65, 42, 72, 38, 70, dark, 2.2), Q(54, 65, 58, 72, 62, 70, dark, 2.2),
      L(27, 62, 11, 59, dark, 1.8), L(27, 66, 10, 67, dark, 1.8), L(73, 62, 89, 59, dark, 1.8), L(73, 66, 90, 67, dark, 1.8),
      Q(33, 84, 50, 92, 67, 84, "#FF4757", 4.5),
      C(50, 89, 4.5, "#FFD32A"), CS(50, 89, 4.5, "#B38F00", 1.5),
    ].join("");
  },
  fox() {
    const dark = "#261C14", orange = "#E65100";
    return [
      P("M16,42 L22,8 L48,32 Z", orange, dark), P("M20,15 L22,8 L28,12 Z", dark),
      P("M84,42 L78,8 L52,32 Z", orange, dark), P("M80,15 L78,8 L72,12 Z", dark),
      P("M18,50 C14,70 34,84 50,86 C66,84 86,70 82,50 C80,36 20,36 18,50 Z", orange, dark),
      P("M18,50 C18,72 36,83 50,86 C38,76 26,64 24,50 Z", "#fff"), P("M82,50 C82,72 64,83 50,86 C62,76 74,64 76,50 Z", "#fff"),
      EF(28.5, 43.5, 11, 13, "#FFB300"), ES(28.5, 43.5, 11, 13, dark, 2), EF(30.5, 44.5, 7, 11, dark), C(32, 48, 2.2, "#fff"), C(36, 52, 1.1, "#fff"),
      Q(60, 52, 66, 44, 72, 52, dark, 2.8),
      EF(45.5, 70.5, 9, 7, dark),
      Q(46, 78, 50, 82, 54, 78, dark, 2.2),
      L(22, 64, 10, 63, dark, 1.8), L(78, 64, 90, 63, dark, 1.8),
    ].join("");
  },
  owl() {
    const dark = "#3E2723";
    return [
      P("M26,38 L20,16 L38,28 Z", "#8D6E63", dark, 2.4), P("M74,38 L80,16 L62,28 Z", "#8D6E63", dark, 2.4),
      EF(15, 28, 70, 60, "#A1887F"), ES(15, 28, 70, 60, dark, 2.8),
      C(35, 56, 17, "#D7CCC8"), C(65, 56, 17, "#D7CCC8"),
      C(35, 56, 14, "#fff"), CS(35, 56, 14, "#FFB300", 3), C(65, 56, 14, "#fff"), CS(65, 56, 14, "#FFB300", 3),
      L(48, 56, 52, 56, "#FFB300", 3.5),
      C(35, 56, 8.5, "#FFC107"), C(35, 56, 5.5, "#212121"), C(33, 53, 2.5, "#fff"),
      C(65, 56, 8.5, "#FFC107"), C(65, 56, 5.5, "#212121"), C(63, 53, 2.5, "#fff"),
      P("M50,56 L46,65 L54,65 Z", "#FF9800", "#E65100", 1.6),
      P("M50,14 L74,22 L50,30 L26,22 Z", "#1A237E", "#0D47A1", 1.8),
      C(50, 22, 2.2, "#FFD54F"),
      Q(50, 22, 62, 24, 66, 34, "#FFD54F", 1.8), C(66, 34, 1.8, "#FFD54F"),
    ].join("");
  },
  dragon() {
    const dark = "#0077B6", ink = "#264653";
    return [
      P("M50,10 L44,22 L56,22 Z", "#FF7043", ink, 2), P("M62,15 L57,26 L69,26 Z", "#FF7043", ink, 2),
      P("M28,34 C18,24 16,12 24,10 C28,14 31,24 33,35 Z", "#FFCA28", ink, 2.2), P("M72,34 C82,24 84,12 76,10 C72,14 69,24 67,35 Z", "#FFCA28", ink, 2.2),
      EF(17, 30, 66, 56, "#48CAE4"), ES(17, 30, 66, 56, dark, 2.8),
      EF(28, 52, 44, 32, "#90E0EF"), ES(28, 52, 44, 32, dark, 2.2),
      EF(20, 59, 12, 8, "#FF85A1", 0.6), EF(68, 59, 12, 8, "#FF85A1", 0.6),
      EF(30.5, 43.5, 13, 15, "#7209B7"), C(35, 48, 3, "#fff"), C(39, 53, 1.3, "#fff"),
      EF(56.5, 43.5, 13, 15, "#7209B7"), C(61, 48, 3, "#fff"), C(65, 53, 1.3, "#fff"),
      EF(43, 63.5, 4, 5, dark), EF(53, 63.5, 4, 5, dark),
      Q(44, 74, 50, 78, 56, 74, dark, 2.4),
      P("M64,68 L66,64 L68,68 L72,70 L68,72 L66,76 L64,72 L60,70 Z", "#FFD166"),
    ].join("");
  },
};

const PARTY = [
  P("M44,2 L32,24 L56,20 Z", "#FF4757", "#28232F", 2.2),
  L(36, 17, 50, 15, "#FFD32A", 2.5), L(39, 10, 47, 9, "#58C6FF", 2.5),
  C(44, 2, 3.5, "#FFD32A"), CS(44, 2, 3.5, "#28232F", 1.8),
  P("M18,24 L20,20 L22,24 L26,26 L22,28 L20,32 L18,28 L14,26 Z", "#FFD700"),
  P("M78,36 L80,32 L82,36 L86,38 L82,40 L80,44 L78,40 L74,38 Z", "#FFD700"),
].join("");

// ---------- Public API ----------

let installed = false;
/** Adds the shared gradients and symbols once; every icon then references them with <use>. */
export function installDefs() {
  if (installed) return;
  installed = true;
  const pieces = [PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING].flatMap((t) => [pieceGroup(t, "w"), pieceGroup(t, "b")]).join("");
  const checkers = [
    checkerGroup("ckr", "#E54335", "#9E231B", "#FA776B", "#75150E", false),
    checkerGroup("ckrk", "#E54335", "#9E231B", "#FA776B", "#75150E", true),
    checkerGroup("ckb", "#383544", "#1B1924", "#6B687D", "#100E17", false),
    checkerGroup("ckbk", "#383544", "#1B1924", "#6B687D", "#100E17", true),
  ].join("");
  const svg = fromMarkup(
    `<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs>` +
    `<linearGradient id="grad-w" gradientUnits="userSpaceOnUse" x1="25" y1="10" x2="75" y2="90"><stop offset="0" stop-color="#FFFFFF"/><stop offset="0.5" stop-color="#FFFDF6"/><stop offset="1" stop-color="#EBE1CA"/></linearGradient>` +
    `<linearGradient id="grad-b" gradientUnits="userSpaceOnUse" x1="25" y1="10" x2="75" y2="90"><stop offset="0" stop-color="#514C60"/><stop offset="0.5" stop-color="#383446"/><stop offset="1" stop-color="#221F2D"/></linearGradient>` +
    `${pieces}${checkers}</defs></svg>`);
  document.body.prepend(svg);
}

/** The <use> markup for a chess piece (signed code) on the 100 x 100 grid. */
export const pieceUse = (piece, extra = "") => `<use href="#p${Math.abs(piece)}${piece > 0 ? "w" : "b"}" ${extra}/>`;
export const checkerUse = (piece, extra = "") => `<use href="#${CHECKER_IDS[piece]}" ${extra}/>`;

/** A single chess piece as a standalone picture. */
export function pieceIcon(piece, cls = "icon") {
  return fromMarkup(`<svg class="${cls}" viewBox="0 0 100 100" aria-hidden="true">${pieceUse(piece)}</svg>`);
}

/** A single checker as a picture (radius 44 of 100, like the app). */
export function checkerIcon(piece, cls = "icon") {
  return fromMarkup(`<svg class="${cls}" viewBox="-50 -50 100 100" aria-hidden="true">${checkerUse(piece, 'transform="scale(1.1)"')}</svg>`);
}

/** Picture for a lesson card: a piece or a checker. */
export const lessonIcon = (lesson, cls) => (lesson.game === "checkers" ? checkerIcon(lesson.icon, cls) : pieceIcon(lesson.icon, cls));

/** Cartoon avatar for an opponent. thinking / celebrating add a gentle bounce. */
export function animalAvatar(bot, { thinking = false, celebrating = false, cls = "avatar" } = {}) {
  const motion = celebrating ? " celebrating" : thinking ? " thinking" : "";
  return fromMarkup(`<svg class="${cls}${motion}" viewBox="0 0 100 100" aria-hidden="true">${CHARACTERS[bot.id]()}</svg>`);
}

/** Sir Hops, the knight coach. Wears a party hat when happy. */
export function sirHops({ happy = false, cls = "avatar" } = {}) {
  return fromMarkup(`<svg class="${cls}${happy ? " celebrating" : ""}" viewBox="0 0 100 100" aria-hidden="true">${pieceUse(KNIGHT)}${happy ? PARTY : ""}</svg>`);
}

/** A star, filled gold or empty grey. */
export function starShape(filled, cls = "star") {
  const pts = [];
  for (let i = 0; i < 10; i++) {
    const r = i % 2 === 0 ? 50 : 23.5;
    const a = -Math.PI / 2 + (i * Math.PI) / 5;
    pts.push(`${(50 + r * Math.cos(a)).toFixed(2)},${(50 + r * Math.sin(a)).toFixed(2)}`);
  }
  return fromMarkup(
    `<svg class="${cls}" viewBox="0 0 100 100" aria-hidden="true"><polygon points="${pts.join(" ")}" fill="${filled ? "#FFC93C" : "#E4E0EE"}" stroke="${filled ? "#E09A00" : "#CFCADC"}" stroke-width="7" stroke-linejoin="round"/></svg>`);
}

export const STAR_POINTS = (() => {
  const pts = [];
  for (let i = 0; i < 10; i++) {
    const r = i % 2 === 0 ? 1 : 0.45;
    const a = -Math.PI / 2 + (i * Math.PI) / 5;
    pts.push(`${(r * Math.cos(a)).toFixed(4)},${(r * Math.sin(a)).toFixed(4)}`);
  }
  return pts.join(" ");
})();
