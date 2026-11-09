// A small QR code generator (byte mode, versions 1 to 40, error correction L/M/Q/H), written from the QR Code standard
// (ISO/IEC 18004). Used to show the Play Nearby invite and reply so a friend's camera can read them.
//
//   const qr = encodeQR("https://example.com/");   // { version, size, ecl, mask, modules }  modules[y][x] === true is dark
//   qrSvg(qr)                                       // an SVG string, with the quiet border a scanner needs

/** Error-correction codewords in each block, then the number of blocks, by version (index 0 is unused). */
const ECC_PER_BLOCK = {
  L: [-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
  M: [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],
  Q: [-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
  H: [-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
};
const BLOCKS = {
  L: [-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],
  M: [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],
  Q: [-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],
  H: [-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81],
};
/** The two format-information bits that name each level. */
const FORMAT_BITS = { L: 1, M: 0, Q: 3, H: 2 };
const LEVELS = ["L", "M", "Q", "H"];

/** Modules in one side of the symbol. */
export const sizeOf = (version) => version * 4 + 17;

/** Modules left for data and error correction once the fixed patterns are taken out. */
function rawDataModules(version) {
  let n = (16 * version + 128) * version + 64;
  if (version >= 2) {
    const align = Math.floor(version / 7) + 2;
    n -= (25 * align - 10) * align - 55;
    if (version >= 7) n -= 36;
  }
  return n;
}

/** How many bytes of data a symbol of this version and level holds (before the header bits are counted). */
export function dataCodewords(version, ecl) {
  return Math.floor(rawDataModules(version) / 8) - ECC_PER_BLOCK[ecl][version] * BLOCKS[ecl][version];
}

const countBits = (version) => (version <= 9 ? 8 : 16);

/** The most bytes of text that fit at this version and level. */
export const byteCapacity = (version, ecl) => Math.floor((dataCodewords(version, ecl) * 8 - 4 - countBits(version)) / 8);

// ---------------------------------------------------------------- Reed-Solomon over GF(256)

function gfMultiply(x, y) {
  let z = 0;
  for (let i = 7; i >= 0; i--) {
    z = (z << 1) ^ ((z >>> 7) * 0x11d);
    z ^= ((y >>> i) & 1) * x;
  }
  return z;
}

function rsDivisor(degree) {
  const result = new Array(degree).fill(0);
  result[degree - 1] = 1;
  let root = 1;
  for (let i = 0; i < degree; i++) {
    for (let j = 0; j < result.length; j++) {
      result[j] = gfMultiply(result[j], root);
      if (j + 1 < result.length) result[j] ^= result[j + 1];
    }
    root = gfMultiply(root, 2);
  }
  return result;
}

function rsRemainder(data, divisor) {
  const result = divisor.map(() => 0);
  for (const b of data) {
    const factor = b ^ result.shift();
    result.push(0);
    divisor.forEach((coef, i) => { result[i] ^= gfMultiply(coef, factor); });
  }
  return result;
}

// ---------------------------------------------------------------- building the symbol

const bit = (value, i) => ((value >>> i) & 1) !== 0;

function alignmentPositions(version) {
  if (version === 1) return [];
  const count = Math.floor(version / 7) + 2;
  const step = version === 32 ? 26 : Math.ceil((version * 4 + 4) / (count * 2 - 2)) * 2;
  const out = [6];
  for (let pos = sizeOf(version) - 7; out.length < count; pos -= step) out.splice(1, 0, pos);
  return out;
}

const MASKS = [
  (x, y) => (x + y) % 2 === 0,
  (x, y) => y % 2 === 0,
  (x, y) => x % 3 === 0,
  (x, y) => (x + y) % 3 === 0,
  (x, y) => (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0,
  (x, y) => ((x * y) % 2) + ((x * y) % 3) === 0,
  (x, y) => (((x * y) % 2) + ((x * y) % 3)) % 2 === 0,
  (x, y) => (((x + y) % 2) + ((x * y) % 3)) % 2 === 0,
];

class QrSymbol {
  constructor(version, ecl) {
    this.version = version;
    this.ecl = ecl;
    this.size = sizeOf(version);
    this.modules = Array.from({ length: this.size }, () => new Array(this.size).fill(false));
    this.fixed = Array.from({ length: this.size }, () => new Array(this.size).fill(false));
    this.#drawFixedPatterns();
  }

  #set(x, y, dark) {
    this.modules[y][x] = dark;
    this.fixed[y][x] = true;
  }

  #drawFixedPatterns() {
    const { size, version } = this;
    for (let i = 0; i < size; i++) { this.#set(6, i, i % 2 === 0); this.#set(i, 6, i % 2 === 0); }
    for (const [cx, cy] of [[3, 3], [size - 4, 3], [3, size - 4]]) {
      for (let dy = -4; dy <= 4; dy++) {
        for (let dx = -4; dx <= 4; dx++) {
          const dist = Math.max(Math.abs(dx), Math.abs(dy));
          const x = cx + dx, y = cy + dy;
          if (x >= 0 && x < size && y >= 0 && y < size) this.#set(x, y, dist !== 2 && dist !== 4);
        }
      }
    }
    const pos = alignmentPositions(version);
    for (let i = 0; i < pos.length; i++) {
      for (let j = 0; j < pos.length; j++) {
        if ((i === 0 && j === 0) || (i === 0 && j === pos.length - 1) || (i === pos.length - 1 && j === 0)) continue;
        for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) this.#set(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
      }
    }
    this.#drawFormat(0); // reserves the room; the real value goes in once the mask is chosen
    if (version >= 7) {
      let rem = version;
      for (let i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25);
      const bits = (version << 12) | rem;
      for (let i = 0; i < 18; i++) {
        const a = size - 11 + (i % 3), b = Math.floor(i / 3);
        this.#set(a, b, bit(bits, i));
        this.#set(b, a, bit(bits, i));
      }
    }
  }

  #drawFormat(mask) {
    const { size } = this;
    const data = (FORMAT_BITS[this.ecl] << 3) | mask;
    let rem = data;
    for (let i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
    const bits = ((data << 10) | rem) ^ 0x5412;
    for (let i = 0; i <= 5; i++) this.#set(8, i, bit(bits, i));
    this.#set(8, 7, bit(bits, 6));
    this.#set(8, 8, bit(bits, 7));
    this.#set(7, 8, bit(bits, 8));
    for (let i = 9; i < 15; i++) this.#set(14 - i, 8, bit(bits, i));
    for (let i = 0; i < 8; i++) this.#set(size - 1 - i, 8, bit(bits, i));
    for (let i = 8; i < 15; i++) this.#set(8, size - 15 + i, bit(bits, i));
    this.#set(8, size - 8, true);
  }

  /** Lays the data and error-correction bytes out in the zigzag the standard asks for. */
  placeCodewords(bytes) {
    const { size } = this;
    let i = 0;
    for (let right = size - 1; right >= 1; right -= 2) {
      if (right === 6) right = 5;
      for (let vert = 0; vert < size; vert++) {
        for (let j = 0; j < 2; j++) {
          const x = right - j;
          const y = ((right + 1) & 2) === 0 ? size - 1 - vert : vert;
          if (!this.fixed[y][x] && i < bytes.length * 8) {
            this.modules[y][x] = bit(bytes[i >>> 3], 7 - (i & 7));
            i++;
          }
        }
      }
    }
  }

  #applyMask(mask) {
    for (let y = 0; y < this.size; y++) {
      for (let x = 0; x < this.size; x++) if (!this.fixed[y][x] && MASKS[mask](x, y)) this.modules[y][x] = !this.modules[y][x];
    }
  }

  /** Picks the mask with the lowest penalty (the standard's four rules), applies it and writes the format bits. */
  finish(forcedMask = -1) {
    let best = forcedMask, bestScore = Infinity;
    if (forcedMask < 0) {
      for (let m = 0; m < 8; m++) {
        this.#applyMask(m);
        this.#drawFormat(m);
        const score = this.#penalty();
        if (score < bestScore) { best = m; bestScore = score; }
        this.#applyMask(m); // masks undo themselves
      }
    }
    this.#applyMask(best);
    this.#drawFormat(best);
    this.mask = best;
  }

  #penalty() {
    const { size, modules } = this;
    let score = 0;
    const lines = [];
    for (let y = 0; y < size; y++) lines.push(modules[y]);
    for (let x = 0; x < size; x++) lines.push(modules.map((row) => row[x]));
    for (const line of lines) {
      let run = 1;
      for (let i = 1; i <= size; i++) {
        if (i < size && line[i] === line[i - 1]) run++;
        else { if (run >= 5) score += 3 + (run - 5); run = 1; }
      }
      // Something that looks like a finder pattern (1:1:3:1:1) with four light modules on one side.
      for (let i = 0; i + 11 <= size; i++) {
        const w = line.slice(i, i + 11).map(Number).join("");
        if (w === "10111010000" || w === "00001011101") score += 40;
      }
    }
    for (let y = 0; y < size - 1; y++) {
      for (let x = 0; x < size - 1; x++) {
        const c = modules[y][x];
        if (c === modules[y][x + 1] && c === modules[y + 1][x] && c === modules[y + 1][x + 1]) score += 3;
      }
    }
    const dark = modules.reduce((n, row) => n + row.filter(Boolean).length, 0);
    const total = size * size;
    score += Math.max(0, Math.ceil(Math.abs(dark * 20 - total * 10) / total) - 1) * 10; // dark/light balance
    return score;
  }
}

