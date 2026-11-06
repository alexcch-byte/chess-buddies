// Sir Hops' voice: reads lesson instructions out loud with the browser's own voices (the Web Speech API), so there are
// no recordings to download and children who can't read yet can still follow along. Port of audio/Narrator.kt.

// Pictures, arrows and symbols that a voice would read as "light bulb" or "right arrow".
const NOT_SPOKEN = /[\p{So}\p{Sk}‍️⃣←-⇿]/gu;

/** Turns the words on screen into words that sound right when read out loud. */
export function cleanForSpeech(text) {
  return String(text).replace(NOT_SPOKEN, " ").replace(/\s+/g, " ").trim();
}

/** Picks a friendly English voice: US English first, and one that runs on this device rather than on a server. */
export function chooseVoice(voices) {
  const english = voices.filter((v) => /^en[-_]/i.test(v.lang));
  const us = english.filter((v) => /^en[-_]us$/i.test(v.lang));
  return us.find((v) => v.localService) ?? us[0] ?? english.find((v) => v.localService) ?? english[0] ?? null;
}

const REPEAT_GAP_MS = 25_000;
const VOICES_WAIT_MS = 1500;

export class Narrator {
  /** [win] and [now] are only swapped out by tests. */
  constructor(win = globalThis, now = () => Date.now()) {
    this.synth = win.speechSynthesis ?? null;
    this.Utterance = win.SpeechSynthesisUtterance ?? null;
    this.now = now;
    this.recent = new Map();
    this.owner = null;
    this.voices = [];
    this.voice = null;
    this.settled = false; // true once the browser has had time to report its voices
    this.listeners = new Set();
    if (this.#hasApi) {
      this.#refresh();
      this.synth.addEventListener?.("voiceschanged", () => { this.settled = true; this.#refresh(); });
      setTimeout(() => { this.settled = true; this.#notify(); }, VOICES_WAIT_MS);
    }
  }

  get #hasApi() { return !!(this.synth && this.Utterance); }

  /** False when this browser has no voice we can use, so the screen can hide its "Listen" button. */
  get available() { return this.#hasApi && (this.voices.length > 0 || !this.settled); }

  subscribe(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); }
  #notify() { for (const fn of this.listeners) fn(); }

  #refresh() {
    this.voices = this.synth.getVoices?.() ?? [];
    this.voice = chooseVoice(this.voices);
    this.#notify();
  }

  /** A lesson screen takes over the voice. Lines read a moment ago are fair game again. */
  attach(screen) {
    this.owner = screen;
    this.recent.clear();
  }

  /** Silences the voice, unless a newer screen has already taken over. */
  detach(screen) {
    if (this.owner !== screen) return;
    this.owner = null;
    this.stop();
  }

  /**
   * Reads text out loud, interrupting whatever is being said. A line that was just read isn't repeated (the coach says the
   * same instruction again after every ordinary move) unless the child asked for it with force.
   */
  say(text, { force = false } = {}) {
    const spoken = cleanForSpeech(text);
    if (!spoken || !this.available) return;
    const t = this.now();
    if (!force && t - (this.recent.get(spoken) ?? -Infinity) < REPEAT_GAP_MS) return;
    this.recent.set(spoken, t);
    const u = new this.Utterance(spoken);
    u.lang = this.voice?.lang ?? "en-US";
    if (this.voice) u.voice = this.voice;
    u.rate = 0.92;
    u.pitch = 1.08;
    this.synth.cancel();
    this.synth.speak(u);
  }

  stop() {
    try { this.synth?.cancel(); } catch { /* nothing is speaking */ }
  }
}
