// Tests for Play Nearby in the browser: the session (a port of NearbySessionTest.kt), the move glue, and the invite codes.
// The WebRTC link itself needs a real browser, so these run the session over a pair of in-memory links.
// Run with: node --test web-tests/nearby.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { NearbySession, Nearby, State, Wire, reconcile } from "../docs/play/js/nearby/session.js";
import { NearbyMatch } from "../docs/play/js/nearby/match-link.js";
import { encodeSignal, decodeSignal, extractCode, inviteLink, codeFromHash, SignalError } from "../docs/play/js/nearby/signal.js";
import { ChessMatchController } from "../docs/play/js/controllers/match-chess.js";
import { CheckersMatchController } from "../docs/play/js/controllers/match-checkers.js";
import { uci } from "../docs/play/js/chess/position.js";

// ---------- in-memory links ----------

class MemLink {
  constructor() { this.inbox = []; this.peer = null; this.open = true; }
  get alive() { return this.open; }
  send(line) { if (this.open && this.peer?.open) this.peer.inbox.push(line); }
  poll() { return this.inbox.length ? this.inbox.shift() : null; }
  /** Sends what is already queued (it was delivered when sent), then hangs up both ends. */
  close() { this.open = false; if (this.peer) this.peer.open = false; }
}

function linkPair() {
  const a = new MemLink(), b = new MemLink();
  a.peer = b; b.peer = a;
  return [a, b];
}

/** A source that hands out the links pushed into it. */
class MemSource {
  constructor() { this.queue = []; this.stopped = false; }
  push(link) { this.queue.push(link); }
  poll() { return this.queue.shift() ?? null; }
  stop() { this.stopped = true; }
}

class Inbox {
  constructor(session) { this.events = []; session.attach((e) => this.events.push(e)); }
  has(type) { return this.events.some((e) => e.type === type); }
  count(type) { return this.events.filter((e) => e.type === type).length; }
  first(type) { return this.events.find((e) => e.type === type); }
  last(type) { return this.events.filter((e) => e.type === type).at(-1); }
}

/** A host and a guest, with the guest's link already connected, and a clock the test can push forward. */
function setup({ hostIsFirst = true, kind = Nearby.CHESS } = {}) {
  const hostSource = new MemSource();
  const guestSource = new MemSource();
  const host = NearbySession.host(kind, hostIsFirst, hostSource);
  const guest = NearbySession.guest(guestSource);
  const world = { now: 0, host, guest, hostSource, guestSource, hostInbox: null, guestInbox: null };
  world.hostInbox = new Inbox(host);
  world.guestInbox = new Inbox(guest);
  world.tick = (ms = 50, sessions = [host, guest]) => { world.now += ms; for (const s of sessions) s.update(world.now); };
  world.run = (n = 10, ms = 50, sessions) => { for (let i = 0; i < n; i++) world.tick(ms, sessions); };
  /** Connects a fresh link between the two. Returns the guest's end. */
  world.connect = () => {
    const [h, g] = linkPair();
    hostSource.push(h);
    guestSource.push(g);
    return g;
  };
  return world;
}

function joined(opts) {
  const w = setup(opts);
  w.guestLink = w.connect();
  w.run(10);
  assert.ok(w.host.live && w.guest.live, "both sides should be live");
  return w;
}

// ---------- joining ----------

test("joining works and the guest learns the game and its side", () => {
  const w = joined({ hostIsFirst: true, kind: Nearby.CHECKERS });
  assert.equal(w.hostInbox.first("joined").iAmFirst, true);
  assert.equal(w.guestInbox.first("joined").iAmFirst, false);
  assert.equal(w.guestInbox.first("joined").kind, Nearby.CHECKERS);
  assert.equal(w.guest.kind, Nearby.CHECKERS);
});

test("the host can play the second side", () => {
  const w = joined({ hostIsFirst: false });
  assert.equal(w.hostInbox.first("joined").iAmFirst, false);
  assert.equal(w.guestInbox.first("joined").iAmFirst, true);
});

