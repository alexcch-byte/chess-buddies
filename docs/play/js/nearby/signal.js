// Invite and reply codes: how two browsers find each other without a server.
//
// WebRTC needs each side to hand the other a small description of itself (an "offer" and an "answer"). Normally a server
// relays them. Here a grown-up copies them across instead: the host's code is the invite, the friend's code is the reply.
// A code is the description squeezed down and written in URL-safe letters, so it can also ride along in a link.
//
//   CB1  O|A  L|W  Z|R  payload
//        |    |    |
//        |    |    Z = squeezed (deflate), R = plain
//        |    L = same Wi-Fi only, W = also asks a public lookup server for the outside address (far away friends)
//        O = invite, A = reply

export class SignalError extends Error {}

const PREFIX = "CB1";

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

/** Turns a description into a code. role is "offer" or "answer". */
export async function encodeSignal({ role, wide, sdp }) {
  const raw = new TextEncoder().encode(sdp);
  const squeezed = canSqueeze();
  const body = squeezed ? await pipe(raw, new CompressionStream("deflate-raw")) : raw;
  return PREFIX + (role === "offer" ? "O" : "A") + (wide ? "W" : "L") + (squeezed ? "Z" : "R") + toBase64Url(body);
}

/** Pulls the code out of whatever was pasted: the bare code, a whole invite link, or a code with a message around it. */
export function extractCode(pasted) {
  const m = /CB1[OA][LW][ZR][A-Za-z0-9_-]+/.exec(String(pasted ?? ""));
  return m ? m[0] : null;
}

/** The other way round. Throws SignalError with a sentence a grown-up can act on. */
export async function decodeSignal(pasted) {
  const code = extractCode(pasted);
  if (!code) throw new SignalError("That doesn't look like a Chess Buddies code. Copy the whole code and try again.");
  const role = code[3] === "O" ? "offer" : "answer";
  const wide = code[4] === "W";
  const squeezed = code[5] === "Z";
  let body;
  try {
    body = fromBase64Url(code.slice(6));
  } catch {
    throw new SignalError("That code has a typo in it. Copy the whole code again.");
  }
  if (squeezed) {
    if (!canSqueeze()) throw new SignalError("This browser is too old to open that code. Try a newer browser.");
    try {
      body = await pipe(body, new DecompressionStream("deflate-raw"));
    } catch {
      throw new SignalError("That code is cut short. Copy the whole code again.");
    }
  }
  const sdp = new TextDecoder().decode(body);
  if (!sdp.startsWith("v=0")) throw new SignalError("That code has a typo in it. Copy the whole code again.");
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
