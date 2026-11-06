// Runs one chess lesson: the intro pages, then each challenge in turn. Port of LessonController.kt.
import { Piece, isCapture } from "../chess/position.js";
import { explainIllegal } from "../chess/coach.js";
import { LessonRules, LessonSolver } from "../lessons/chess-lessons.js";
import { sleep, pick } from "../ui/dom.js";
import { Controller, Mood, LessonPhase, PRAISE, starsFor, later } from "./base.js";

export class ChessLessonController extends Controller {
  constructor(lesson, { progress, sound }) {
    super();
    this.lesson = lesson;
    this.progress = progress;
    this.sound = sound;
    this.phase = LessonPhase.INTRO;
    this.introPage = 0;
    this.index = 0;
    this.position = LessonRules.playablePosition(lesson.challenges[0]);
    this.starsLeft = new Set();
    this.moves = 0;
    this.par = null;
    this.coachText = lesson.intro[0];
    this.mood = Mood.NORMAL;
    this.animation = null;
    this.lastMove = null;
    this.hint = null;
    this.wrongSquare = null;
    this.earned = 0;
    this.pendingPromotion = null;
    this.mistakes = 0;
    this.usedHint = false;
    this.token = 0; // changes whenever a challenge (re)starts, so late results can be ignored
    this.animationId = 0;
    this.startingPosition = this.position;
    this.setupMove = null;
    this.pendingDragged = false;
  }

  get challenge() { return this.lesson.challenges[this.index]; }
  get challengeCount() { return this.lesson.challenges.length; }
  get isLastIntroPage() { return this.introPage === this.lesson.intro.length - 1; }
  get isMultiMove() { return this.challenge.goal.multiMove; }

  get goalLabel() {
    const goal = this.challenge.goal;
    switch (goal.kind) {
      case "stars": return `⭐ ${goal.stars.size - this.starsLeft.size} of ${goal.stars.size} stars`;
      case "captureAll": return "Capture them all!";
      case "promote": return "Promote your pawn";
      case "checkmate": return "Find checkmate";
      case "giveCheck": return "Give check";
      case "escapeCheck": return "Escape from check";
      case "captureOn": return "Find the best capture";
      case "castle": return "Castle!";
      case "enPassant": return "Capture en passant";
      case "fork": return "Find the fork";
      case "playMove": return "Find the best move";
      default: return "";
    }
  }

  /** A piece to demonstrate during the intro: the first piece of the side to move. */
  get introPreviewSquare() {
    for (let sq = 0; sq < 64; sq++) {
      const p = this.position.get(sq);
      if (p !== 0 && (p > 0) === this.position.whiteToMove) return sq;
    }
    return null;
  }

  nextIntroPage() {
    if (this.isLastIntroPage) { this.startChallenge(0); return; }
    this.introPage++;
    this.coachText = this.lesson.intro[this.introPage];
    this.emit();
  }

  skipIntro() { this.startChallenge(0); }
  restartChallenge() { this.startChallenge(this.index); }

  nextChallenge() {
    if (this.index + 1 < this.challengeCount) { this.startChallenge(this.index + 1); return; }
    this.phase = LessonPhase.FINISHED;
    this.mood = Mood.HAPPY;
    this.coachText = `You finished "${this.lesson.title}"! You're becoming a real chess player!`;
    this.sound.play("win");
    this.emit();
  }

  async startChallenge(i) {
    const my = ++this.token;
    this.index = i;
    this.mistakes = 0;
    this.usedHint = false;
    this.#resetBoardState();
    const c = this.challenge;
    this.coachText = c.instruction;
    this.mood = Mood.NORMAL;
    this.par = null;
    const initial = LessonRules.initialPosition(c);
    this.setupMove = LessonRules.setupMove(c);
    this.startingPosition = this.setupMove ? initial.makeMove(this.setupMove) : initial;
    this.position = initial;
    this.starsLeft = c.goal.kind === "stars" ? new Set(c.goal.stars) : new Set();
    this.phase = this.setupMove ? LessonPhase.BUSY : LessonPhase.PLAYING;
    this.emit();

    if (this.setupMove) {
      await sleep(800);
      if (this.token !== my || this.dead) return;
      this.#slide(initial, this.setupMove);
      this.sound.play(this.startingPosition.inCheck() ? "check" : "move");
      this.emit();
      await sleep(350);
      if (this.token !== my || this.dead) return;
      this.phase = LessonPhase.PLAYING;
      this.emit();
    }
    if (c.goal.multiMove) {
      const start = this.startingPosition, stars = this.starsLeft;
      const plan = await later(() => LessonSolver.solveMultiMove(start, c.goal, stars, c.safeOnly));
      if (this.token === my && !this.dead) { this.par = plan ? plan.length : null; this.emit(); }
    }
  }