test("a different protocol version is refused and the host keeps waiting", () => {
  const w = setup();
  const [h, g] = linkPair();
  w.hostSource.push(h);
  w.run(2, 50, [w.host]);
  assert.ok(g.poll().startsWith(`hello|${Nearby.VERSION}|chess`));
  g.send(Wire.msg("join", 99, ""));
  w.run(2, 50, [w.host]);
  assert.equal(g.poll(), "reject|version");
  assert.equal(w.host.state, State.WAITING);
  // A browser with the right version can still join afterwards.
  w.connect();
  w.run(10);
  assert.ok(w.host.live);
});

test("a third browser is told the game is full", () => {
  const w = joined();
  const [h, g] = linkPair();
  w.hostSource.push(h);
  w.run(2);
  assert.ok(g.poll().startsWith("hello|"));
  g.send(Wire.msg("join", Nearby.VERSION, "somebody-else"));
  w.run(2);
  assert.equal(g.poll(), "reject|busy");
  assert.ok(w.host.live && w.guest.live);
});

test("a guest whose host never says hello gives up", () => {
  const w = setup();
  const [, g] = linkPair(); // the other end is never given to the host
  w.guestSource.push(g);
  w.run(3);
  w.tick(Nearby.HANDSHAKE_TIMEOUT_MS + 100);
  assert.ok(w.guestInbox.has("couldNotConnect"));
  assert.equal(w.guest.state, State.CLOSED);
});

test("a guest whose link dies before the handshake can't connect", () => {
  const w = setup();
  const [, g] = linkPair();
  w.guestSource.push(g);
  w.run(2);
  g.close();
  w.run(2);
  assert.ok(w.guestInbox.has("couldNotConnect"));
});

test("a host that rejects with an old version tells the guest", () => {
  const w = setup();
  const [h, g] = linkPair();
  w.guestSource.push(g);
  w.run(1);
  h.send(Wire.msg("hello", 99, "chess"));
  w.run(2);
  assert.equal(w.guestInbox.first("rejected").reason, "version");
  assert.equal(w.guest.state, State.CLOSED);
});

// ---------- playing ----------

test("moves travel both ways", () => {
  const w = joined();
  w.host.sendMove(1, "e2e4");
  w.run(2);
  assert.deepEqual(w.guestInbox.first("move"), { type: "move", n: 1, wire: "e2e4" });
  w.guest.sendMove(2, "e7e5");
  w.run(2);
  assert.deepEqual(w.hostInbox.first("move"), { type: "move", n: 2, wire: "e7e5" });
});

test("moves that arrive while nobody listens wait for the next listener", () => {
  const w = setup();
  const lobby = [];
  // Like the lobby screen: it takes the joined event, then steps aside for the match screen.
  w.guest.attach((e) => { lobby.push(e); if (e.type === "joined") w.guest.attach(null); });
  w.connect();
  w.run(10);
  w.host.sendMove(1, "d2d4");
  w.run(4);
  assert.equal(lobby.length, 1);
  const match = [];
  w.guest.attach((e) => match.push(e));
  assert.ok(match.some((e) => e.type === "move" && e.n === 1 && e.wire === "d2d4"));
});

test("each side is told the other's full list of moves when it joins", () => {
  const w = setup();
  w.host.moveSource = () => ["e2e4", "e7e5", "g1f3"];
  w.guest.moveSource = () => ["e2e4", "e7e5"];
  w.connect();
  w.run(10);
  assert.deepEqual(w.guestInbox.last("sync").moves, ["e2e4", "e7e5", "g1f3"]);
  assert.deepEqual(w.hostInbox.last("sync").moves, ["e2e4", "e7e5"]);
});

test("a browser can ask the other for its whole list of moves", () => {
  const w = joined();
  w.host.moveSource = () => ["a2a3"];
  w.guest.requestSync();
  w.run(3);
  assert.deepEqual(w.guestInbox.last("sync").moves, ["a2a3"]);
});

// ---------- dropping out and coming back ----------

test("a quiet link is noticed, and the game resumes by itself when the other side speaks again", () => {
  const w = joined();
  w.host.moveSource = () => ["e2e4", "e7e5", "g1f3"];
  w.guest.moveSource = () => ["e2e4", "e7e5"];
  // Only the host keeps running; the guest's heartbeats stop (a locked screen, say).
  w.tick(Nearby.LOST_AFTER_MS + 1000, [w.host]);
  assert.ok(w.hostInbox.has("lost"));
  assert.equal(w.host.state, State.LOST);
  // The guest wakes up and the two carry on, with no new invite.
  w.run(60);
  assert.ok(w.host.live && w.guest.live);
  assert.equal(w.hostInbox.count("resumed"), 1);
  assert.equal(w.hostInbox.count("joined"), 1);
  assert.deepEqual(w.hostInbox.last("sync").moves, ["e2e4", "e7e5"]);
  assert.deepEqual(w.guestInbox.last("sync").moves, ["e2e4", "e7e5", "g1f3"]);
});

