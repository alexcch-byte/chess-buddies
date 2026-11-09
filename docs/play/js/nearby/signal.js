// Invite and reply codes: how two browsers find each other without a server.
//
// WebRTC needs each side to hand the other a small description of itself (an "offer" and an "answer"). Normally a server
// relays them. Here a grown-up copies them across instead (or scans a QR code): the host's code is the invite, the friend's
// code is the reply. A code is the description written in URL-safe letters, so it can also ride along in a link.
//
// Short form:   CB2  O|A  L|W  payload
//   The few facts a data-channel description really holds (ICE user name and password, the DTLS fingerprint, the ways to be
//   reached) packed as bytes; the standard text is rebuilt on the other side. About 150 letters, small enough for a QR code
//   that a tablet camera can read.
// Long form:    CB1  O|A  L|W  Z|R  payload
//   The whole description, squeezed (Z = deflate) or plain (R). Used only when a description has something the short form
//   can't carry, and still understood when pasted.
//
//   O = invite, A = reply.  L = same Wi-Fi only, W = also asks a public lookup server for the outside address (far away).

export class SignalError extends Error {}

const CODE = /CB1[OA][LW][ZR][A-Za-z0-9_-]+|CB2[OA][LW][A-Za-z0-9_-]+/;
const te = new TextEncoder();
const td = new TextDecoder();

