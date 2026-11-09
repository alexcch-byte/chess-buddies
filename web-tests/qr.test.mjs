// Tests for the QR code generator. The codes themselves were also decoded with OpenCV (an independent reader) for every
// version 1 to 40 at every error-correction level; that check needs OpenCV, so here we pin the facts it relies on.
// Run with: node --test web-tests/qr.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { encodeQR, qrSvg, byteCapacity, dataCodewords, sizeOf } from "../docs/play/js/ui/qr.js";

test("how much each version holds matches the standard's table", () => {
  // Data codewords per version for L, M, Q, H (ISO/IEC 18004, table 7), versions 1 to 14.
  const table = {
    1: [19, 16, 13, 9], 2: [34, 28, 22, 16], 3: [55, 44, 34, 26], 4: [80, 64, 48, 36], 5: [108, 86, 62, 46], 6: [136, 108, 76, 60],
    7: [156, 124, 88, 66], 8: [194, 154, 110, 86], 9: [232, 182, 132, 100], 10: [274, 216, 154, 122], 11: [324, 254, 180, 140],
    12: [370, 290, 206, 158], 13: [428, 334, 244, 180], 14: [461, 365, 261, 197],
  };
  for (const [v, counts] of Object.entries(table)) {
    ["L", "M", "Q", "H"].forEach((ecl, i) => assert.equal(dataCodewords(Number(v), ecl), counts[i], `version ${v} ${ecl}`));
  }
  // The biggest symbol, version 40, holds 2953 bytes at level L.
  assert.equal(byteCapacity(40, "L"), 2953);
  assert.equal(byteCapacity(1, "L"), 17);
  assert.equal(byteCapacity(1, "H"), 7);
  assert.equal(sizeOf(1), 21);
  assert.equal(sizeOf(40), 177);
});

test("the smallest version that fits is chosen, and the level is raised while it still fits", () => {
  assert.equal(encodeQR("A".repeat(17), { boost: false }).version, 1);
  assert.equal(encodeQR("A".repeat(18), { boost: false }).version, 2);
  const small = encodeQR("hello");
  assert.equal(small.version, 1);
  assert.equal(small.ecl, "H", "five letters fit version 1 even at the strongest level");
  const full = encodeQR("A".repeat(17));
  assert.equal(full.ecl, "L", "no room to raise it");
  assert.equal(encodeQR("hello", { boost: false }).ecl, "L");
  assert.throws(() => encodeQR("A".repeat(2954)), RangeError);
});

test("a symbol has the fixed patterns every reader looks for", () => {
  const { modules: m, size } = encodeQR("https://example.com/");
  // Three finder patterns, a 7x7 ring with a 3x3 centre, in three corners and not the fourth.
  const finder = (x0, y0) => {
    for (let y = 0; y < 7; y++) for (let x = 0; x < 7; x++) {
      const ring = Math.max(Math.abs(x - 3), Math.abs(y - 3));
      assert.equal(m[y0 + y][x0 + x], ring !== 2, `finder at ${x0},${y0}, module ${x},${y}`);
    }
  };
  finder(0, 0); finder(size - 7, 0); finder(0, size - 7);
  // Light margin around each finder, and the always-dark module.
  for (let i = 0; i < 8; i++) { assert.equal(m[7][i], false); assert.equal(m[i][7], false); }
  assert.equal(m[size - 8][8], true);
  // Timing patterns alternate between the finders.
  for (let i = 8; i < size - 8; i++) { assert.equal(m[6][i], i % 2 === 0); assert.equal(m[i][6], i % 2 === 0); }
});

