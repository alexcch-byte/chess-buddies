// A chess game against a bot, or between two players sharing the device when bot is null. Port of MatchController.kt.
import { Piece, Square, PAWN, isCapture, isEnPassant } from "../chess/position.js";
import { Game, EndReason, Notation } from "../chess/game.js";
import { hangingPieces, mateInOne, opponentMateThreat, explainIllegal } from "../chess/coach.js";
import { sleep, pick } from "../ui/dom.js";
import { Controller, Mood } from "./base.js";

const MIN_THINK_MS = 700;
const YOUR_TURN = ["Your turn!", "Your move! Take your time.", "What will you do next?", "Your turn. Look for captures and checks!"];
const OPENING_TIPS = [
  "Tip: Control the center! Pawns and pieces in the middle of the board are strong.",
  "Tip: Get your Knights and Bishops out early.",
  "Tip: Castle early to keep your King safe.",
  "Tip: Don't bring your Queen out too early. She can get chased around!",
];
const MIDDLE_TIPS = [
  "Tip: Before you move, ask yourself: what is my opponent attacking?",
  "Tip: Every move, look for checks, captures and threats.",
  "Tip: Rooks love open lines with no pawns in the way.",
  "Tip: A Knight on the edge of the board can't do much.",
  "Tip: Look for forks: one piece attacking two at once!",
];
const ENDGAME_TIPS = [
  "Tip: In the endgame, your King becomes a fighter. Bring it toward the center!",
  "Tip: Push your pawns! A new Queen could win the game.",
  "Tip: When you're winning, watch out for stalemate.",
];

export class ChessMatchController extends Controller {
  constructor(bot, playerWhite, { progress, sound, ai }) {
    super();
    this.bot = bot;
    this.playerWhite = playerWhite;
    this.progress = progress;
    this.sound = sound;
    this.ai = ai;
    this.game = new Game();
    this.animation = null;
    this.thinking = false;
    this.coachText = "";
    this.mood = Mood.NORMAL;
    this.hint = null;
    this.pendingPromotion = null;
    this.takeback = null;
    this.outcome = null;
    this.showResult = false;
    this.gameNumber = 0;
    this.flipped = !playerWhite;
    this.botToken = 0;
    this.hintToken = 0;
    this.animationId = 0;
    this.pendingDragged = false;
    this.botMovesSinceTip = 0;
    this.#greet();
    if (bot && !playerWhite) this.#scheduleBotMove();
  }

  get position() { return this.game.position; }
  get playerFirst() { return this.playerWhite; }
  get firstSideName() { return "White"; }
  get secondSideName() { return "Black"; }
  get firstSideToMove() { return this.position.whiteToMove; }
  get botName() { return this.bot ? this.bot.displayName.split(" ")[0] : ""; }

  get isHumanTurn() {
    return this.outcome === null && this.takeback === null && !this.thinking &&
      (this.bot === null || this.position.whiteToMove === this.playerWhite);
  }

  get canUndo() {
    return this.bot === null ? this.game.moves.length > 0 : this.game.moves.length >= (this.playerWhite ? 1 : 2);
  }

  get result() {
    const o = this.outcome;
    if (!o) return null;
    const won = o.whiteWins !== null && (this.bot === null || o.whiteWins === this.playerWhite);
    return {
      headline: this.#resultHeadline(o),
      explanation: this.#resultExplanation(o),
      emoji: o.whiteWins === null ? "🤝" : won ? "🏆" : "💪",
      celebrate: won,
    };
  }