/**
 * Makes a QR code for text (as UTF-8 bytes). Picks the smallest version that fits, and then the strongest error correction
 * that still fits in that version (turn that off with boost: false). minVersion and mask are for testing.
 */
export function encodeQR(text, { ecl = "L", minVersion = 1, boost = true, mask = -1 } = {}) {
  const data = new TextEncoder().encode(text);
  let version = minVersion;
  while (version <= 40 && byteCapacity(version, ecl) < data.length) version++;
  if (version > 40) throw new RangeError("That is too much text for a QR code.");
  if (boost) for (const next of LEVELS.slice(LEVELS.indexOf(ecl) + 1)) if (byteCapacity(version, next) >= data.length) ecl = next;

  // Header, text, an end marker and padding, as a run of bits.
  const bits = [];
  const put = (value, count) => { for (let i = count - 1; i >= 0; i--) bits.push((value >>> i) & 1); };
  put(0b0100, 4);
  put(data.length, countBits(version));
  for (const b of data) put(b, 8);
  const capacityBits = dataCodewords(version, ecl) * 8;
  put(0, Math.min(4, capacityBits - bits.length));
  put(0, (8 - (bits.length % 8)) % 8);
  const bytes = [];
  for (let i = 0; i < bits.length; i += 8) bytes.push(parseInt(bits.slice(i, i + 8).join(""), 2));
  for (let pad = 0xec; bytes.length < dataCodewords(version, ecl); pad ^= 0xec ^ 0x11) bytes.push(pad);

  // Error correction: split into blocks, work out each block's check bytes, then interleave.
  const blocks = BLOCKS[ecl][version], eccLen = ECC_PER_BLOCK[ecl][version];
  const rawCodewords = Math.floor(rawDataModules(version) / 8);
  const shortBlocks = blocks - (rawCodewords % blocks);
  const shortLen = Math.floor(rawCodewords / blocks);
  const divisor = rsDivisor(eccLen);
  const parts = [];
  for (let i = 0, k = 0; i < blocks; i++) {
    const dat = bytes.slice(k, k + shortLen - eccLen + (i < shortBlocks ? 0 : 1));
    k += dat.length;
    const ecc = rsRemainder(dat, divisor);
    if (i < shortBlocks) dat.push(0); // keeps every block the same length; the filler is skipped below
    parts.push(dat.concat(ecc));
  }
  const out = [];
  for (let i = 0; i < parts[0].length; i++) {
    parts.forEach((block, j) => { if (i !== shortLen - eccLen || j >= shortBlocks) out.push(block[i]); });
  }

  const symbol = new QrSymbol(version, ecl);
  symbol.placeCodewords(out);
  symbol.finish(mask);
  return { version, size: symbol.size, ecl, mask: symbol.mask, modules: symbol.modules };
}

/** The code as an SVG string: black modules on a white square with the 4-module quiet border scanners need. */
export function qrSvg(qr, { border = 4, label = "QR code" } = {}) {
  const dim = qr.size + border * 2;
  let path = "";
  for (let y = 0; y < qr.size; y++) {
    for (let x = 0; x < qr.size; x++) if (qr.modules[y][x]) path += `M${x + border} ${y + border}h1v1h-1z`;
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${dim} ${dim}" role="img" aria-label="${label}" shape-rendering="crispEdges">` +
    `<rect width="${dim}" height="${dim}" fill="#fff"/><path d="${path}" fill="#000"/></svg>`;
}
