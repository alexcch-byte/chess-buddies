// Tests for reading QR codes: the vendored jsQR reader against codes made by our own generator (two independent
// implementations of the standard), plus the camera helpers that don't need a camera.
// Run with: node --test web-tests/qr-scan.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { encodeQR, byteCapacity } from "../docs/play/js/ui/qr.js";
import { loadReader, readQr, cameraSupported, cameraProblem } from "../docs/play/js/ui/qr-scan.js";
import { encodeSignal, inviteLink } from "../docs/play/js/nearby/signal.js";

/** Draws a code as grey pixels the way a camera frame would hold it: dark modules on a light page with a margin. */
function picture(qr, { scale = 4, margin = 4, rotate = 0, noise = 0, seed = 1 } = {}) {
  const n = (qr.size + margin * 2) * scale;
  let px = new Uint8Array(n * n).fill(255);
  for (let y = 0; y < qr.size; y++) for (let x = 0; x < qr.size; x++) {
    if (!qr.modules[y][x]) continue;
    for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) px[((y + margin) * scale + dy) * n + (x + margin) * scale + dx] = 0;
  }
  for (let turn = 0; turn < rotate / 90; turn++) { // a quarter turn clockwise
    const next = new Uint8Array(n * n);
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) next[x * n + (n - 1 - y)] = px[y * n + x];
    px = next;
  }
  let s = seed;
  const rnd = () => (s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
  const data = new Uint8ClampedArray(n * n * 4);
  for (let i = 0; i < n * n; i++) {
    const v = px[i] + (noise ? (rnd() - 0.5) * 2 * noise : 0);
    data[4 * i] = data[4 * i + 1] = data[4 * i + 2] = v;
    data[4 * i + 3] = 255;
  }
  return { data, width: n, height: n };
}

const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_#=:/.";
const text = (n, seed) => { let s = seed; return Array.from({ length: n }, () => chars[Math.floor(((s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff) * chars.length)]).join(""); };

test("the reader loads and reads a code from our generator", async () => {
  const jsQR = await loadReader();
  assert.equal(typeof jsQR, "function");
  assert.equal(readQr(jsQR, picture(encodeQR("hello"))), "hello");
});

test("every version up to 20, at every level, reads back exactly", async () => {
  const jsQR = await loadReader();
  for (let version = 1; version <= 20; version++) {
    for (const ecl of ["L", "M", "Q", "H"]) {
      const s = text(byteCapacity(version, ecl), version * 7 + ecl.charCodeAt(0));
      const qr = encodeQR(s, { ecl, minVersion: version, boost: false });
      assert.equal(qr.version, version);
      assert.equal(readQr(jsQR, picture(qr, { scale: 3 })), s, `version ${version} level ${ecl}`);
    }
  }
});

test("a real invite link and reply read back, turned any way round and with a noisy picture", async () => {
  const jsQR = await loadReader();
  const sdp = [
    "v=0", "o=- 1 2 IN IP4 127.0.0.1", "s=-", "t=0 0", "a=group:BUNDLE 0", "m=application 9 UDP/DTLS/SCTP webrtc-datachannel", "c=IN IP4 0.0.0.0",
    "a=candidate:1 1 udp 2113937151 a3f83d50-6552-48ef-b8aa-9631f6f14843.local 59358 typ host generation 0",
    "a=candidate:2 1 udp 2113939711 aefa4f3c-6f97-4ff0-8527-75de5a217e22.local 59359 typ host generation 0",
    "a=ice-ufrag:fu1J", "a=ice-pwd:BxZgGqY6Vf9BFP7IWAN38opb",
    "a=fingerprint:sha-256 B1:74:CD:9F:51:9F:13:9E:00:A8:78:50:14:D2:7B:F0:D3:96:6C:BC:98:62:B4:3D:BE:E8:E6:90:1F:2E:60:DE",
    "a=setup:actpass", "a=mid:0", "a=sctp-port:5000", "",
  ].join("\r\n");
  const invite = inviteLink(await encodeSignal({ role: "offer", wide: false, sdp }), "https://alexcch-byte.github.io/chess-buddies/play/");
  const reply = await encodeSignal({ role: "answer", wide: true, sdp: sdp.replace("actpass", "active") });
  for (const s of [invite, reply]) {
    const qr = encodeQR(s);
    for (const rotate of [0, 90, 180, 270]) assert.equal(readQr(jsQR, picture(qr, { rotate })), s, `turned ${rotate} degrees`);
    assert.equal(readQr(jsQR, picture(qr, { scale: 5, noise: 45, seed: 9 })), s, "noisy picture");
    assert.equal(readQr(jsQR, picture(qr, { scale: 3 })), s, "small: 3 pixels per module");
  }
});

test("a picture with no code in it, or a wrong code, reads as nothing", async () => {
  const jsQR = await loadReader();
  const blank = { data: new Uint8ClampedArray(200 * 200 * 4).fill(255), width: 200, height: 200 };
  assert.equal(readQr(jsQR, blank), null);
  const noise = picture(encodeQR("hello"), { noise: 400, seed: 3 });
  assert.equal(readQr(jsQR, noise), null);
});

test("camera helpers: support check and a plain sentence for each kind of failure", () => {
  assert.equal(cameraSupported({}), false);
  assert.equal(cameraSupported({ mediaDevices: {} }), false);
  assert.equal(cameraSupported({ mediaDevices: { getUserMedia() {} } }), true);
  assert.match(cameraProblem({ name: "NotAllowedError" }), /isn't allowed/);
  assert.match(cameraProblem({ name: "NotFoundError" }), /no camera/);
  assert.match(cameraProblem({ name: "NotReadableError" }), /another app/);
  assert.match(cameraProblem(new Error("who knows")), /didn't start/);
  for (const name of ["NotAllowedError", "NotFoundError", "NotReadableError", "Other"]) assert.match(cameraProblem({ name }), /paste/i);
});