  #resetBoardState() {
    this.moves = 0;
    this.hint = null;
    this.wrongSquare = null;
    this.lastMove = null;
    this.animation = null;
    this.pendingPromotion = null;
  }

  onBoardMove(from, to, dragged) {
    if (this.phase !== LessonPhase.PLAYING) return;
    const candidates = this.position.legalMoves().filter((m) => m.from === from && m.to === to);
    if (candidates.length === 0) return;
    if (candidates.length > 1) {
      this.pendingDragged = dragged;
      this.pendingPromotion = candidates;
      this.emit();
    } else {
      this.#play(candidates[0], dragged);
    }
  }

  choosePromotion(type) {
    const m = this.pendingPromotion?.find((c) => c.promotion === type);
    if (!m) return;
    this.pendingPromotion = null;
    this.#play(m, this.pendingDragged);
  }

  cancelPromotion() { this.pendingPromotion = null; this.emit(); }

  onIllegal(from, to) {
    if (this.phase !== LessonPhase.PLAYING) return;
    this.sound.play("illegal");
    this.mood = Mood.OOPS;
    this.coachText = explainIllegal(this.position, from, to);
    this.emit();
  }

  onSelect() { this.sound.play("select"); }

  async showHint() {
    if (this.phase !== LessonPhase.PLAYING) return;
    this.usedHint = true;
    const pos = this.position, c = this.challenge, stars = this.starsLeft;
    const m = await later(() => (c.goal.multiMove
      ? LessonSolver.solveMultiMove(pos, c.goal, stars, c.safeOnly)?.firstMove
      : LessonSolver.solveOneShot(pos, c.goal)));
    if (m && this.position === pos && !this.dead) {
      this.hint = m;
      this.mood = Mood.NORMAL;
      this.coachText = `Follow the green arrow! Move your ${Piece.name(Math.abs(pos.get(m.from)))}.`;
      this.emit();
    }
  }

  #play(m, dragged) {
    const before = this.position;
    const after = before.makeMove(m);
    this.hint = null;
    this.wrongSquare = null;
    this.lastMove = m;
    this.animation = dragged ? null : { move: m, before, id: ++this.animationId };
    this.moves++;
    this.sound.play(m.promotion ? "promote" : isCapture(m) ? "capture" : "move");
    if (this.challenge.goal.multiMove) this.#multiMoveStep(m, after);
    else this.#checkPuzzleAnswer(before, m, after);
  }

  #multiMoveStep(m, after) {
    const c = this.challenge;
    const my = this.token;
    if (c.safeOnly && LessonRules.landedInDanger(after, m)) {
      this.position = after;
      this.mistakes++;
      this.phase = LessonPhase.BUSY;
      this.mood = Mood.OOPS;
      const attackers = after.attackers(m.to, after.whiteToMove)
        .sort((a, b) => Piece.points(Math.abs(after.get(a))) - Piece.points(Math.abs(after.get(b))));
      const attackerName = attackers.length ? Piece.name(Math.abs(after.get(attackers[0]))) : "enemy";
      this.coachText = `Oh no! The ${attackerName} can capture your ${Piece.name(Math.abs(after.get(m.to)))} there. Let's try again!`;
      this.emit();
      (async () => {
        await sleep(700); if (this.token !== my || this.dead) return;
        this.#punish(after, m); this.emit();
        await sleep(1500); if (this.token !== my || this.dead) return;
        this.#retry();
      })();
      return;
    }
    const collected = this.starsLeft.has(m.to);
    const stars = new Set(this.starsLeft);
    stars.delete(m.to);
    if (collected) this.sound.play("star");
    this.starsLeft = stars;
    if (LessonRules.finished(c.goal, m, after, stars)) {
      this.position = after;
      this.#solved();
      return;
    }
    this.position = after.passTurn();
    this.mood = Mood.NORMAL;
    if (collected) {
      this.coachText = stars.size === 1 ? "Got it! Just 1 more star." : `Got it! ${stars.size} more stars to go.`;
    } else if (isCapture(m) && c.goal.kind === "captureAll") {
      const left = after.pieceCount(after.whiteToMove);
      this.coachText = left === 1 ? "Yum! Just 1 more to capture." : `Nice capture! ${left} more to go.`;
    } else {
      this.coachText = c.instruction;
    }
    this.emit();
  }

  #checkPuzzleAnswer(before, m, after) {
    this.position = after;
    const c = this.challenge;
    const my = this.token;
    if (LessonRules.solves(c.goal, before, m, after)) {
      if (after.inCheck()) this.sound.play("check");
      this.#solved();
      return;
    }
    this.mistakes++;
    this.phase = LessonPhase.BUSY;
    this.mood = Mood.OOPS;
    this.coachText = LessonRules.wrongAnswerText(c, m, after);
    this.wrongSquare = m.to;
    this.emit();
    (async () => {
      this.sound.play("oops");
      await sleep(800); if (this.token !== my || this.dead) return;
      const punished = this.#punish(after, m);
      this.emit();
      await sleep(punished ? 1100 : 500); if (this.token !== my || this.dead) return;
      this.#retry();
    })();
  }

  /** If the opponent can capture the piece that just moved, show it happening. */
  #punish(after, m) {
    const replies = after.legalMoves()
      .filter((r) => r.to === m.to && isCapture(r))
      .sort((a, b) => Piece.points(Math.abs(after.get(a.from))) - Piece.points(Math.abs(after.get(b.from))));
    if (!replies.length) return false;
    this.#slide(after, replies[0]);
    this.sound.play("capture");
    return true;
  }

  /** Back to the start of the current challenge, keeping the mistake count. */
  #retry() {
    this.#resetBoardState();
    this.position = this.startingPosition;
    this.lastMove = this.setupMove;
    this.starsLeft = this.challenge.goal.kind === "stars" ? new Set(this.challenge.goal.stars) : new Set();
    this.phase = LessonPhase.PLAYING;
    this.emit();
  }

  #slide(from, m) {
    this.position = from.makeMove(m);
    this.lastMove = m;
    this.animation = { move: m, before: from, id: ++this.animationId };
  }

  #solved() {
    this.phase = LessonPhase.SOLVED;
    this.earned = starsFor({ multiMove: this.challenge.goal.multiMove, moves: this.moves, par: this.par, mistakes: this.mistakes, usedHint: this.usedHint });
    this.progress.recordStars(this.lesson.id, this.index, this.earned);
    this.mood = Mood.HAPPY;
    this.coachText = pick(PRAISE);
    this.sound.play("success");
    this.emit();
  }
}
