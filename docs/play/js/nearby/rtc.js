// The connection itself: a WebRTC data channel between two browsers, set up by swapping an invite code and a reply code
// (see signal.js). No game data goes through any server. By default nothing is contacted at all, so both browsers have to
// be on the same Wi-Fi. "Far away" mode also asks a public lookup server (Google's STUN) for each browser's outside address.
import { encodeSignal, decodeSignal, SignalError } from "./signal.js";

const STUN = [{ urls: "stun:stun.l.google.com:19302" }];
const GATHER_MS = 4000;

/** True when this browser can do this at all. */
export const nearbySupported = (win = globalThis) => typeof win.RTCPeerConnection === "function";

const config = (wide) => ({ iceServers: wide ? STUN : [] });

/** Waits until the browser has listed every way it can be reached (or gives up waiting, keeping what it has). */
function gathered(pc, timeoutMs = GATHER_MS) {
  return new Promise((resolve) => {
    if (pc.iceGatheringState === "complete") { resolve(); return; }
    const finish = () => { clearTimeout(timer); pc.removeEventListener("icegatheringstatechange", onChange); resolve(); };
    const onChange = () => { if (pc.iceGatheringState === "complete") finish(); };
    const timer = setTimeout(finish, timeoutMs);
    pc.addEventListener("icegatheringstatechange", onChange);
  });
}

/** A data channel dressed up as a Link (see session.js): one line of text per message, and poll() instead of callbacks. */
class ChannelLink {
  constructor(pc, channel) {
    this.pc = pc;
    this.channel = channel;
    this.inbox = [];
    this.closed = false;
    channel.onmessage = (e) => { if (typeof e.data === "string") this.inbox.push(e.data); };
    channel.onclose = () => { this.closed = true; };
    channel.onerror = () => { this.closed = true; };
    // A dropped Wi-Fi connection can take a while to close the channel, but the connection itself says so sooner.
    pc.addEventListener("connectionstatechange", () => {
      if (pc.connectionState === "failed" || pc.connectionState === "closed") this.closed = true;
    });
  }

  get open() { return this.channel.readyState === "open"; }
  get alive() { return !this.closed && (this.channel.readyState === "open" || this.channel.readyState === "connecting"); }

  send(line) {
    if (this.channel.readyState !== "open") return;
    try { this.channel.send(line); } catch { /* the channel closed under us; alive will say so */ }
  }

  poll() { return this.inbox.length ? this.inbox.shift() : null; }

  /** Lets anything already sent go out, then hangs up. */
  close() {
    if (this.closed) return;
    this.closed = true;
    try { this.channel.close(); } catch { /* already closed */ }
    setTimeout(() => { try { this.pc.close(); } catch { /* already closed */ } }, 1000);
  }
}

/**
 * The host's side. createInvite() makes a code for one friend; acceptReply() takes the code they send back. When the
 * connection opens, poll() hands the link to the session. Call createInvite() again to let a friend (back) in.
 */
export class RtcHost {
  constructor({ wide = false } = {}) {
    this.wide = wide;
    this.pc = null;
    this.waiting = null; // the link for the invite that is out right now, until it opens
  }

  async createInvite() {
    this.#drop();
    const pc = new RTCPeerConnection(config(this.wide));
    this.pc = pc;
    this.waiting = new ChannelLink(pc, pc.createDataChannel("chessbuddies", { ordered: true }));
    await pc.setLocalDescription(await pc.createOffer());
    await gathered(pc);
    return encodeSignal({ role: "offer", wide: this.wide, sdp: pc.localDescription.sdp });
  }

  async acceptReply(pasted) {
    const s = await decodeSignal(pasted);
    if (s.role !== "answer") throw new SignalError("That is an invite, not a reply. Paste the code your friend sent back.");
    if (!this.pc || this.pc.signalingState !== "have-local-offer") throw new SignalError("This invite was already used. Make a new invite.");
    try {
      await this.pc.setRemoteDescription({ type: "answer", sdp: s.sdp });
    } catch {
      throw new SignalError("That reply doesn't match this invite. Check your friend used the newest invite.");
    }
  }

  poll() {
    const l = this.waiting;
    if (l && l.open) { this.waiting = null; return l; }
    return null;
  }

  stop() { this.#drop(); }

  #drop() {
    if (this.waiting) this.waiting.close();
    this.waiting = null;
    this.pc = null;
  }
}

/** The friend's side. join() takes the invite code and returns the reply code; poll() hands over the link once it opens. */
export class RtcGuest {
  constructor() {
    this.pc = null;
    this.waiting = null;
  }

  async join(pasted) {
    const s = await decodeSignal(pasted);
    if (s.role !== "offer") throw new SignalError("That is a reply, not an invite. Paste the invite your friend sent you.");
    this.#drop();
    const pc = new RTCPeerConnection(config(s.wide));
    this.pc = pc;
    pc.ondatachannel = (e) => { this.waiting = new ChannelLink(pc, e.channel); };
    try {
      await pc.setRemoteDescription({ type: "offer", sdp: s.sdp });
    } catch {
      throw new SignalError("That invite didn't work. Ask your friend for a new one.");
    }
    await pc.setLocalDescription(await pc.createAnswer());
    await gathered(pc);
    return encodeSignal({ role: "answer", wide: s.wide, sdp: pc.localDescription.sdp });
  }

  poll() {
    const l = this.waiting;
    if (l && l.open) { this.waiting = null; return l; }
    return null;
  }

  stop() { this.#drop(); }

  #drop() {
    if (this.waiting) this.waiting.close();
    this.waiting = null;
    try { this.pc?.close(); } catch { /* already closed */ }
    this.pc = null;
  }
}
