// Runs one checkers lesson. The child always plays Red. Port of CheckersLessonController.kt.
import { Checker } from "../checkers/checkers.js";
import { explainIllegalCheckers } from "../checkers/checkers-coach.js";
import { CGoal, CheckersRules, CheckersSolver } from "../lessons/checkers-lessons.js";
import { sleep, pick } from "../ui/dom.js";
import { Controller, Mood, LessonPhase, starsFor, later } from "./base.js";

const PRAISE = ["Great job!", "You did it!", "Super smart move!", "Wow, you're a natural!", "Fantastic!", "Jump-tastic!"];

export class CheckersLessonController extends Controller {
  constructor(lesson, { progress, sound }) {
    super();
    this.lesson = lesson;
    this.progress = progress;
    this.sound = sound;
    this.phase = LessonPhase.INTRO;
    this.introPage = 0;
    this.index = 0;
    this.position = lesson.challenges[0].position;
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
    this.mistakes = 0;
    this.usedHint = false;
    this.token = 0;
    this.animationId = 0;
    /** For a two-step trap puzzle: 0 = offer a checker, 1 = take the multi-jump. */
    this.stage = 0;
    this.stageStart = this.position;
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
      case "crown": return "Get crowned";
      case "capture": return goal.atLeast === 1 ? "Make the jump" : `Jump ${goal.atLeast} checkers at once`;
      case "safeMove": return "Find a safe move";
      case "win": return "Win the game";
      case "shot": return this.stage === 0 ? "Set the trap" : "Spring the trap!";
      default: return "";
    }
  }

  /** The first red checker, demonstrated during the intro. */
  get introPreviewSquare() {
    for (let sq = 0; sq < 64; sq++) if (this.position.get(sq) > 0) return sq;
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
    this.coachText = `You finished "${this.lesson.title}"! You're becoming a checkers champion!`;
    this.sound.play("win");
    this.emit();
  }

  async startChallenge(i) {
    const my = ++this.token;
    this.index = i;
    this.mistakes = 0;
    this.usedHint = false;
    this.stage = 0;
    this.#resetBoard();
    const c = this.challenge;
    this.position = c.position;
    this.stageStart = c.position;
    this.starsLeft = c.goal.kind === "stars" ? new Set(c.goal.stars) : new Set();
    this.coachText = c.instruction;
    this.mood = Mood.NORMAL;
    this.par = null;
    this.phase = LessonPhase.PLAYING;
    this.emit();
    if (c.goal.multiMove) {
      const start = c.position, stars = this.starsLeft;
      const plan = await later(() => CheckersSolver.solveMultiMove(start, c.goal, stars));
      if (this.token === my && !this.dead) { this.par = plan ? plan.length : null; this.emit(); }
    }
  }

  #resetBoard() {
    this.moves = 0;
    this.hint = null;
    this.wrongSquare = null;
    this.lastMove = null;
    this.animation = null;
  }

  onSelect() { this.sound.play("select"); }

  onIllegal(from, to) {
    if (this.phase !== LessonPhase.PLAYING) return;
    this.sound.play("illegal");
    this.mood = Mood.OOPS;
    this.coachText = explainIllegalCheckers(this.position, from, to);
    this.emit();
  }

  /** The goal for the current step (the second step of a trap puzzle is a plain multi-jump). */
  #currentGoal() {
    const goal = this.challenge.goal;
    return goal.kind === "shot" && this.stage === 1 ? CGoal.capture(goal.atLeast) : goal;
  }

  async showHint() {
    if (this.phase !== LessonPhase.PLAYING) return;
    this.usedHint = true;
    const pos = this.position, goal = this.#currentGoal(), stars = this.starsLeft;
    const m = await later(() => (goal.multiMove
      ? CheckersSolver.solveMultiMove(pos, goal, stars)?.firstMove
      : CheckersSolver.solveOneShot(pos, goal)));
    if (m && this.position === pos && !this.dead) {
      this.hint = m;
      this.mood = Mood.NORMAL;
      this.coachText = "Follow the green arrow!";
      this.emit();
    }
  }

  onBoardMove(m, animate) {
    if (this.phase !== LessonPhase.PLAYING) return;
    const before = this.position;
    const after = before.makeMove(m);
    this.hint = null;
    this.wrongSquare = null;
    this.lastMove = m;
    this.animation = animate ? { move: m, before, id: ++this.animationId } : null;
    this.moves++;
    const crowned = !Checker.isKing(before.get(m.from)) && Checker.isKing(after.get(m.to));
    this.sound.play(crowned ? "promote" : m.isCapture ? "capture" : "move");
    const goal = this.#currentGoal();
    if (goal.multiMove) this.#multiMoveStep(goal, before, m, after);
    else this.#checkAnswer(goal, m, after);
  }

  #multiMoveStep(goal, before, m, after) {
    const collected = this.starsLeft.has(m.to);
    const stars = new Set(this.starsLeft);
    stars.delete(m.to);
    if (collected) this.sound.play("star");
    this.starsLeft = stars;
    if (CheckersRules.finished(goal, before, m, after, stars)) {
      this.position = after;
      this.#solved();
      return;
    }
    this.position = after.passTurn();
    this.mood = Mood.NORMAL;
    const crowned = !Checker.isKing(before.get(m.from)) && Checker.isKing(after.get(m.to));
    if (crowned) this.coachText = "Crowned! Now your king can move backward too.";
    else if (collected) this.coachText = stars.size === 1 ? "Got it! Just 1 more star." : `Got it! ${stars.size} more stars to go.`;
    else if (m.isCapture && goal.kind === "captureAll") {
      const left = after.pieceCount(false);
      this.coachText = left === 1 ? "Nice jump! Just 1 more to capture." : `Nice jump! ${left} more to go.`;
    } else this.coachText = this.challenge.instruction;
    this.emit();
  }

  #checkAnswer(goal, m, after) {
    this.position = after;
    const my = this.token;
    if (CheckersRules.solves(goal, m, after)) {
      if (this.challenge.goal.kind === "shot" && this.stage === 0) this.#springTrap(after, this.challenge.goal);
      else this.#solved();
      return;
    }
    this.mistakes++;
    this.phase = LessonPhase.BUSY;
    this.mood = Mood.OOPS;
    this.coachText = CheckersRules.wrongAnswerText(this.challenge, m, after);
    this.wrongSquare = m.to;
    this.emit();
    const retryFrom = this.stageStart;
    (async () => {
      this.sound.play("oops");
      await sleep(900); if (this.token !== my || this.dead) return;
      // If Black can now jump, show it happening.
      const reply = after.captureMoves().sort((a, b) => b.captured.length - a.captured.length)[0];
      if (reply) {
        this.#slide(after, reply);
        this.sound.play("capture");
        this.emit();
        await sleep(350 + 260 * reply.captured.length); if (this.token !== my || this.dead) return;
      }
      await sleep(500); if (this.token !== my || this.dead) return;
      this.#resetBoard();
      this.position = retryFrom;
      this.starsLeft = this.challenge.goal.kind === "stars" ? new Set(this.challenge.goal.stars) : new Set();
      this.phase = LessonPhase.PLAYING;
      this.emit();
    })();
  }

  /** First half of a trap puzzle done: Black is forced to jump, then the child jumps back. */
  #springTrap(after, shot) {
    const my = this.token;
    this.phase = LessonPhase.BUSY;
    this.mood = Mood.HAPPY;
    this.coachText = "Black has to jump. That's the rule!";
    this.emit();
    (async () => {
      await sleep(900); if (this.token !== my || this.dead) return;
      this.#slide(after, after.legalMoves()[0]);
      this.sound.play("capture");
      this.emit();
      await sleep(700); if (this.token !== my || this.dead) return;
      this.stage = 1;
      this.stageStart = this.position;
      this.moves = 0;
      this.mood = Mood.NORMAL;
      this.coachText = `The trap is ready! Now jump ${shot.atLeast} black checkers in one move.`;
      this.phase = LessonPhase.PLAYING;
      this.emit();
    })();
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
