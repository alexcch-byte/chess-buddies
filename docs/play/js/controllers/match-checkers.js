// A checkers game against a bot, or between two players sharing the device when bot is null. Port of CheckersMatchController.kt.
import { Square } from "../chess/position.js";
import { Checker, CheckersGame, CheckersEndReason } from "../checkers/checkers.js";
import { explainIllegalCheckers } from "../checkers/checkers-coach.js";
import { sleep, pick } from "../ui/dom.js";
import { Controller, Mood } from "./base.js";

const MIN_THINK_MS = 650;
const YOUR_TURN = ["Your turn!", "Your move! Take your time.", "What will you do next?", "Your turn. Can any of your checkers be jumped?"];
const TIPS = [
  "Tip: Keep checkers on your back row as long as you can. Then the other side can't get kings!",
  "Tip: Checkers on the edge of the board can't be jumped.",
  "Tip: Try to get a king! Kings can move backward too.",
  "Tip: Move your checkers in groups so they protect each other.",
  "Tip: Before you move, check whether your checker could be jumped.",
  "Tip: Sometimes giving away one checker lets you jump two!",
];

const pathKey = (m) => m.path.join(",");

export class CheckersMatchController extends Controller {
  constructor(bot, playerRed, { progress, sound, ai }) {
    super();
    this.bot = bot;
    this.playerRed = playerRed;
    this.progress = progress;
    this.sound = sound;
    this.ai = ai;
    this.game = new CheckersGame();
    this.animation = null;
    this.thinking = false;
    this.coachText = "";
    this.mood = Mood.NORMAL;
    this.hint = null;
    this.takeback = null;
    this.outcome = null;
    this.showResult = false;
    this.gameNumber = 0;
    this.flipped = !playerRed;
    this.botToken = 0;
    this.hintToken = 0;
    this.animationId = 0;
    this.botMovesSinceTip = 0;
    this.#greet();
    if (bot && !playerRed) this.#scheduleBotMove(null);
  }

  get position() { return this.game.position; }
  get playerFirst() { return this.playerRed; }
  get firstSideName() { return "Red"; }
  get secondSideName() { return "Black"; }
  get firstSideToMove() { return this.position.redToMove; }
  get botName() { return this.bot ? this.bot.displayName.split(" ")[0] : ""; }

  get isHumanTurn() {
    return this.outcome === null && this.takeback === null && !this.thinking &&
      (this.bot === null || this.position.redToMove === this.playerRed);
  }

  get canUndo() {
    return this.bot === null ? this.game.moves.length > 0 : this.game.moves.length >= (this.playerRed ? 1 : 2);
  }

  get result() {
    const o = this.outcome;
    if (!o) return null;
    const won = o.redWins !== null && (this.bot === null || o.redWins === this.playerRed);
    return {
      headline: this.#resultHeadline(o),
      explanation: this.#resultExplanation(o),
      emoji: o.redWins === null ? "🤝" : won ? "🏆" : "💪",
      celebrate: won,
    };
  }