  #greet() {
    this.mood = Mood.NORMAL;
    if (this.bot === null) this.coachText = "White moves first. Take turns, and have fun!";
    else if (this.playerWhite) this.coachText = `${this.bot.displayName} wants to play! You have the White pieces, so you go first.`;
    else this.coachText = `${this.bot.displayName} wants to play! You have the Black pieces, so ${this.botName} goes first.`;
  }

  newGame() {
    this.botToken++; this.hintToken++;
    this.game = new Game();
    this.animation = null;
    this.thinking = false;
    this.hint = null;
    this.takeback = null;
    this.outcome = null;
    this.showResult = false;
    this.pendingPromotion = null;
    this.botMovesSinceTip = 0;
    this.gameNumber++;
    this.#greet();
    if (this.bot && !this.playerWhite) this.#scheduleBotMove();
    this.emit();
  }

  onBoardMove(from, to, dragged) {
    if (!this.isHumanTurn) return;
    const candidates = this.position.legalMoves().filter((m) => m.from === from && m.to === to);
    if (candidates.length === 0) return;
    if (candidates.length > 1) {
      this.pendingDragged = dragged;
      this.pendingPromotion = candidates;
      this.emit();
    } else {
      this.#humanMove(candidates[0], dragged);
    }
  }

  choosePromotion(type) {
    const m = this.pendingPromotion?.find((c) => c.promotion === type);
    if (!m) return;
    this.pendingPromotion = null;
    this.#humanMove(m, this.pendingDragged);
  }

  cancelPromotion() { this.pendingPromotion = null; this.emit(); }
  onSelect() { this.sound.play("select"); }

  onIllegal(from, to) {
    if (!this.isHumanTurn) return;
    this.sound.play("illegal");
    this.mood = Mood.OOPS;
    this.coachText = explainIllegal(this.position, from, to);
    this.emit();
  }

  #humanMove(m, dragged) {
    this.hintToken++;
    const before = this.position;
    this.#apply(m, !dragged);
    if (this.game.isOver) { this.#finish(); return; }
    if (this.bot === null) { this.#friendCoach(); return; }
    if (this.progress.coachTips) {
      const warning = this.#blunderWarning(before, m, this.position);
      if (warning) {
        this.takeback = warning;
        this.mood = Mood.OOPS;
        this.coachText = warning;
        this.emit();
        return;
      }
    }
    this.#scheduleBotMove();
  }

  acceptTakeback() {
    this.takeback = null;
    this.game = this.game.undo(1);
    this.animation = null;
    this.mood = Mood.NORMAL;
    this.coachText = "Okay! Look around the board and find a safer move.";
    this.emit();
  }

  declineTakeback() {
    this.takeback = null;
    this.mood = Mood.NORMAL;
    this.#scheduleBotMove();
  }

  undo() {
    if (!this.canUndo) return;
    this.botToken++; this.hintToken++;
    this.thinking = false;
    this.takeback = null;
    this.outcome = null;
    this.showResult = false;
    this.hint = null;
    this.animation = null;
    let g = this.game.undo(1);
    if (this.bot) while (g.moves.length && g.position.whiteToMove !== this.playerWhite) g = g.undo(1);
    this.game = g;
    this.mood = Mood.NORMAL;
    this.coachText = "Move taken back. Try something new!";
    if (this.bot && this.position.whiteToMove !== this.playerWhite) this.#scheduleBotMove();
    this.emit();
  }

  async requestHint() {
    if (!this.isHumanTurn) return;
    const pos = this.position;
    const my = ++this.hintToken;
    this.mood = Mood.NORMAL;
    this.coachText = "Hmm, let me think… 🤔";
    this.emit();
    const result = await this.ai.chessBest(pos, 4, 1200);
    if (result && my === this.hintToken && this.position === pos && !this.dead) {
      this.hint = result.move;
      this.coachText = `💡 How about this: ${Notation.describe(pos, result.move)}?`;
      this.emit();
    }
  }

  #apply(m, animate) {
    const before = this.position;
    this.game = this.game.play(m);
    this.hint = null;
    this.animation = animate ? { move: m, before, id: ++this.animationId } : null;
    this.sound.play(this.position.inCheck() ? "check" : m.promotion ? "promote" : isCapture(m) ? "capture" : "move");
    this.emit();
  }

  async #scheduleBotMove() {
    const b = this.bot;
    if (!b) return;
    this.thinking = true;
    const start = this.game;
    const my = ++this.botToken;
    this.emit();
    const begun = performance.now();
    let m = null;
    try { m = await this.ai.chessChoose(start.position, b); } catch { m = null; }
    const wait = MIN_THINK_MS - (performance.now() - begun);
    if (wait > 0) await sleep(wait);
    if (this.dead || my !== this.botToken) return;
    if (this.game !== start || !m) { this.thinking = false; this.emit(); return; }
    const before = this.position;
    this.#apply(m, true);
    this.thinking = false;
    if (this.game.isOver) this.#finish(); else this.#botCoach(before, m);
  }

  /** What the coach says after the bot moves. */
  #botCoach(before, m) {
    const pos = this.position;
    const me = pos.whiteToMove;
    this.mood = Mood.NORMAL;
    const tips = this.progress.coachTips;
    let text;
    if (pos.inCheck()) {
      this.mood = Mood.OOPS;
      text = `Check! ${this.botName} is attacking your King. Get it to safety!`;
    } else if (isCapture(m)) {
      const taken = isEnPassant(m) ? PAWN : Math.abs(before.get(m.to));
      const canRecapture = pos.legalMoves().some((l) => l.to === m.to && isCapture(l));
      text = `${this.botName} captured your ${Piece.name(taken)}!` + (canRecapture && tips ? " Can you capture back?" : "");
    } else if (tips && mateInOne(pos)) {
      this.mood = Mood.HAPPY;
      text = "✨ Wow! You can checkmate in ONE move. Can you find it?";
    } else if (tips && opponentMateThreat(pos)) {
      this.mood = Mood.OOPS;
      text = `⚠️ Watch out! ${this.botName} is threatening checkmate. Can you stop it?`;
    } else if (tips && this.#freeCaptures(pos).length) {
      text = `👀 Look closely: one of ${this.botName}'s pieces isn't protected!`;
    } else if (tips && this.#valuableHanging(pos, me).length) {
      const sq = this.#valuableHanging(pos, me)[0];
      this.mood = Mood.OOPS;
      text = `Careful! Your ${Piece.name(Math.abs(pos.get(sq)))} on ${Square.name(sq)} is under attack.`;
    } else if (tips && ++this.botMovesSinceTip >= 2) {
      this.botMovesSinceTip = 0;
      text = this.#tipFor(pos);
    } else {
      text = pick(YOUR_TURN);
    }
    this.coachText = text;
    this.emit();
  }

  #friendCoach() {
    const side = this.position.whiteToMove ? "White" : "Black";
    this.mood = Mood.NORMAL;
    if (this.position.inCheck()) {
      this.mood = Mood.OOPS;
      this.coachText = `Check! ${side}'s King is under attack. ${side}, protect your King!`;
    } else {
      this.coachText = `${side}'s turn!`;
    }
    this.emit();
  }

  /** Enemy pieces worth at least a Knight that the side to move can simply take. */
  #freeCaptures(pos) {
    const legal = pos.legalMoves();
    return hangingPieces(pos, !pos.whiteToMove).filter(
      (sq) => Piece.points(Math.abs(pos.get(sq))) >= 3 && legal.some((l) => l.to === sq && isCapture(l)));
  }

  /** Own pieces worth at least a Knight that the opponent could win. */
  #valuableHanging(pos, white) {
    return hangingPieces(pos, white).filter((sq) => Piece.points(Math.abs(pos.get(sq))) >= 3);
  }

  /** A warning if the child's move just gave something big away, or null if the move looks fine. */
  #blunderWarning(before, m, after) {
    const me = before.whiteToMove;
    if (mateInOne(after)) return `Uh-oh! After that move, ${this.botName} can checkmate you right away. Want to take it back?`;
    // Compare in kid points so even trades (like Bishop for Knight) don't trigger a warning.
    const capturedPoints = isCapture(m) ? Piece.points(isEnPassant(m) ? PAWN : Math.abs(before.get(m.to))) : 0;
    const alreadyHanging = new Set(hangingPieces(before, me));
    const opponentMoves = after.legalMoves();
    const newlyHanging = hangingPieces(after, me).filter((sq) => {
      const points = Piece.points(Math.abs(after.get(sq)));
      return points >= 3 && points > capturedPoints && (sq === m.to || !alreadyHanging.has(sq)) &&
        opponentMoves.some((o) => o.to === sq && isCapture(o));
    });
    if (!newlyHanging.length) return null;
    const sq = newlyHanging.reduce((a, b) => (Piece.points(Math.abs(after.get(b))) > Piece.points(Math.abs(after.get(a))) ? b : a));
    return `Careful! ${this.botName} could capture your ${Piece.name(Math.abs(after.get(sq)))} on ${Square.name(sq)}. Want to take that move back?`;
  }

  #tipFor(pos) {
    const pieces = pos.pieceCount(true) + pos.pieceCount(false);
    const list = pos.fullmoveNumber <= 8 ? OPENING_TIPS : pieces <= 10 ? ENDGAME_TIPS : MIDDLE_TIPS;
    return pick(list);
  }

  async #finish() {
    const o = this.game.outcome;
    if (!o) return;
    this.outcome = o;
    this.thinking = false;
    this.hint = null;
    if (o.whiteWins === null) {
      this.mood = Mood.NORMAL;
      this.sound.play("success");
    } else if (this.bot === null || o.whiteWins === this.playerWhite) {
      this.mood = Mood.HAPPY;
      if (this.bot) this.progress.recordWin("chess", this.bot);
      this.sound.play("win");
    } else {
      this.mood = Mood.OOPS;
      this.sound.play("lose");
    }
    this.coachText = this.#resultHeadline(o) + " " + this.#resultExplanation(o);
    this.emit();
    const my = this.gameNumber;
    await sleep(1100);
    if (!this.dead && this.gameNumber === my && this.outcome) { this.showResult = true; this.emit(); }
  }

  #resultHeadline(o) {
    if (o.whiteWins === null) return "It's a draw!";
    if (this.bot === null) return o.whiteWins ? "White wins!" : "Black wins!";
    if (o.whiteWins === this.playerWhite) return "You won!";
    return `${this.botName} won this time.`;
  }

  #resultExplanation(o) {
    switch (o.reason) {
      case EndReason.CHECKMATE:
        if (this.bot === null) return "Checkmate! The King is trapped with no way out.";
        if (o.whiteWins === this.playerWhite) return `Checkmate! You trapped ${this.botName}'s King. Amazing!`;
        return "Checkmate. Every game makes you stronger. Want to try again?";
      case EndReason.STALEMATE: return "Stalemate: the player to move has no legal moves but isn't in check.";
      case EndReason.INSUFFICIENT_MATERIAL: return "Neither side has enough pieces left to checkmate.";
      case EndReason.FIFTY_MOVES: return "50 moves went by with no captures or pawn moves.";
      case EndReason.REPETITION: return "The same position happened three times.";
      default: return "";
    }
  }
}
