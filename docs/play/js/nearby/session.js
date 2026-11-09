// One game between two browsers, from either side. Port of NearbySession.kt (and Pairing.kt's constants).
//
// The host waits for a friend; the guest connects. After the handshake the two are equals: each browser keeps its own
// copy of the game and they swap moves. This file knows nothing about chess or checkers: a move is a short piece of text
// (its "wire" name) and a move number. It is driven by calling update(now) about twenty times a second and reports what
// happened through events: { type: "joined" | "resumed" | "lost" | "move" | ... }.
//
// Differences from the Android version, because a browser can't open sockets and the two sides pair by swapping an
// invite code instead of tapping four pictures:
//   - there is no picture code, proof or lock-out: only the person who was sent the invite can complete the connection;
//   - a link is handed in by a "source" (see rtc.js) rather than found by the session;
//   - a quiet link is only "lost" for the moment. If messages start flowing again the game resumes by itself; only a link
//     that has really closed needs a new invite.
// The two apps can't play each other (different transports), so the message shapes are free to differ.

export const Nearby = Object.freeze({
  VERSION: 1,
  HEARTBEAT_MS: 1000,
  /** No news for this long means the other browser has gone quiet. */
  LOST_AFTER_MS: 6000,
  HANDSHAKE_TIMEOUT_MS: 12_000,
  CHESS: "chess",
  CHECKERS: "checkers",
});

export const State = Object.freeze({ WAITING: "waiting", LIVE: "live", LOST: "lost", CLOSED: "closed" });

/** Messages are one line each, fields separated by "|". Move names, numbers and hex strings never contain one. */
export const Wire = Object.freeze({
  msg: (...parts) => parts.join("|"),
  parse: (line) => line.split("|"),
});

