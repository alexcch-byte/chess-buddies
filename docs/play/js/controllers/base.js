// Shared plumbing for lesson and match controllers.
export const Mood = Object.freeze({ NORMAL: "normal", HAPPY: "happy", OOPS: "oops" });
export const LessonPhase = Object.freeze({ INTRO: "intro", PLAYING: "playing", BUSY: "busy", SOLVED: "solved", FINISHED: "finished" });

/** Screens subscribe and re-render whenever the controller changes. */
export class Controller {
  constructor() { this.listeners = new Set(); this.dead = false; }
  subscribe(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); }
  emit() { if (!this.dead) for (const fn of [...this.listeners]) fn(); }
  /** Stops pending timers and async work from touching a screen that has closed. */
  destroy() { this.dead = true; this.listeners.clear(); }
}

export const PRAISE = ["Great job!", "You did it!", "Super smart move!", "Wow, you're a natural!", "Fantastic!", "That's the way!"];

/** 1-3 stars for a finished challenge. */
export function starsFor({ multiMove, moves, par, mistakes, usedHint }) {
  let stars = 3;
  if (multiMove) {
    if (par === null || moves <= par) stars = 3;
    else if (moves <= par + 2) stars = 2;
    else stars = 1;
  }
  stars -= mistakes;
  if (usedHint) stars = Math.min(stars, 2);
  return Math.max(1, Math.min(3, stars));
}

/** Runs heavy work on the next tick so the screen can paint first. */
export const later = (fn) => new Promise((resolve, reject) => setTimeout(() => { try { resolve(fn()); } catch (e) { reject(e); } }, 0));