test("a link that really closed needs a new invite, and only the same browser may use it", () => {
  const w = joined();
  w.guestLink.close();
  w.run(3);
  assert.equal(w.host.state, State.LOST);
  assert.equal(w.guest.state, State.LOST);
  assert.ok(w.hostInbox.has("lost") && w.guestInbox.has("lost"));

  // A different browser can't take the seat.
  const [hx, gx] = linkPair();
  w.hostSource.push(hx);
  w.run(2);
  gx.poll();
  gx.send(Wire.msg("join", Nearby.VERSION, ""));
  w.run(2);
  assert.equal(gx.poll(), "reject|busy");

  // The original guest comes back through a new invite and is let in without a new "joined".
  w.connect();
  w.run(10);
  assert.ok(w.host.live && w.guest.live);
  assert.equal(w.hostInbox.count("joined"), 1);
  assert.equal(w.hostInbox.count("resumed"), 1);
  assert.equal(w.guestInbox.count("resumed"), 1);
});

// ---------- playing again ----------

test("playing again needs both players and swaps sides each time", () => {
  const w = joined({ hostIsFirst: true });
  w.guest.wantRematch();
  w.run(3);
  assert.ok(w.hostInbox.has("rematchAsked"));
  assert.ok(!w.hostInbox.has("newGame"));
  w.host.wantRematch();
  w.run(3);
  assert.equal(w.hostInbox.first("newGame").iAmFirst, false);
  assert.equal(w.guestInbox.first("newGame").iAmFirst, true);
  assert.equal(w.host.iAmFirst, false);
  assert.equal(w.guest.iAmFirst, true);

  w.host.wantRematch();
  w.run(3);
  assert.ok(w.guestInbox.has("rematchAsked"));
  w.guest.wantRematch();
  w.run(3);
  assert.equal(w.hostInbox.count("newGame"), 2);
  assert.equal(w.guestInbox.count("newGame"), 2);
  assert.equal(w.host.iAmFirst, true);
});

test("a rematch can be turned down", () => {
  const w = joined();
  w.guest.wantRematch();
  w.run(3);
  w.host.declineRematch();
  w.run(3);
  assert.ok(w.guestInbox.has("rematchDeclined"));
  assert.ok(!w.guestInbox.has("newGame") && !w.hostInbox.has("newGame"));
});

test("leaving tells the friend", () => {
  const w = joined();
  w.guest.leave();
  w.run(3, 50, [w.host]);
  assert.ok(w.hostInbox.has("friendLeft"));
  assert.equal(w.host.state, State.CLOSED);
  assert.equal(w.guest.state, State.CLOSED);
  assert.ok(w.hostSource.stopped && w.guestSource.stopped);
});

test("a goodbye after a quiet spell is not mistaken for coming back", () => {
  const w = joined();
  w.tick(Nearby.LOST_AFTER_MS + 1000, [w.host]);
  assert.equal(w.host.state, State.LOST);
  w.guest.leave();
  w.run(3, 50, [w.host]);
  assert.ok(w.hostInbox.has("friendLeft"));
  assert.ok(!w.hostInbox.has("resumed"));
});

// ---------- reconcile ----------

test("reconcile keeps, appends or replaces", () => {
  const ab = ["a", "b"], abc = ["a", "b", "c"];
  assert.deepEqual(reconcile(ab, ab, true), { type: "keep" });
  assert.deepEqual(reconcile(ab, abc, true), { type: "append", moves: ["c"] });
  assert.deepEqual(reconcile(abc, ab, false), { type: "keep" });
  assert.deepEqual(reconcile([], abc, false), { type: "append", moves: abc });
  assert.deepEqual(reconcile(["a", "x"], abc, true), { type: "keep" }, "the host's game wins");
  assert.deepEqual(reconcile(["a", "x"], abc, false), { type: "replace", moves: abc }, "the guest follows the host");
});

