// The match screen, for chess and checkers: the board, both players, Sir Hops and the game buttons.
// Port of MatchScreen.kt.
import { h } from "../ui/dom.js";
import { pieceIcon, checkerIcon, animalAvatar, sirHops } from "../ui/art.js";
import { kidButton, topBar, card, createCoach, confirmDialog, openDialog, promotionDialog, confetti, fitBoard } from "../ui/components.js";
import { Board } from "../ui/board.js";
import { botById } from "../chess/ai.js";
import { Piece, QUEEN, ROOK, BISHOP, KNIGHT, PAWN, KING } from "../chess/position.js";
import { hangingPieces } from "../chess/coach.js";
import { Checker } from "../checkers/checkers.js";
import { ChessMatchController } from "../controllers/match-chess.js";
import { CheckersMatchController } from "../controllers/match-checkers.js";

const START_COUNTS = [[QUEEN, 1], [ROOK, 2], [BISHOP, 2], [KNIGHT, 2], [PAWN, 8]];

/** Pieces `first` side has captured, plus a "+N" lead. */
function capturedRow(c, game, first) {
  const pos = c.position;
  let icons, lead;
  if (game === "chess") {
    icons = START_COUNTS.flatMap(([type, count]) => {
      const piece = Piece.make(type, !first); // captured pieces belong to the other side
      return Array.from({ length: Math.max(0, count - pos.count(piece)) }, () => pieceIcon(piece, "icon cap"));
    });
    lead = pos.materialPoints(first) - pos.materialPoints(!first);
  } else {
    const taken = Math.max(0, 12 - pos.pieceCount(!first));
    icons = Array.from({ length: taken }, () => checkerIcon(first ? Checker.BLACK : Checker.RED, "icon cap small"));
    lead = pos.pieceCount(first) - pos.pieceCount(!first);
  }
  return h("div", { class: "captured" }, h("div", { class: "captured-icons" }, icons), lead > 0 ? h("span", { class: "lead" }, `+${lead}`) : null);
}

/** Name, avatar and captured pieces for one side. The top strip is whoever sits at the far side of the board. */
function playerStrip(c, game, top) {
  const bottomIsFirst = !c.flipped;
  const first = top ? !bottomIsFirst : bottomIsFirst;
  const bot = c.bot;
  const nearby = c.nearbySideFirst !== null;
  const mine = first === c.playerFirst;
  const isBot = bot !== null && !mine;
  const name = isBot ? bot.displayName : nearby ? (mine ? "You" : "Your friend") : bot !== null ? "You" : first ? c.firstSideName : c.secondSideName;
  const toMove = c.result === null && c.firstSideToMove === first;
  const icon = isBot
    ? animalAvatar(bot, { thinking: c.thinking, cls: "avatar strip-img" })
    : game === "chess" ? pieceIcon(Piece.make(KING, first), "icon strip-img") : checkerIcon(first ? Checker.RED : Checker.BLACK, "icon strip-img");
  let sub;
  if (isBot && c.thinking) sub = h("span", { class: "thinking" }, "Thinking", h("i", null, "."), h("i", null, "."), h("i", null, "."));
  else if (toMove && !isBot && (!nearby || mine)) sub = h("span", { class: "your-turn" }, "Your turn!");
  else sub = h("span", null, " ");
  return h("div", { class: `strip card${toMove ? " active" : ""}` },
    h("div", { class: "strip-avatar" }, icon),
    h("div", { class: "strip-text" }, h("div", { class: "strip-name" }, name), h("div", { class: "strip-sub" }, sub)),
    capturedRow(c, game, first));
}