function toBase64Url(bytes) {
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64Url(text) {
  const b64 = text.replace(/-/g, "+").replace(/_/g, "/");
  const bin = atob(b64 + "=".repeat((4 - (b64.length % 4)) % 4));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

async function pipe(bytes, stream) {
  const out = new Blob([bytes]).stream().pipeThrough(stream);
  return new Uint8Array(await new Response(out).arrayBuffer());
}

const canSqueeze = () => typeof CompressionStream === "function" && typeof DecompressionStream === "function";

// ---------------------------------------------------------------- the short form

const SETUP = ["actpass", "active", "passive"];
const KIND = { IPV4: 0, IPV6: 1, MDNS: 2, TEXT: 3 };
const TYPE = ["host", "srflx"];
const MDNS = /^([0-9a-f]{8})-([0-9a-f]{4})-([0-9a-f]{4})-([0-9a-f]{4})-([0-9a-f]{12})\.local$/i;

/** Lines the short form leaves out because it writes them back the same way every time. */
const REBUILT = [
  /^v=0$/, /^o=/, /^s=/, /^t=0 0$/, /^a=group:BUNDLE 0$/, /^a=extmap-allow-mixed$/, /^a=msid-semantic:/, /^c=IN IP4 0\.0\.0\.0$/,
  /^a=ice-options:/, /^a=mid:0$/, /^a=sctp-port:5000$/, /^a=max-message-size:/, /^a=sendrecv$/, /^a=end-of-candidates$/,
];
const DATA_CHANNEL = /^m=application \d+ UDP\/DTLS\/SCTP webrtc-datachannel$/;

function parseIPv6(text) {
  if (!/^[0-9a-f:]+$/i.test(text) || (text.match(/::/g) ?? []).length > 1) return null;
  const [head, tail] = text.split("::");
  const a = head ? head.split(":") : [];
  const b = tail === undefined ? [] : tail ? tail.split(":") : [];
  const missing = 8 - a.length - b.length;
  if (tail === undefined ? a.length !== 8 : missing < 1) return null;
  const groups = [...a, ...Array(tail === undefined ? 0 : missing).fill("0"), ...b];
  if (groups.length !== 8 || groups.some((g) => g === "" || g.length > 4)) return null;
  const out = new Uint8Array(16);
  groups.forEach((g, i) => { const v = Number.parseInt(g, 16); out[2 * i] = v >> 8; out[2 * i + 1] = v & 255; });
  return out;
}

/** An address as { kind, bytes } (a name we don't recognise is kept as text, so nothing is lost). */
function parseAddress(text) {
  const v4 = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(text);
  if (v4 && v4.slice(1).every((n) => Number(n) <= 255)) return { kind: KIND.IPV4, bytes: Uint8Array.from(v4.slice(1), Number) };
  const m = MDNS.exec(text);
  if (m) return { kind: KIND.MDNS, bytes: Uint8Array.from(hexToBytes(m.slice(1).join(""))) };
  if (text.includes(":")) {
    const v6 = parseIPv6(text);
    if (v6) return { kind: KIND.IPV6, bytes: v6 };
  }
  const bytes = te.encode(text);
  return bytes.length > 0 && bytes.length < 256 ? { kind: KIND.TEXT, bytes } : null;
}

function hexToBytes(hex) {
  return Array.from({ length: hex.length / 2 }, (_, i) => Number.parseInt(hex.slice(2 * i, 2 * i + 2), 16));
}

const hex = (bytes) => Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

function formatAddress(kind, bytes) {
  switch (kind) {
    case KIND.IPV4: return Array.from(bytes).join(".");
    case KIND.IPV6: return Array.from({ length: 8 }, (_, i) => ((bytes[2 * i] << 8) | bytes[2 * i + 1]).toString(16)).join(":");
    case KIND.MDNS: { const h = hex(bytes); return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}.local`; }
    default: return td.decode(bytes);
  }
}

function parseCandidate(line) {
  const t = line.slice(2).split(" "); // drop "a="
  if (!/^candidate:\S+$/.test(t[0]) || t.length < 8 || t[1] !== "1" || t[2].toLowerCase() !== "udp" || t[6] !== "typ") return null;
  const type = TYPE.indexOf(t[7]);
  const priority = Number(t[3]), port = Number(t[5]);
  const address = parseAddress(t[4]);
  if (type < 0 || !address || !Number.isInteger(priority) || priority < 0 || priority > 0xffffffff || !Number.isInteger(port) || port < 0 || port > 65535) return null;
  let related = null;
  for (let i = 8; i < t.length; i += 2) {
    const key = t[i], value = t[i + 1];
    if (value === undefined) return null;
    if (key === "raddr") related = { address: parseAddress(value), port: related?.port ?? 0 };
    else if (key === "rport") related = { address: related?.address ?? null, port: Number(value) };
    else if (!["generation", "ufrag", "network-id", "network-cost"].includes(key)) return null;
  }
  if (type === 1 && related && (!related.address || !Number.isInteger(related.port) || related.port < 0 || related.port > 65535)) return null;
  if (type === 0 && related) return null;
  return { type, address, port, priority, related };
}

/**
 * Packs a data-channel description into bytes, or returns null when it holds anything the short form can't carry
 * (the caller then uses the long form).
 */
export function compactSdp(sdp) {
  let ufrag = null, pwd = null, fingerprint = null, setup = null, sawData = false;
  const candidates = [];
  for (const line of sdp.split(/\r?\n/).filter(Boolean)) {
    let m;
    if (DATA_CHANNEL.test(line)) {
      if (sawData) return null;
      sawData = true;
    } else if ((m = /^a=ice-ufrag:(\S+)$/.exec(line))) ufrag = te.encode(m[1]);
    else if ((m = /^a=ice-pwd:(\S+)$/.exec(line))) pwd = te.encode(m[1]);
    else if ((m = /^a=fingerprint:sha-256 ((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})$/.exec(line))) fingerprint = Uint8Array.from(m[1].split(":"), (h) => Number.parseInt(h, 16));
    else if ((m = /^a=setup:(actpass|active|passive)$/.exec(line))) setup = SETUP.indexOf(m[1]);
    else if (line.startsWith("a=candidate:")) {
      const c = parseCandidate(line);
      if (!c) return null;
      candidates.push(c);
    } else if (!REBUILT.some((re) => re.test(line))) return null;
  }
  if (!sawData || !ufrag || !pwd || !fingerprint || setup === null || ufrag.length > 255 || pwd.length > 255 || candidates.length > 255) return null;

  const out = [setup, ufrag.length, ...ufrag, pwd.length, ...pwd, ...fingerprint, candidates.length];
  const address = ({ kind, bytes }) => out.push(kind, ...(kind === KIND.TEXT ? [bytes.length] : []), ...bytes);
  const u16 = (n) => out.push(n >> 8, n & 255);
  for (const c of candidates) {
    out.push(c.type);
    address(c.address);
    u16(c.port);
    out.push((c.priority >>> 24) & 255, (c.priority >>> 16) & 255, (c.priority >>> 8) & 255, c.priority & 255);
    if (c.type === 1) {
      out.push(c.related ? 1 : 0);
      if (c.related) { address(c.related.address); u16(c.related.port); }
    }
  }
  return Uint8Array.from(out);
}

/** The standard description text, rebuilt from the packed bytes. */
export function expandSdp(bytes) {
  let at = 0;
  const need = (n) => { if (at + n > bytes.length) throw new SignalError("That code is cut short. Copy the whole code again."); };
  const byte = () => { need(1); return bytes[at++]; };
  const take = (n) => { need(n); const out = bytes.subarray(at, at + n); at += n; return out; };
  const u16 = () => (byte() << 8) | byte();
  const address = () => {
    const kind = byte();
    if (kind > KIND.TEXT) throw new SignalError("That code has a typo in it. Copy the whole code again.");
    const len = kind === KIND.IPV4 ? 4 : kind === KIND.TEXT ? byte() : 16;
    return formatAddress(kind, take(len));
  };

  const setup = SETUP[byte()];
  if (!setup) throw new SignalError("That code has a typo in it. Copy the whole code again.");
  const ufrag = td.decode(take(byte()));
  const pwd = td.decode(take(byte()));
  const fingerprint = Array.from(take(32), (b) => b.toString(16).padStart(2, "0").toUpperCase()).join(":");
  const lines = [];
  for (let i = byte(); i > 0; i--) {
    const type = byte();
    if (type > 1) throw new SignalError("That code has a typo in it. Copy the whole code again.");
    const addr = address(), port = u16();
    const priority = ((byte() << 24) | (byte() << 16) | (byte() << 8) | byte()) >>> 0;
    let line = `a=candidate:${lines.length + 1} 1 udp ${priority} ${addr} ${port} typ ${TYPE[type]}`;
    if (type === 1 && byte() === 1) line += ` raddr ${address()} rport ${u16()}`;
    lines.push(`${line} generation 0`);
  }
  return [
    "v=0", "o=- 1 1 IN IP4 127.0.0.1", "s=-", "t=0 0", "a=group:BUNDLE 0",
    "m=application 9 UDP/DTLS/SCTP webrtc-datachannel", "c=IN IP4 0.0.0.0",
    ...lines, "a=end-of-candidates",
    `a=ice-ufrag:${ufrag}`, `a=ice-pwd:${pwd}`, `a=fingerprint:sha-256 ${fingerprint}`,
    `a=setup:${setup}`, "a=mid:0", "a=sctp-port:5000", "a=max-message-size:262144", "",
  ].join("\r\n");
}

// ---------------------------------------------------------------- codes

/** Turns a description into a code. role is "offer" or "answer". */
export async function encodeSignal({ role, wide, sdp }) {
  const head = (role === "offer" ? "O" : "A") + (wide ? "W" : "L");
  const short = compactSdp(sdp);
  if (short) return "CB2" + head + toBase64Url(short);
  const raw = te.encode(sdp);
  const squeezed = canSqueeze();
  const body = squeezed ? await pipe(raw, new CompressionStream("deflate-raw")) : raw;
  return "CB1" + head + (squeezed ? "Z" : "R") + toBase64Url(body);
}

/** Pulls the code out of whatever was pasted: the bare code, a whole invite link, or a code with a message around it. */
export function extractCode(pasted) {
  const m = CODE.exec(String(pasted ?? ""));
  return m ? m[0] : null;
}

/** The other way round. Throws SignalError with a sentence a grown-up can act on. */
export async function decodeSignal(pasted) {
  const code = extractCode(pasted);
  if (!code) throw new SignalError("That doesn't look like a Chess Buddies code. Copy the whole code and try again.");
  const role = code[3] === "O" ? "offer" : "answer";
  const wide = code[4] === "W";
  const short = code.startsWith("CB2");
  let body;
  try {
    body = fromBase64Url(code.slice(short ? 5 : 6));
  } catch {
    throw new SignalError("That code has a typo in it. Copy the whole code again.");
  }
  let sdp;
  if (short) {
    sdp = expandSdp(body);
  } else {
    if (code[5] === "Z") {
      if (!canSqueeze()) throw new SignalError("This browser is too old to open that code. Try a newer browser.");
      try {
        body = await pipe(body, new DecompressionStream("deflate-raw"));
      } catch {
        throw new SignalError("That code is cut short. Copy the whole code again.");
      }
    }
    sdp = td.decode(body);
    if (!sdp.startsWith("v=0")) throw new SignalError("That code has a typo in it. Copy the whole code again.");
  }
  return { role, wide, sdp };
}

/** The link a host can send so a friend lands straight on the "join" step. */
export function inviteLink(code, base = globalThis.location?.href ?? "") {
  const url = new URL(base, "https://example.invalid/");
  url.hash = `join=${code}`;
  url.search = "";
  return url.href;
}

/** The invite code in the page's address (#join=...), if there is one. */
export function codeFromHash(hash) {
  const m = /^#join=(.+)$/.exec(hash ?? "");
  return m ? extractCode(decodeURIComponent(m[1])) : null;
}