test("the format bits name the level and mask, with a valid check code", () => {
  const FORMAT_BITS = { L: 1, M: 0, Q: 3, H: 2 };
  for (const ecl of ["L", "M", "Q", "H"]) {
    for (let mask = 0; mask < 8; mask++) {
      const { modules: m, size } = encodeQR("format check", { ecl, mask, boost: false });
      // Read the copy around the top-left finder, then the copy split between the other two finders; both must agree.
      let first = 0;
      const at = [[8, 0], [8, 1], [8, 2], [8, 3], [8, 4], [8, 5], [8, 7], [8, 8], [7, 8], [5, 8], [4, 8], [3, 8], [2, 8], [1, 8], [0, 8]];
      at.forEach(([x, y], i) => { if (m[y][x]) first |= 1 << i; });
      let second = 0;
      for (let i = 0; i < 8; i++) if (m[8][size - 1 - i]) second |= 1 << i;
      for (let i = 8; i < 15; i++) if (m[size - 15 + i][8]) second |= 1 << i;
      assert.equal(first, second, `${ecl} mask ${mask}: both copies agree`);
      const data = (FORMAT_BITS[ecl] << 3) | mask;
      let rem = data;
      for (let i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
      assert.equal(first, ((data << 10) | rem) ^ 0x5412, `${ecl} mask ${mask}: bits are the BCH code of the data`);
    }
  }
});

test("the same text always gives the same code, and a known code has not changed", () => {
  const text = "https://alexcch-byte.github.io/chess-buddies/play/#join=CB2OL";
  const a = encodeQR(text, { ecl: "M" });
  assert.deepEqual(a.modules, encodeQR(text, { ecl: "M" }).modules);
  // This exact picture was decoded back to its text with OpenCV (an independent reader). If the generator changes, make
  // the new picture and decode it the same way before updating this fingerprint.
  const hash = createHash("sha256").update(a.modules.map((r) => r.map(Number).join("")).join("|")).digest("hex");
  assert.deepEqual({ version: a.version, ecl: a.ecl, mask: a.mask, size: a.size, hash }, {
    version: 4, ecl: "M", mask: 2, size: 33, hash: "f4b2c3e073a834aa762f1e534de284a463a74521bf2cd47634c23bfc9d5aad6d",
  });
});

test("any text works, including accents and emoji", () => {
  const qr = encodeQR("Café ♞ 😀");
  assert.ok(qr.version >= 1 && qr.modules.length === qr.size);
});

test("an invite link and a reply from a real connection make codes a tablet camera can read", async () => {
  // Real-size inputs: the short invite link (about 210 letters) and the reply (about 155).
  const { encodeSignal, inviteLink } = await import("../docs/play/js/nearby/signal.js");
  const sdp = [
    "v=0", "o=- 1 2 IN IP4 127.0.0.1", "s=-", "t=0 0", "a=group:BUNDLE 0", "m=application 9 UDP/DTLS/SCTP webrtc-datachannel", "c=IN IP4 0.0.0.0",
    "a=candidate:1 1 udp 2113937151 a3f83d50-6552-48ef-b8aa-9631f6f14843.local 59358 typ host generation 0",
    "a=candidate:2 1 udp 2113939711 aefa4f3c-6f97-4ff0-8527-75de5a217e22.local 59359 typ host generation 0",
    "a=ice-ufrag:fu1J", "a=ice-pwd:BxZgGqY6Vf9BFP7IWAN38opb",
    "a=fingerprint:sha-256 B1:74:CD:9F:51:9F:13:9E:00:A8:78:50:14:D2:7B:F0:D3:96:6C:BC:98:62:B4:3D:BE:E8:E6:90:1F:2E:60:DE",
    "a=setup:actpass", "a=mid:0", "a=sctp-port:5000", "",
  ].join("\r\n");
  const code = await encodeSignal({ role: "offer", wide: false, sdp });
  const link = inviteLink(code, "https://alexcch-byte.github.io/chess-buddies/play/");
  const invite = encodeQR(link), reply = encodeQR(code.replace("CB2OL", "CB2AL"));
  assert.ok(invite.size <= 57, `the invite is ${invite.size} modules wide (version ${invite.version})`);
  assert.ok(reply.size <= 49, `the reply is ${reply.size} modules wide (version ${reply.version})`);
});

test("the SVG has one dark path, a white background and the quiet border", () => {
  const qr = encodeQR("hi");
  const svg = qrSvg(qr, { label: "Invite" });
  assert.match(svg, /^<svg [^>]*viewBox="0 0 29 29"/, "21 modules plus 4 on each side");
  assert.match(svg, /aria-label="Invite"/);
  assert.equal((svg.match(/<path /g) ?? []).length, 1);
  const dark = qr.modules.flat().filter(Boolean).length;
  assert.equal((svg.match(/h1v1h-1z/g) ?? []).length, dark);
});