// ---------- the move glue ----------

/** A tiny pretend game: every move is accepted unless it is "bad". */
class FakeGame {
  constructor() { this.moves = []; this.log = []; this.isOver = false; }
  wires() { return [...this.moves]; }
  playRemote(w) { if (w === "bad") return false; this.moves.push(w); return true; }
  rebuild(ws) { if (ws.includes("bad")) return false; this.moves = [...ws]; this.log.push("rebuild"); return true; }
  afterRemoteMoves() { this.log.push("after"); }
  onConnection(live) { this.log.push(live ? "live" : "quiet"); }
  onRematchDeclined() { this.log.push("declined"); }
  onNewGame(first) { this.log.push(`new:${first}`); this.moves = []; }
  onFriendLeft() { this.log.push("left"); }
}

function matchSetup() {
  const w = joined();
  const hg = new FakeGame(), gg = new FakeGame();
  let changes = 0;
  const hm = new NearbyMatch(w.host, hg, () => changes++);
  const gm = new NearbyMatch(w.guest, gg, () => changes++);
  return { w, hg, gg, hm, gm, changes: () => changes };
}

test("the glue plays the friend's moves in order", () => {
  const { w, hg, gg, hm, gm } = matchSetup();
  hg.moves.push("e2e4"); hm.sendMove("e2e4");
  w.run(3);
  assert.deepEqual(gg.moves, ["e2e4"]);
  gg.moves.push("e7e5"); gm.sendMove("e7e5");
  w.run(3);
  assert.deepEqual(hg.moves, ["e2e4", "e7e5"]);
  assert.deepEqual(gg.log, ["after"]);
});

test("a move that skips ahead makes the glue ask for the whole list and catch up", () => {
  const { w, hg, gg } = matchSetup();
  hg.moves.push("e2e4", "e7e5", "g1f3");
  w.host.sendMove(3, "g1f3"); // the guest never saw moves 1 and 2
  w.run(6);
  assert.deepEqual(gg.moves, ["e2e4", "e7e5", "g1f3"]);
});

test("an illegal remote move asks for a fresh list instead of breaking", () => {
  const { w, hg, gg } = matchSetup();
  hg.moves.push("bad");
  w.host.sendMove(1, "bad");
  w.run(6);
  assert.deepEqual(gg.moves, [], "nothing illegal was played");
});

test("a guest whose game disagrees is rebuilt from the host's", () => {
  const { w, hg, gg, gm } = matchSetup();
  hg.moves.push("a2a3", "a7a6");
  gg.moves.push("h2h3");
  w.guest.requestSync();
  w.run(6);
  assert.deepEqual(gg.moves, ["a2a3", "a7a6"]);
  assert.ok(gg.log.includes("rebuild"));
  // The host keeps its own game when it is the one that disagrees.
  hg.moves.splice(0, 2, "b2b3");
  gg.moves = ["c2c3"];
  w.host.requestSync();
  w.run(6);
  assert.deepEqual(hg.moves, ["b2b3"]);
});

test("the glue tracks the connection, rematch questions and leaving", () => {
  const { w, hg, gg, hm, gm } = matchSetup();
  w.tick(Nearby.LOST_AFTER_MS + 1000, [w.host]);
  assert.equal(hm.linkLive, false);
  assert.deepEqual(hg.log.slice(-1), ["quiet"]);
  w.run(60);
  assert.equal(hm.linkLive, true);
  assert.deepEqual(hg.log.slice(-1), ["live"]);

  gm.session.wantRematch();
  w.run(3);
  assert.equal(hm.request, "Your friend wants to play again!");
  hm.answerRequest(true);
  w.run(3);
  assert.equal(hm.request, null);
  assert.equal(hg.log.at(-1), "new:false");
  assert.equal(gg.log.at(-1), "new:true");

  gm.leave();
  w.run(3, 50, [w.host]);
  assert.equal(hm.friendLeft, true);
  assert.equal(hg.log.at(-1), "left");
});

// ---------- invite codes ----------

