// Glue between a NearbySession and a running game. Port of NearbyMatch.kt: it sends this player's moves and plays the
// friend's moves in order, catching up (or asking the friend to) when the two browsers have drifted apart, for example
// after the connection went quiet.
//
// The game object has to offer:
//   wires(): string[]               every move played so far, by its wire name
//   isOver: boolean
//   playRemote(wire, animate): boolean   plays the other player's move; false if it isn't legal here
//   rebuild(wires): boolean         throws the game away and replays wires from the start; false if one isn't legal
//   afterRemoteMoves()              called once after one or more of the friend's moves have been played
//   onConnection(live), onRematchDeclined(), onNewGame(iAmFirst), onFriendLeft()
import { reconcile } from "./session.js";

export class NearbyMatch {
  /** onChange is called whenever linkLive, request or friendLeft change, so the screen can redraw. */
  constructor(session, game, onChange = () => {}) {
    this.session = session;
    this.game = game;
    this.onChange = onChange;
    /** False while the other browser can't be reached. */
    this.linkLive = session.live;
    /** A question from the friend that needs a yes or a no, such as "play again?". */
    this.request = null;
    this.friendLeft = false;
    session.moveSource = () => game.wires();
    session.attach((e) => this.#onEvent(e));
  }

  sendMove(wire) { this.session.sendMove(this.game.wires().length, wire); }

  askRematch() { this.session.wantRematch(); }

  answerRequest(yes) {
    this.request = null;
    if (yes) this.session.wantRematch(); else this.session.declineRematch();
    this.onChange();
  }

  leave() {
    this.session.attach(null);
    this.session.leave();
  }

  #onEvent(e) {
    const { game, session } = this;
    switch (e.type) {
      case "move": {
        const have = game.wires().length;
        if (game.isOver || e.n <= have) break;
        if (e.n > have + 1) session.requestSync();
        else if (game.playRemote(e.wire, true)) game.afterRemoteMoves();
        else session.requestSync();
        break;
      }
      case "sync": {
        const r = reconcile(game.wires(), e.moves, session.isHost);
        if (r.type === "append") {
          const played = r.moves.every((w, i) => game.playRemote(w, r.moves.length === 1 && i === 0));
          if (played) game.afterRemoteMoves(); else session.requestSync();
        } else if (r.type === "replace") {
          if (game.rebuild(r.moves)) game.afterRemoteMoves();
        }
        break;
      }
      case "lost":
        this.linkLive = false;
        game.onConnection(false);
        break;
      case "resumed":
        this.linkLive = true;
        game.onConnection(true);
        break;
      case "rematchAsked":
        this.request = "Your friend wants to play again!";
        break;
      case "rematchDeclined":
        game.onRematchDeclined();
        break;
      case "newGame":
        this.request = null;
        game.onNewGame(e.iAmFirst);
        break;
      case "friendLeft":
        this.friendLeft = true;
        game.onFriendLeft();
        break;
    }
    this.onChange();
  }
}