export function newToken() {
  const bytes = new Uint8Array(8);
  globalThis.crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * A two-way connection carrying one line of text per message. Nothing blocks: poll() returns null when nothing has arrived.
 *   alive: boolean   send(line)   poll(): string | null   close()
 * A source hands out links as they connect: poll(): Link | null, and stop().
 */
export class NearbySession {
  /** Wait for a friend. kind is Nearby.CHESS or Nearby.CHECKERS; hostIsFirst means the host plays White or Red. */
  static host(kind, hostIsFirst, source) { return new NearbySession(true, kind, hostIsFirst, source); }

  /** Join a game. The source hands over the link once the connection is open (and again after a reconnect). */
  static guest(source) { return new NearbySession(false, "", true, source); }

  constructor(isHost, kind, hostIsFirst, source) {
    this.isHost = isHost;
    /** "chess" or "checkers". A guest learns it from the host. */
    this.kind = kind;
    this.state = State.WAITING;
    /** True when this browser plays White (chess) or Red (checkers). A guest learns it when it joins. */
    this.iAmFirst = isHost ? hostIsFirst : !hostIsFirst;
    /** Asked for when the browsers (re)join, so the other side can catch up. Set by whoever owns the game. */
    this.moveSource = () => [];

    this.source = source;
    this.sink = null;
    this.queue = [];
    this.link = null;
    this.lastRecv = 0;
    this.lastHb = 0;
    this.everLive = false;
    this.iAsked = false;
    this.theyAsked = false;
    this.token = isHost ? newToken() : "";
    // host only
    this.pending = [];
    this.joinedOnce = false;
    // guest only
    this.handshakeDone = false;
    this.linkSince = 0;
  }

  get live() { return this.state === State.LIVE; }

  /**
   * Starts receiving events. Anything that arrived while nobody was listening is delivered first, in order.
   * A handler may call attach(null) from inside an event to hold the rest back for the next listener.
   */
  attach(sink) {
    this.sink = sink;
    while (this.sink && this.queue.length) this.sink(this.queue.shift());
  }

  #emit(e) {
    if (this.sink && this.queue.length === 0) this.sink(e); else this.queue.push(e);
  }

  // ---------------------------------------------------------------- what the game can ask for

  sendMove(n, wire) {
    if (this.state === State.LIVE) this.link?.send(Wire.msg("move", n, wire));
  }

  /** Tell the other browser every move this one knows about. */
  sendSync() { this.link?.send(Wire.msg("sync", this.moveSource().join(","))); }

  /** Ask the other browser for its full list of moves (used when a move arrives out of order). */
  requestSync() { this.link?.send("needsync"); }

  wantRematch() {
    if (this.state !== State.LIVE) return;
    this.link?.send("again");
    if (this.theyAsked) this.#startNewGame(); else this.iAsked = true;
  }

  declineRematch() {
    this.theyAsked = false;
    this.link?.send("no");
  }

  #startNewGame() {
    this.iAsked = false;
    this.theyAsked = false;
    this.iAmFirst = !this.iAmFirst;
    this.#emit({ type: "newGame", iAmFirst: this.iAmFirst });
  }

  /** Say goodbye and shut everything down. */
  leave() {
    if (this.state !== State.CLOSED) {
      this.link?.send("bye");
      this.link?.close();
    }
    this.link = null;
    for (const p of this.pending) p.link.close();
    this.pending = [];
    this.source?.stop();
    this.state = State.CLOSED;
  }

  // ---------------------------------------------------------------- the heartbeat

  update(now) {
    if (this.state === State.CLOSED) return;
    if (this.isHost) this.#updateHost(now); else this.#updateGuest(now);
  }

  #updateHost(now) {
    for (let l = this.source.poll(); l; l = this.source.poll()) {
      this.pending.push({ link: l, since: now });
      l.send(Wire.msg("hello", Nearby.VERSION, this.kind));
    }
    this.pending = this.pending.filter((p) => {
      for (let line = p.link.poll(); line !== null; line = p.link.poll()) {
        const m = Wire.parse(line);
        if (m[0] === "join") { this.#onJoin(p, m, now); return false; }
      }
      if (!p.link.alive || now - p.since > Nearby.HANDSHAKE_TIMEOUT_MS) { p.link.close(); return false; }
      return true;
    });
    this.#pollLink(now);
  }

  #updateGuest(now) {
    const l = this.source.poll();
    if (l) {
      this.link?.close();
      this.link = l;
      this.handshakeDone = false;
      this.linkSince = now;
      this.lastRecv = now;
    }
    this.#pollLink(now);
    if (this.state === State.CLOSED) return;
    if (this.link && !this.handshakeDone && now - this.linkSince > Nearby.HANDSHAKE_TIMEOUT_MS) {
      this.link.close();
      this.link = null;
      if (!this.everLive) this.#couldNotConnect();
    }
  }

  #couldNotConnect() {
    this.link?.close();
    this.link = null;
    this.source?.stop();
    this.state = State.CLOSED;
    this.#emit({ type: "couldNotConnect" });
  }

  #pollLink(now) {
    const l = this.link;
    if (!l) return;
    for (let line = l.poll(); line !== null; line = l.poll()) {
      this.lastRecv = now;
      const m = Wire.parse(line);
      if (!this.isHost && !this.handshakeDone) this.#handleHandshake(m, now); else this.#handleLive(m, now);
      if (this.state === State.CLOSED || this.link !== l) return;
    }
    if (!l.alive) { this.#lose(true); return; }
    if (this.state === State.LIVE || this.state === State.LOST) {
      if (now - this.lastHb >= Nearby.HEARTBEAT_MS) { l.send("hb"); this.lastHb = now; }
      if (this.state === State.LIVE && now - this.lastRecv > Nearby.LOST_AFTER_MS) this.#lose(false);
    }
  }

  /** hard: the link itself has closed, so it is gone for good. Otherwise the other side has only gone quiet. */
  #lose(hard) {
    if (hard) {
      this.link?.close();
      this.link = null;
      this.handshakeDone = false;
      if (!this.isHost && !this.everLive) { this.#couldNotConnect(); return; }
    }
    this.iAsked = false;
    this.theyAsked = false;
    if (this.state === State.LIVE) {
      this.state = State.LOST;
      this.#emit({ type: "lost" });
    } else if (hard && this.state === State.LOST) {
      this.#emit({ type: "lost" }); // a quiet link has now really closed: say so again so the screen can update its note
    }
  }

  /** True when the link has closed for good, so only a fresh invite could bring the friend back. */
  get disconnected() { return this.state === State.LOST && this.link === null; }

  // ---------------------------------------------------------------- joining

  #onJoin(p, m, now) {
    if (Number(m[1]) !== Nearby.VERSION) return this.#reject(p, "version");
    // Once somebody has joined, only that same browser may come back.
    if (this.joinedOnce && (m[2] ?? "") !== this.token) return this.#reject(p, "busy");
    this.link?.close();
    this.link = p.link;
    this.lastRecv = now;
    this.lastHb = now;
    const first = !this.joinedOnce;
    this.joinedOnce = true;
    this.state = State.LIVE;
    this.iAsked = false;
    this.theyAsked = false;
    p.link.send(Wire.msg("welcome", this.iAmFirst ? 0 : 1, this.token, this.kind));
    this.#emit(first ? { type: "joined", iAmFirst: this.iAmFirst, kind: this.kind } : { type: "resumed" });
    this.sendSync();
  }

  #reject(p, reason) {
    p.link.send(Wire.msg("reject", reason));
    p.link.close();
  }

  #handleHandshake(m, now) {
    switch (m[0]) {
      case "hello":
        if (Number(m[1]) !== Nearby.VERSION) { this.#rejectedByHost("version"); return; }
        if (m[2]) this.kind = m[2];
        this.link?.send(Wire.msg("join", Nearby.VERSION, this.token));
        break;
      case "welcome": {
        this.iAmFirst = m[1] === "1";
        this.token = m[2] ?? "";
        if (m[3]) this.kind = m[3];
        this.handshakeDone = true;
        this.lastRecv = now;
        this.lastHb = now;
        this.state = State.LIVE;
        const first = !this.everLive;
        this.everLive = true;
        this.#emit(first ? { type: "joined", iAmFirst: this.iAmFirst, kind: this.kind } : { type: "resumed" });
        this.sendSync();
        break;
      }
      case "reject":
        this.#rejectedByHost(m[1] || "busy");
        break;
    }
  }

  #rejectedByHost(reason) {
    this.link?.close();
    this.link = null;
    this.source?.stop();
    this.state = State.CLOSED;
    this.#emit({ type: "rejected", reason });
  }

  // ---------------------------------------------------------------- playing

  #handleLive(m, now) {
    // Anything from the other side proves it is back after a quiet spell.
    if (this.state === State.LOST && m[0] !== "bye") {
      this.state = State.LIVE;
      this.lastHb = now;
      this.#emit({ type: "resumed" });
      // The other side may not have noticed the gap, so it won't offer its list on its own: swap lists both ways.
      this.sendSync();
      this.requestSync();
    }
    switch (m[0]) {
      case "move": {
        const n = Number.parseInt(m[1], 10);
        const wire = m[2];
        if (Number.isInteger(n) && wire && this.state === State.LIVE) this.#emit({ type: "move", n, wire });
        break;
      }
      case "sync":
        this.#emit({ type: "sync", moves: m[1] ? m[1].split(",") : [] });
        break;
      case "needsync":
        this.sendSync();
        break;
      case "again":
        if (this.iAsked) this.#startNewGame();
        else { this.theyAsked = true; this.#emit({ type: "rematchAsked" }); }
        break;
      case "no":
        if (this.iAsked) { this.iAsked = false; this.#emit({ type: "rematchDeclined" }); }
        break;
      case "bye":
        this.link?.close();
        this.link = null;
        this.source?.stop();
        this.state = State.CLOSED;
        this.#emit({ type: "friendLeft" });
        break;
    }
  }
}

/**
 * What a browser should do with the other one's full list of moves.
 *   { type: "keep" }                 nothing to do
 *   { type: "append", moves }        the other side is ahead: play these extra moves
 *   { type: "replace", moves }       the games disagree and this is the guest, so start over from the host's list
 */
export function reconcile(local, remote, iAmHost) {
  const sameStart = (a, b) => a.every((m, i) => m === b[i]);
  if (remote.length === local.length && sameStart(local, remote)) return { type: "keep" };
  if (remote.length > local.length && sameStart(local, remote)) return { type: "append", moves: remote.slice(local.length) };
  if (local.length >= remote.length && sameStart(remote, local)) return { type: "keep" };
  if (iAmHost) return { type: "keep" };
  return { type: "replace", moves: remote };
}

/** Calls session.update about twenty times a second until the session closes. Returns a function that stops early. */
export function runSession(session, { now = () => performance.now(), everyMs = 50 } = {}) {
  const timer = setInterval(() => {
    session.update(now());
    if (session.state === State.CLOSED) clearInterval(timer);
  }, everyMs);
  return () => clearInterval(timer);
}