export function matchScreen(ctx, { game, botId, playerFirst, nearby = null }) {
  const { progress, back } = ctx;
  const bot = botId ? botById(botId) : null;
  const c = game === "chess" ? new ChessMatchController(bot, playerFirst, ctx, nearby) : new CheckersMatchController(bot, playerFirst, ctx, nearby);
  const isNearby = nearby !== null;
  const title = isNearby ? (game === "chess" ? "Chess Nearby" : "Checkers Nearby")
    : bot === null ? (game === "chess" ? "Chess with a Friend" : "Checkers with a Friend")
      : (game === "chess" ? "Chess Practice" : "Checkers Practice");
  // In a game on two browsers, leaving ends the game for the friend too, so ask first.
  const leave = isNearby
    ? () => confirmDialog({ title: "Leave this game?", message: "Your friend will be told that you left.", confirmText: "Leave", dismissText: "Stay", onConfirm: back })
    : back;

  const board = new Board(game, {
    onMove: game === "chess" ? (f, t, d) => c.onBoardMove(f, t, d) : (m, a) => c.onBoardMove(m, a),
    onSelect: () => c.onSelect(),
    onIllegal: (f, t) => c.onIllegal(f, t),
  });
  const coach = createCoach({ compact: true });
  const area = h("div", { class: "play match" });
  const root = h("div", { class: "screen" }, topBar(title, leave), area);

  let wide = null;
  const stopFit = fitBoard(area, { narrowHeightRatio: 0.62, onChange: (w) => { wide = w; render(); } });

  let promoClose = null, resultClose = null, leftClose = null, confettiKey = null;

  /** "Waiting for your friend…" and "Your friend wants to play again!", shown above the game buttons. */
  const friendNotes = () => {
    const note = c.connectionNote, question = c.friendRequest;
    return [
      note ? h("div", { class: "note", role: "status" }, h("span", { "aria-hidden": "true" }, "📡"), h("span", null, note)) : null,
      question ? h("div", { class: "friend-ask" },
        h("div", { class: "friend-ask-text" }, question),
        h("div", { class: "row" },
          kidButton({ text: "Yes!", emoji: "👍", color: "green", small: true, onClick: () => c.answerFriendRequest(true) }),
          kidButton({ text: "Not now", color: "grey", small: true, onClick: () => c.answerFriendRequest(false) }))) : null,
    ];
  };

  const takebackButtons = () => (c.takeback === null ? null : h("div", { class: "row" },
    kidButton({ text: "Take it back", emoji: "↩️", color: "orange", small: true, onClick: () => c.acceptTakeback() }),
    kidButton({ text: "Keep my move", color: "grey", small: true, onClick: () => c.declineTakeback() })));

  const controls = () => h("div", { class: "row wrap" },
    bot ? kidButton({ text: "Hint", emoji: "💡", color: "orange", small: true, enabled: c.isHumanTurn, onClick: () => c.requestHint() }) : null,
    !isNearby ? kidButton({ text: "Undo", emoji: "↩️", color: "blue", small: true, enabled: c.canUndo, onClick: () => c.undo() }) : null,
    bot === null && !isNearby ? kidButton({ text: "Flip board", emoji: "🔃", color: "green", small: true, onClick: () => { c.flipped = !c.flipped; c.emit(); } }) : null,
    kidButton({
      text: isNearby ? "Play again" : "New game", emoji: "✨", color: "purple", small: true,
      onClick: () => confirmDialog(isNearby
        ? { title: "Play again?", message: "I'll ask your friend. If they say yes, you swap colors and start a fresh board.", confirmText: "Ask my friend", onConfirm: () => c.newGame() }
        : { title: "Start a new game?", message: "This game will end and a fresh board will be set up.", confirmText: "New game", onConfirm: () => c.newGame() }),
    }));

  const showFriendLeftDialog = () => {
    leftClose = openDialog(h("div", { class: "dialog-body" },
      h("div", { class: "result-emoji" }, "👋"),
      h("h2", null, "Your friend left"),
      h("p", null, "The game is over. You can look at the board, or go home."),
      h("div", { class: "row" },
        kidButton({ text: "See the board", color: "blue", small: true, onClick: () => { leftClose?.(); } }),
        kidButton({ text: "Home", color: "purple", small: true, onClick: () => { leftClose?.(); back(); } }))));
  };

  const showResultDialog = () => {
    const r = c.result;
    if (!r) return;
    const art = r.celebrate ? sirHops({ happy: true, cls: "avatar result-art" }) : bot ? animalAvatar(bot, { cls: "avatar result-art" }) : h("div", { class: "result-emoji" }, r.emoji);
    let close;
    close = openDialog(h("div", { class: "dialog-body" },
      art, h("h2", null, r.headline), h("p", null, r.explanation),
      h("div", { class: "stack" },
        kidButton({ text: "Play again", emoji: "🔄", color: "green", onClick: () => { close(); resultClose = null; c.newGame(); } }),
        h("div", { class: "row" },
          kidButton({ text: "See the board", color: "blue", small: true, onClick: () => { close(); resultClose = null; c.showResult = false; c.emit(); } }),
          kidButton({ text: bot ? "New opponent" : "Home", color: "purple", small: true, onClick: () => { close(); resultClose = null; back(); } })))),
    { onDismiss: () => { resultClose = null; c.showResult = false; c.emit(); } });
    resultClose = close;
  };

  const render = () => {
    coach.set(c.coachText, c.mood);
    const pos = c.position;
    let danger = new Set();
    if (progress.dangerAlerts && c.isHumanTurn && c.bot) {
      danger = game === "chess" ? new Set(hangingPieces(pos, pos.whiteToMove)) : pos.piecesInDanger(pos.redToMove);
    }
    board.update({
      position: pos, theme: progress.boardTheme, flipped: c.flipped, interactive: c.isHumanTurn,
      showLegalMoves: progress.showLegalMoves, showCoordinates: progress.showCoordinates,
      decor: { lastMove: c.game.lastMove, hint: c.hint, danger, stars: null, wrongSquare: null, previewSquare: null },
      animation: c.animation,
    });

    if (wide !== null) {
      const stripTop = playerStrip(c, game, true), stripBottom = playerStrip(c, game, false);
      const boardHolder = h("div", { class: "board-holder" }, board.el);
      if (wide) {
        area.replaceChildren(boardHolder, h("div", { class: "side" }, stripTop, coach.el, friendNotes(), takebackButtons(), controls(), stripBottom));
      } else {
        area.replaceChildren(h("div", { class: "match-col" }, stripTop, boardHolder, stripBottom, coach.el, friendNotes(), takebackButtons(), controls()));
      }
    }

    if (c.pendingPromotion && !promoClose) {
      promoClose = promotionDialog({
        white: c.position.whiteToMove,
        onPick: (type) => { promoClose = null; c.choosePromotion(type); },
        onDismiss: () => { promoClose = null; c.cancelPromotion(); },
      });
    } else if (!c.pendingPromotion && promoClose) { promoClose(); promoClose = null; }

    if (c.showResult && !resultClose) showResultDialog();
    else if (!c.showResult && resultClose) { resultClose(); resultClose = null; }

    if (c.friendLeft && !leftClose) showFriendLeftDialog();

    if (c.result?.celebrate) {
      const key = String(c.gameNumber);
      if (key !== confettiKey) { confettiKey = key; confetti(); }
    }
  };

  const unsubC = c.subscribe(render);
  const unsubP = progress.subscribe(render);
  return {
    el: root,
    destroy() {
      unsubC(); unsubP();
      c.leaveNearby?.(); // leaving the screen ends a game against a friend
      c.destroy(); board.destroy(); stopFit(); promoClose?.(); resultClose?.(); leftClose?.();
    },
  };
}
