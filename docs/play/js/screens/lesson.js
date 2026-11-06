// The lesson screen, for chess and checkers: the board on one side, Sir Hops and the controls on the other.
// Port of LessonScreen.kt.
import { h } from "../ui/dom.js";
import { lessonIcon, sirHops } from "../ui/art.js";
import { kidButton, topBar, card, createCoach, victoryBanner, promotionDialog, confetti, fitBoard } from "../ui/components.js";
import { Board } from "../ui/board.js";
import { lessonById, nextLessonAfter } from "../lessons/catalog.js";
import { ChessLessonController } from "../controllers/lesson-chess.js";
import { CheckersLessonController } from "../controllers/lesson-checkers.js";
import { LessonPhase } from "../controllers/base.js";

function challengeDots(count, current, phase) {
  return h("div", { class: "dots" }, Array.from({ length: count }, (_, i) => {
    const done = i < current || (i === current && (phase === LessonPhase.SOLVED || phase === LessonPhase.FINISHED));
    return h("span", { class: `dot${done ? " done" : i === current ? " current" : ""}` });
  }));
}

export function lessonScreen(ctx, { lessonId }) {
  const { progress, sound, navigate, back, replace } = ctx;
  const lesson = lessonById(lessonId);
  const game = lesson.game;
  const c = game === "chess" ? new ChessLessonController(lesson, ctx) : new CheckersLessonController(lesson, ctx);

  const board = new Board(game, {
    onMove: game === "chess" ? (f, t, d) => c.onBoardMove(f, t, d) : (m, a) => c.onBoardMove(m, a),
    onSelect: () => c.onSelect(),
    onIllegal: (f, t) => c.onIllegal(f, t),
  });
  const coach = createCoach();
  const side = h("div", { class: "side" });
  const area = h("div", { class: "play" }, h("div", { class: "board-holder" }, board.el), side);
  const barSlot = h("div", { class: "bar-slot" });
  const root = h("div", { class: "screen" }, topBar(lesson.title, back, barSlot), area);
  const stopFit = fitBoard(area, { narrowHeightRatio: 0.6 });

  let promoClose = null;
  let confettiKey = null;

  const introControls = () => h("div", { class: "controls" },
    h("div", { class: "intro-dots" }, lesson.intro.map((_, i) => h("span", { class: `intro-dot${i === c.introPage ? " current" : ""}` }))),
    h("div", { class: "row" },
      kidButton({ text: c.isLastIntroPage ? "Let's go!" : "Next", emoji: c.isLastIntroPage ? "🚀" : "➡️", onClick: () => c.nextIntroPage() }),
      c.isLastIntroPage ? null : kidButton({ text: "Skip", color: "grey", small: true, onClick: () => c.skipIntro() })));

  const playControls = () => h("div", { class: "controls" },
    card(h("div", { class: "goal-card" },
      h("div", { class: "goal-main" },
        h("div", { class: "goal-small" }, `Challenge ${c.index + 1} of ${c.challengeCount}`),
        h("div", { class: "goal-label" }, c.goalLabel)),
      c.isMultiMove ? h("div", { class: "goal-moves" },
        h("div", { class: "goal-label" }, `Moves: ${c.moves}`),
        c.par !== null ? h("div", { class: "goal-small" }, `Best: ${c.par}`) : null) : null)),
    h("div", { class: "row" },
      kidButton({ text: "Hint", emoji: "💡", color: "orange", small: true, onClick: () => c.showHint() }),
      kidButton({ text: "Start over", emoji: "🔄", color: "blue", small: true, onClick: () => c.restartChallenge() })));

  const solvedCard = () => victoryBanner({
    stars: c.earned, seed: c.index,
    onNext: () => c.nextChallenge(),
    onRetry: c.earned < 3 ? () => c.restartChallenge() : null,
    nextLabel: c.index + 1 < c.challengeCount ? "Next ➔" : "Finish 🏆",
  });

  const finishedCard = () => {
    const stars = progress.lessonStars(lesson), max = progress.lessonMaxStars(lesson);
    const next = nextLessonAfter(game, lesson.id);
    return card(h("div", { class: "finished" },
      h("div", { class: "finished-avatar" }, sirHops({ happy: true })),
      h("h2", null, "🎉 Lesson Complete! 🎉"),
      h("div", { class: "finished-stars" }, `⭐ ${stars} of ${max} stars`),
      next
        ? h("div", { class: "up-next" }, lessonIcon(next, "icon"), h("span", null, `Up next: ${next.title}`))
        : h("p", null, "You finished every lesson! Try a Practice Match to use your new skills."),
      h("div", { class: "row" },
        kidButton({ text: "All lessons", emoji: "📚", color: "purple", small: true, onClick: () => back() }),
        next ? kidButton({ text: "Next lesson", emoji: "➡️", color: "green", small: true, onClick: () => replace({ name: "lesson", lessonId: next.id }) }) : null)),
    { color: "#FFFBEA" });
  };

  const render = () => {
    coach.set(c.coachText, c.mood);
    barSlot.replaceChildren(c.phase === LessonPhase.INTRO ? "" : challengeDots(c.challengeCount, c.index, c.phase));
    let controls;
    switch (c.phase) {
      case LessonPhase.INTRO: controls = introControls(); break;
      case LessonPhase.PLAYING: case LessonPhase.BUSY: controls = playControls(); break;
      case LessonPhase.SOLVED: controls = solvedCard(); break;
      default: controls = finishedCard();
    }
    side.replaceChildren(coach.el, controls);

    board.update({
      position: c.position,
      theme: progress.boardTheme,
      flipped: false,
      interactive: c.phase === LessonPhase.PLAYING,
      showLegalMoves: progress.showLegalMoves,
      showCoordinates: progress.showCoordinates,
      decor: {
        lastMove: c.lastMove, stars: c.starsLeft, hint: c.hint, wrongSquare: c.wrongSquare,
        previewSquare: c.phase === LessonPhase.INTRO ? c.introPreviewSquare : null, danger: null,
      },
      animation: c.animation,
    });

    // Pawn promotion choice (chess only).
    if (c.pendingPromotion && !promoClose) {
      promoClose = promotionDialog({
        white: c.position.whiteToMove,
        onPick: (type) => { promoClose = null; c.choosePromotion(type); },
        onDismiss: () => { promoClose = null; c.cancelPromotion(); },
      });
    } else if (!c.pendingPromotion && promoClose) {
      promoClose(); promoClose = null;
    }

    if (c.phase === LessonPhase.SOLVED || c.phase === LessonPhase.FINISHED) {
      const key = `${c.index}-${c.phase}`;
      if (key !== confettiKey) { confettiKey = key; confetti(); }
    }
  };

  const unsubC = c.subscribe(render);
  const unsubP = progress.subscribe(render);
  render();
  return {
    el: root,
    destroy() { unsubC(); unsubP(); c.destroy(); board.destroy(); stopFit(); promoClose?.(); },
  };
}