// Real descriptions captured from Chrome: a data channel and no servers, so two mDNS host candidates each.
const REAL_OFFER = [
  "v=0", "o=- 5202960942357305530 2 IN IP4 127.0.0.1", "s=-", "t=0 0", "a=group:BUNDLE 0", "a=extmap-allow-mixed", "a=msid-semantic: WMS",
  "m=application 9 UDP/DTLS/SCTP webrtc-datachannel", "c=IN IP4 0.0.0.0",
  "a=candidate:4042789491 1 udp 2113937151 a3f83d50-6552-48ef-b8aa-9631f6f14843.local 59358 typ host generation 0 network-cost 999",
  "a=candidate:2909940398 1 udp 2113939711 aefa4f3c-6f97-4ff0-8527-75de5a217e22.local 59359 typ host generation 0 network-cost 999",
  "a=ice-ufrag:fu1J", "a=ice-pwd:BxZgGqY6Vf9BFP7IWAN38opb", "a=ice-options:trickle",
  "a=fingerprint:sha-256 B1:74:CD:9F:51:9F:13:9E:00:A8:78:50:14:D2:7B:F0:D3:96:6C:BC:98:62:B4:3D:BE:E8:E6:90:1F:2E:60:DE",
  "a=setup:actpass", "a=mid:0", "a=sctp-port:5000", "a=max-message-size:262144", "",
].join("\r\n");
const REAL_ANSWER = [
  "v=0", "o=- 1909921813312749420 2 IN IP4 127.0.0.1", "s=-", "t=0 0", "a=group:BUNDLE 0", "a=extmap-allow-mixed", "a=msid-semantic: WMS",
  "m=application 9 UDP/DTLS/SCTP webrtc-datachannel", "c=IN IP4 0.0.0.0",
  "a=candidate:2115272588 1 udp 2113937151 a3f83d50-6552-48ef-b8aa-9631f6f14843.local 57123 typ host generation 0 network-cost 999",
  "a=candidate:2250988509 1 udp 2113939711 aefa4f3c-6f97-4ff0-8527-75de5a217e22.local 57124 typ host generation 0 network-cost 999",
  "a=ice-ufrag:1Q2r", "a=ice-pwd:D4ajdSS5pthSCyxEneKEGEPb", "a=ice-options:trickle",
  "a=fingerprint:sha-256 64:35:2B:D1:6D:36:70:9D:E5:DB:30:18:A7:1A:F8:05:26:A6:CD:4D:6B:48:79:26:F8:E1:76:7C:21:4E:C1:0E",
  "a=setup:active", "a=mid:0", "a=sctp-port:5000", "a=max-message-size:262144", "",
].join("\r\n");

// A Firefox-style description: real addresses (IPv4 and IPv6), lookup-server addresses, upper-case "UDP", and lines we drop.
const FIREFOX_STYLE = [
  "v=0", "o=mozilla...THIS_IS_SDPARTA-99.0 7787 0 IN IP4 0.0.0.0", "s=-", "t=0 0", "a=sendrecv", "a=fingerprint:sha-256 " + "AB:".repeat(31) + "AB",
  "a=group:BUNDLE 0", "a=ice-options:trickle", "a=msid-semantic:WMS *",
  "m=application 9 UDP/DTLS/SCTP webrtc-datachannel", "c=IN IP4 0.0.0.0",
  "a=candidate:0 1 UDP 2122252543 192.168.1.20 49152 typ host",
  "a=candidate:1 1 UDP 2122187007 2001:db8:0:0:0:0:0:1 49153 typ host",
  "a=candidate:2 1 UDP 1686052607 203.0.113.5 49152 typ srflx raddr 192.168.1.20 rport 49152",
  "a=candidate:3 1 UDP 1686052606 203.0.113.6 49153 typ srflx raddr 0.0.0.0 rport 0 generation 0",
  "a=sendrecv", "a=end-of-candidates", "a=ice-pwd:0123456789abcdef0123456789abcdef", "a=ice-ufrag:abcd1234",
  "a=mid:0", "a=setup:passive", "a=sctp-port:5000", "a=max-message-size:1073741823", "",
].join("\r\n");