  #greet() {
    this.mood = Mood.NORMAL;
    if (this.bot === null) this.coachText = "Red moves first. Take turns, and remember: if you can jump, you must!";
    else if (this.playerRed) this.coachText = `${this.botName} wants to play checkers! You're Red, so you go first.`;
    else this.coachText = `${this.botName} wants to play checkers! You're Black, so ${this.botName} goes first.`;
  }

  newGame() {
    this.botToken++; this.hintToken++;
    this.game = new CheckersGame();
    this.animation = null;
    this.thinking = false;
    this.hint = null;
    this.takeback = null;
    this.outcome = null;
    this.showResult = false;
    this.botMovesSinceTip = 0;
    this.gameNumber++;
    this.#greet();
    if (this.bot && !this.playerRed) this.#scheduleBotMove(null);
    this.emit();
  }

  onSelect() { this.sound.play("select"); }

  onIllegal(from, to) {
    if (!this.isHumanTurn) return;
    this.sound.play("illegal");
    this.mood = Mood.OOPS;
    this.coachText = explainIllegalCheckers(this.position, from, to);
    this.emit();
  }

  onBoardMove(m, animate) {
    if (!this.isHumanTurn) return;
    this.hintToken++;
    const before = this.position;
    this.#apply(m, animate);
    if (this.game.isOver) this.#finish();
    else if (this.bot === null) this.#friendCoach();
    else this.#scheduleBotMove(this.progress.coachTips ? [before, m] : null);
  }

  acceptTakeback() {
    this.takeback = null;
    this.game = this.game.undo(1);
    this.animation = null;
    this.mood = Mood.NORMAL;
    this.coachText = "Okay! Look for a move where your checkers stay safe.";
    this.emit();
  }

  declineTakeback() {
    this.takeback = null;
    this.mood = Mood.NORMAL;
    this.#scheduleBotMove(null);
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
    if (this.bot) while (g.moves.length && g.position.redToMove !== this.playerRed) g = g.undo(1);
    this.game = g;
    this.mood = Mood.NORMAL;
    this.coachText = "Move taken back. Try something new!";
    if (this.bot && this.position.redToMove !== this.playerRed) this.#scheduleBotMove(null);
    this.emit();
  }

  async requestHint() {
    if (!this.isHumanTurn) return;
    const pos = this.position;
    const my = ++this.hintToken;
    this.mood = Mood.NORMAL;
    this.coachText = "Hmm, let me think… 🤔";
    this.emit();
    const m = await this.ai.checkersBest(pos, 10, 1200);
    if (m && my === this.hintToken && this.position === pos && !this.dead) {
      this.hint = m;
      this.coachText = m.isCapture
        ? `💡 Jump with the checker on ${Square.name(m.from)}!`
        : `💡 How about moving the checker on ${Square.name(m.from)} to ${Square.name(m.to)}?`;
      this.emit();
    }
  }

  #apply(m, animate) {
    const before = this.position;
    this.game = this.game.play(m);
    this.hint = null;
    this.animation = animate ? { move: m, before, id: ++this.animationId } : null;
    const crowned = !Checker.isKing(before.get(m.from)) && Checker.isKing(this.position.get(m.to));
    this.sound.play(crowned ? "promote" : m.isCapture ? "capture" : "move");
    this.emit();
  }

  /**
   * Lets the bot reply. If check holds [before, move] of the child's last move, the coach first makes sure that
   * move didn't throw checkers away, and offers a take-back if it did.
   */
  async #scheduleBotMove(check) {
    const b = this.bot;
    if (!b) return;
    this.thinking = true;
    const start = this.game;
    const my = ++this.botToken;
    this.emit();
    const begun = performance.now();
    if (check) {
      let warning = null;
      try { warning = await this.#blunderWarning(check[0], check[1]); } catch { warning = null; }
      if (this.dead || my !== this.botToken) return;
      if (warning && this.game === start) {
        this.thinking = false;
        this.takeback = warning;
        this.mood = Mood.OOPS;
        this.coachText = warning;
        this.emit();
        return;
      }
    }
    let m = null;
    try { m = await this.ai.checkersChoose(start.position, b); } catch { m = null; }
    const wait = MIN_THINK_MS - (performance.now() - begun);
    if (wait > 0) await sleep(wait);
    if (this.dead || my !== this.botToken) return;
    if (this.game !== start || !m) { this.thinking = false; this.emit(); return; }
    const before = this.position;
    this.#apply(m, true);
    this.thinking = false;
    if (this.game.isOver) this.#finish(); else this.#botCoach(before, m);
  }

  /** Compares the child's move with the best one; a big drop means checkers were given away. */
  async #blunderWarning(before, m) {
    const scores = await this.ai.checkersScores(before, 5, 700);
    const mine = scores.find((s) => s.path.join(",") === pathKey(m));
    if (!mine || scores.length === 0) return null;
    const best = Math.max(...scores.map((s) => s.score));
    if (best - mine.score < 90) return null;
    const jumps = before.makeMove(m).captureMoves();
    const biggestJump = jumps.reduce((n, j) => Math.max(n, j.captured.length), 0);
    if (biggestJump >= 2) return `Careful! After that move, ${this.botName} can double jump you. Want to take it back?`;
    if (biggestJump === 1) return `Careful! ${this.botName} could jump your checker. Want to take that move back?`;
    return `Hmm, that move could let ${this.botName} win a checker soon. Want to try a different move?`;
  }

  #botCoach(before, m) {
    const pos = this.position;
    const me = pos.redToMove;
    const tips = this.progress.coachTips;
    const myJumps = pos.captureMoves();
    const biggest = myJumps.reduce((n, j) => Math.max(n, j.captured.length), 0);
    const botCrowned = !Checker.isKing(before.get(m.from)) && Checker.isKing(pos.get(m.to));
    this.mood = Mood.NORMAL;
    let text;
    if (m.isCapture) {
      this.mood = Mood.OOPS;
      const n = m.captured.length;
      const took = n === 1 ? "jumped your checker" : `jumped ${n} of your checkers`;
      text = `${this.botName} ${took}!` + (myJumps.length ? " You have a jump now. You must take it!" : "");
    } else if (botCrowned) {
      text = `${this.botName} got a king! Watch out: kings can move backward.`;
    } else if (biggest >= 2) {
      this.mood = Mood.HAPPY;
      text = "✨ You have a double jump! Can you find it?";
    } else if (myJumps.length) {
      text = "You have a jump! In checkers, you must take it.";
    } else if (tips && pos.piecesInDanger(me).size) {
      this.mood = Mood.OOPS;
      text = `Careful! Your checker on ${Square.name([...pos.piecesInDanger(me)][0])} could be jumped.`;
    } else if (tips && ++this.botMovesSinceTip >= 2) {
      this.botMovesSinceTip = 0;
      text = pick(TIPS);
    } else {
      text = pick(YOUR_TURN);
    }
    this.coachText = text;
    this.emit();
  }

  #friendCoach() {
    const side = Checker.sideName(this.position.redToMove);
    this.mood = Mood.NORMAL;
    this.coachText = this.position.captureMoves().length ? `${side}'s turn. ${side} has a jump, and must take it!` : `${side}'s turn!`;
    this.emit();
  }

  async #finish() {
    const o = this.game.outcome;
    if (!o) return;
    this.outcome = o;
    this.thinking = false;
    this.hint = null;
    if (o.redWins === null) {
      this.mood = Mood.NORMAL;
      this.sound.play("success");
    } else if (this.bot === null || o.redWins === this.playerRed) {
      this.mood = Mood.HAPPY;
      if (this.bot) this.progress.recordWin("checkers", this.bot);
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
    if (o.redWins === null) return "It's a draw!";
    if (this.bot === null) return o.redWins ? "Red wins!" : "Black wins!";
    if (o.redWins === this.playerRed) return "You won!";
    return `${this.botName} won this time.`;
  }

  #resultExplanation(o) {
    const playerWon = o.redWins !== null && o.redWins === this.playerRed;
    switch (o.reason) {
      case CheckersEndReason.NO_PIECES:
        if (this.bot === null) return "Every one of the other side's checkers was captured.";
        return playerWon ? `You captured all of ${this.botName}'s checkers. Amazing!` : `${this.botName} captured all your checkers. Want a rematch?`;
      case CheckersEndReason.NO_MOVES:
        if (this.bot === null) return "The other side was trapped with no moves left.";
        return playerWon ? `${this.botName}'s checkers were trapped with no moves left!` : "Your checkers got trapped with no moves left. Want a rematch?";
      case CheckersEndReason.DRAW_QUIET: return "40 moves each went by with no jumps, so nobody wins.";
      case CheckersEndReason.DRAW_REPETITION: return "The same position happened three times.";
      default: return "";
    }
  }
}