/** The facts a connection depends on, so two descriptions can be compared whatever order or wording they use. */
function facts(sdp) {
  const lines = sdp.split(/\r?\n/).filter(Boolean);
  const pick = (re) => lines.map((l) => re.exec(l)).filter(Boolean).map((m) => m[1])[0];
  const host = (a) => (a.includes(":") ? new URL(`http://[${a}]`).hostname : a);
  return {
    ufrag: pick(/^a=ice-ufrag:(.+)$/),
    pwd: pick(/^a=ice-pwd:(.+)$/),
    fingerprint: pick(/^a=fingerprint:sha-256 (.+)$/).toUpperCase(),
    setup: pick(/^a=setup:(.+)$/),
    candidates: lines.filter((l) => l.startsWith("a=candidate:")).map((l) => {
      const t = l.split(" ");
      const c = { proto: t[2].toLowerCase(), priority: Number(t[3]), address: host(t[4]), port: Number(t[5]), type: t[7] };
      const r = t.indexOf("raddr");
      if (r > 0) { c.raddr = host(t[r + 1]); c.rport = Number(t[t.indexOf("rport") + 1]); }
      return c;
    }),
  };
}

/** Descriptions the short form can't carry. */
const ODD_LINE = REAL_OFFER.replace("a=setup:actpass", "a=setup:actpass\r\na=ssrc:1 cname:odd");

test("a real description packs into a short code and comes back with every fact intact", async () => {
  for (const [role, sdp, setup] of [["offer", REAL_OFFER, "actpass"], ["answer", REAL_ANSWER, "active"]]) {
    const code = await encodeSignal({ role, wide: false, sdp });
    assert.match(code, role === "offer" ? /^CB2OL[A-Za-z0-9_-]+$/ : /^CB2AL[A-Za-z0-9_-]+$/, "short form, link-safe letters only");
    assert.ok(code.length < 170, `code is ${code.length} letters`);
    const back = await decodeSignal(code);
    assert.equal(back.role, role);
    assert.equal(back.wide, false);
    assert.deepEqual(facts(back.sdp), facts(sdp));
    assert.equal(facts(back.sdp).setup, setup);
  }
});

test("the rebuilt description is a complete, standard data-channel description", async () => {
  const { sdp } = await decodeSignal(await encodeSignal({ role: "offer", wide: true, sdp: REAL_OFFER }));
  for (const line of [
    "v=0", "a=group:BUNDLE 0", "m=application 9 UDP/DTLS/SCTP webrtc-datachannel", "c=IN IP4 0.0.0.0", "a=mid:0", "a=sctp-port:5000",
    "a=ice-ufrag:fu1J", "a=ice-pwd:BxZgGqY6Vf9BFP7IWAN38opb", "a=setup:actpass",
    "a=candidate:1 1 udp 2113937151 a3f83d50-6552-48ef-b8aa-9631f6f14843.local 59358 typ host generation 0",
  ]) assert.ok(sdp.split("\r\n").includes(line), `missing: ${line}`);
  assert.ok(sdp.endsWith("\r\n"));
});

test("real addresses, IPv6, lookup-server addresses and other browsers' wording are carried too", async () => {
  const code = await encodeSignal({ role: "answer", wide: true, sdp: FIREFOX_STYLE });
  assert.match(code, /^CB2AW/, "still the short form");
  const back = await decodeSignal(code);
  assert.equal(back.wide, true);
  assert.deepEqual(facts(back.sdp), facts(FIREFOX_STYLE));
  assert.equal(facts(back.sdp).candidates.length, 4);
});

test("anything the short form can't carry falls back to the long form, which still round-trips exactly", async () => {
  const odd = [
    ODD_LINE, // a line we don't know
    REAL_OFFER.replace("typ host generation 0", "typ host tcptype passive generation 0"), // an attribute we don't know
    REAL_OFFER.replace(" 1 udp ", " 2 udp "), // a second component
    REAL_OFFER.replace("fingerprint:sha-256", "fingerprint:sha-1"), // a different hash
  ];
  for (const sdp of odd) {
    const code = await encodeSignal({ role: "offer", wide: false, sdp });
    assert.match(code, /^CB1OL[ZR]/, "long form");
    assert.equal((await decodeSignal(code)).sdp, sdp);
  }
});

test("an invite link and a reply are short enough for a QR code a tablet camera can read", async () => {
  const link = inviteLink(await encodeSignal({ role: "offer", wide: false, sdp: REAL_OFFER }), "https://alexcch-byte.github.io/chess-buddies/play/");
  assert.ok(link.length < 230, `invite link is ${link.length} letters`);
  const reply = await encodeSignal({ role: "answer", wide: false, sdp: REAL_ANSWER });
  assert.ok(reply.length < 170, `reply is ${reply.length} letters`);
});

test("a code can be pasted with a message or a whole link around it", async () => {
  const code = await encodeSignal({ role: "offer", wide: false, sdp: REAL_OFFER });
  assert.equal(extractCode(`  Join me!\n${code}\nthanks `), code);
  const link = inviteLink(code, "https://example.github.io/chess-buddies/play/?x=1");
  assert.equal(link, `https://example.github.io/chess-buddies/play/#join=${code}`);
  assert.equal(extractCode(link), code);
  assert.equal(codeFromHash(`#join=${code}`), code);
  assert.equal(codeFromHash("#something-else"), null);
  assert.equal(codeFromHash(""), null);
  assert.deepEqual(facts((await decodeSignal(link)).sdp), facts(REAL_OFFER));
});

test("codes that are wrong say so in plain words", async () => {
  await assert.rejects(decodeSignal(""), SignalError);
  await assert.rejects(decodeSignal("hello there"), /doesn't look like a Chess Buddies code/);
  const code = await encodeSignal({ role: "offer", wide: false, sdp: REAL_OFFER });
  await assert.rejects(decodeSignal(code.slice(0, 40)), /cut short/, "a short code that stops early");
  await assert.rejects(decodeSignal(code.slice(0, -30)), /cut short/);
  await assert.rejects(decodeSignal("CB2OL!!!"), SignalError);
  await assert.rejects(decodeSignal("CB2OL" + Buffer.from([9, 1, 2, 3]).toString("base64url")), /typo/, "an unknown setup value");
  const long = await encodeSignal({ role: "offer", wide: false, sdp: ODD_LINE });
  await assert.rejects(decodeSignal(long.slice(0, 40)), SignalError, "a long code that stops early");
  await assert.rejects(decodeSignal("CB1OLR" + Buffer.from("not a description").toString("base64url")), /typo/);
});

// ---------- the real chess and checkers controllers, on both sides of a connection ----------

const stubCtx = { progress: { coachTips: true, recordWin() {} }, sound: { play() {} }, ai: null };

function chessPair() {
  const w = joined();
  const host = new ChessMatchController(null, w.host.iAmFirst, stubCtx, w.host);
  const guest = new ChessMatchController(null, w.guest.iAmFirst, stubCtx, w.guest);
  return { w, host, guest, all: [host, guest] };
}

/** Plays a move by its name on one controller, then lets the two browsers talk. */
function chessPlay(pair, c, name) {
  const m = c.position.legalMoves().find((x) => uci(x) === name);
  assert.ok(m, `${name} should be legal`);
  c.onBoardMove(m.from, m.to, false);
  pair.w.run(4);
}

test("chess: moves cross over and each side only moves on its own turn", () => {
  const p = chessPair();
  assert.equal(p.host.nearbySideFirst, true);
  assert.equal(p.guest.nearbySideFirst, false);
  assert.equal(p.host.isHumanTurn, true);
  assert.equal(p.guest.isHumanTurn, false);
  chessPlay(p, p.host, "e2e4");
  assert.deepEqual(p.guest.wires(), ["e2e4"]);
  assert.equal(p.guest.isHumanTurn, true);
  assert.equal(p.host.isHumanTurn, false);
  // Moving out of turn does nothing.
  const m = p.host.position.legalMoves()[0];
  p.host.onBoardMove(m.from, m.to, false);
  p.w.run(4);
  assert.deepEqual(p.guest.wires(), ["e2e4"]);
  chessPlay(p, p.guest, "e7e5");
  assert.deepEqual(p.host.wires(), ["e2e4", "e7e5"]);
  assert.equal(p.host.canUndo, false, "no take-backs in a game with a friend");
  p.all.forEach((c) => c.destroy());
});

test("chess: a captured piece is mentioned by the coach", () => {
  const p = chessPair();
  for (const [c, mv] of [[p.host, "e2e4"], [p.guest, "d7d5"], [p.host, "e4d5"]]) chessPlay(p, c, mv);
  assert.equal(p.guest.coachText, "Your friend captured your Pawn!");
  p.all.forEach((c) => c.destroy());
});

test("chess: checkmate ends the game for both, and each hears the right thing", () => {
  const p = chessPair();
  for (const [c, mv] of [[p.host, "f2f3"], [p.guest, "e7e5"], [p.host, "g2g4"], [p.guest, "d8h4"]]) chessPlay(p, c, mv);
  assert.equal(p.guest.result.headline, "You won!");
  assert.equal(p.guest.result.celebrate, true);
  assert.equal(p.host.result.headline, "Your friend won this time.");
  assert.equal(p.host.result.celebrate, false);
  assert.match(p.host.result.explanation, /play again/);
  assert.equal(p.host.isHumanTurn, false);
  p.all.forEach((c) => c.destroy());
});

test("chess: playing again asks the friend, then swaps colors on a fresh board", () => {
  const p = chessPair();
  chessPlay(p, p.host, "e2e4");
  p.host.newGame();
  p.w.run(4);
  assert.equal(p.host.coachText, "I asked your friend to play again. Waiting for a yes…");
  assert.deepEqual(p.host.wires(), ["e2e4"], "nothing resets until the friend agrees");
  assert.equal(p.guest.friendRequest, "Your friend wants to play again!");
  p.guest.answerFriendRequest(true);
  p.w.run(4);
  assert.deepEqual(p.host.wires(), []);
  assert.deepEqual(p.guest.wires(), []);
  assert.equal(p.host.playerWhite, false);
  assert.equal(p.guest.playerWhite, true);
  assert.equal(p.guest.isHumanTurn, true);
  assert.equal(p.host.isHumanTurn, false);
  assert.equal(p.guest.friendRequest, null);
  chessPlay(p, p.guest, "d2d4");
  assert.deepEqual(p.host.wires(), ["d2d4"]);
  p.all.forEach((c) => c.destroy());
});

test("chess: a quiet friend pauses the game, and a closed link says the invite is needed again", () => {
  const p = chessPair();
  p.w.tick(Nearby.LOST_AFTER_MS + 1000, [p.w.host]);
  assert.equal(p.host.connectionNote, "Waiting for your friend to come back…");
  assert.equal(p.host.isHumanTurn, false);
  p.w.run(60);
  assert.equal(p.host.connectionNote, null);
  assert.equal(p.host.isHumanTurn, true);

  p.w.guestLink.close();
  p.w.run(3);
  assert.match(p.host.connectionNote, /connection was lost/);
  assert.equal(p.host.isHumanTurn, false);
  p.all.forEach((c) => c.destroy());
});

test("chess: the friend leaving is noticed", () => {
  const p = chessPair();
  p.guest.leaveNearby();
  p.w.run(3, 50, [p.w.host]);
  assert.equal(p.host.friendLeft, true);
  assert.equal(p.host.isHumanTurn, false);
  assert.equal(p.host.coachText, "Your friend left the game.");
  p.all.forEach((c) => c.destroy());
});

test("checkers: twelve moves in a row stay in step on both sides, then colors swap for a rematch", () => {
  const w = joined({ kind: Nearby.CHECKERS });
  const host = new CheckersMatchController(null, w.host.iAmFirst, stubCtx, w.host);
  const guest = new CheckersMatchController(null, w.guest.iAmFirst, stubCtx, w.guest);
  assert.equal(host.nearbySideFirst, true);
  assert.equal(guest.nearbySideFirst, false);
  for (let ply = 0; ply < 12; ply++) {
    const mover = ply % 2 === 0 ? host : guest;
    assert.equal(mover.isHumanTurn, true, `ply ${ply}: it is this side's turn`);
    // A jump has to be taken, and legalMoves() already returns only the jumps when there are any.
    const m = mover.position.legalMoves()[0];
    mover.onBoardMove(m, false);
    w.run(4);
    assert.deepEqual(host.wires(), guest.wires(), `ply ${ply}: both sides have the same moves`);
    assert.equal(host.wires().length, ply + 1);
  }
  assert.ok(host.wires().every((s) => /^\d+(-\d+)+$/.test(s)), "moves are named by their squares joined with dashes");

  guest.newGame();
  w.run(4);
  assert.equal(host.friendRequest, "Your friend wants to play again!");
  host.answerFriendRequest(true);
  w.run(4);
  assert.deepEqual(host.wires(), []);
  assert.equal(host.playerRed, false);
  assert.equal(guest.playerRed, true);
  host.destroy();
  guest.destroy();
});
