// Settings plus lesson stars and match wins, saved in this browser. Port of data/ProgressStore.kt.
// Nothing is uploaded anywhere. If storage is blocked (private window), progress just lasts until the page closes.
import { BOTS } from "./chess/ai.js";
import { GAMES } from "./lessons/catalog.js";

const PREFIX = "chessbuddies:";
const STARS = "stars:";
const WINS = "wins:";

const DEFAULTS = {
  sound: true,
  read_aloud: true,
  legal_moves: true,
  coach_tips: true,
  danger_alerts: true,
  coordinates: true,
  board_theme: "meadow",
  selected_game: "chess",
};

function storage() {
  try { return window.localStorage; } catch { return null; }
}

export class ProgressStore {
  constructor() {
    this.listeners = new Set();
    this.mem = new Map(); // fallback when localStorage is unavailable
    this.ls = storage();
    this.stars = new Map();
    this.wins = new Map();
    for (const [k, v] of this.#entries()) {
      if (k.startsWith(STARS)) this.stars.set(k, Number(v));
      else if (k.startsWith(WINS)) this.wins.set(k, Number(v));
    }
  }

  #entries() {
    if (!this.ls) return [...this.mem.entries()];
    const out = [];
    try {
      for (let i = 0; i < this.ls.length; i++) {
        const k = this.ls.key(i);
        if (k && k.startsWith(PREFIX)) out.push([k.slice(PREFIX.length), this.ls.getItem(k)]);
      }
    } catch { /* ignore */ }
    return out;
  }

  #get(key) {
    try {
      const v = this.ls ? this.ls.getItem(PREFIX + key) : this.mem.get(key);
      return v ?? null;
    } catch { return this.mem.get(key) ?? null; }
  }

  #set(key, value) {
    this.mem.set(key, String(value));
    try { this.ls?.setItem(PREFIX + key, String(value)); } catch { /* storage full or blocked */ }
  }

  #remove(key) {
    this.mem.delete(key);
    try { this.ls?.removeItem(PREFIX + key); } catch { /* ignore */ }
  }

  /** Re-render when anything changes. Returns an unsubscribe function. */
  subscribe(fn) { this.listeners.add(fn); return () => this.listeners.delete(fn); }
  #changed() { for (const fn of this.listeners) fn(); }

  #bool(key) { const v = this.#get(key); return v === null ? DEFAULTS[key] : v === "true"; }
  #setBool(key, value) { this.#set(key, value); this.#changed(); }

  get soundOn() { return this.#bool("sound"); }
  set soundOn(v) { this.#setBool("sound", v); }
  /** Sir Hops reads lesson instructions out loud. */
  get readAloud() { return this.#bool("read_aloud"); }
  set readAloud(v) { this.#setBool("read_aloud", v); }
  get showLegalMoves() { return this.#bool("legal_moves"); }
  set showLegalMoves(v) { this.#setBool("legal_moves", v); }
  get coachTips() { return this.#bool("coach_tips"); }
  set coachTips(v) { this.#setBool("coach_tips", v); }
  get dangerAlerts() { return this.#bool("danger_alerts"); }
  set dangerAlerts(v) { this.#setBool("danger_alerts", v); }
  get showCoordinates() { return this.#bool("coordinates"); }
  set showCoordinates(v) { this.#setBool("coordinates", v); }

  get boardTheme() { return this.#get("board_theme") ?? DEFAULTS.board_theme; }
  set boardTheme(id) { this.#set("board_theme", id); this.#changed(); }

  /** The game picked on the home screen: "chess" or "checkers". */
  get selectedGame() { const g = this.#get("selected_game"); return g === "checkers" ? "checkers" : "chess"; }
  set selectedGame(id) { this.#set("selected_game", id); this.#changed(); }

  starsFor(lessonId, challenge) { return this.stars.get(`${STARS}${lessonId}:${challenge}`) ?? 0; }

  /** Keeps the best result for each challenge. */
  recordStars(lessonId, challenge, count) {
    const key = `${STARS}${lessonId}:${challenge}`;
    if (count <= (this.stars.get(key) ?? 0)) return;
    this.stars.set(key, count);
    this.#set(key, count);
    this.#changed();
  }

  lessonStars(lesson) {
    let n = 0;
    for (let i = 0; i < lesson.challengeCount; i++) n += this.starsFor(lesson.id, i);
    return n;
  }
  lessonMaxStars(lesson) { return lesson.challengeCount * 3; }
  isLessonFinished(lesson) {
    for (let i = 0; i < lesson.challengeCount; i++) if (this.starsFor(lesson.id, i) === 0) return false;
    return true;
  }

  totalStars(game) { return GAMES[game].lessons.reduce((n, l) => n + this.lessonStars(l), 0); }
  maxStars(game) { return GAMES[game].lessons.reduce((n, l) => n + this.lessonMaxStars(l), 0); }
  allStars() { return this.totalStars("chess") + this.totalStars("checkers"); }

  /** The first lesson of game the child hasn't finished yet. */
  nextLesson(game) { return GAMES[game].lessons.find((l) => !this.isLessonFinished(l)) ?? null; }

  // Chess wins keep the original key shape so it matches the Android app.
  #winsKey(game, bot) { return game === "chess" ? `${WINS}${bot.id}` : `${WINS}${game}:${bot.id}`; }
  winsAgainst(game, bot) { return this.wins.get(this.#winsKey(game, bot)) ?? 0; }
  totalWins(game) { return BOTS.reduce((n, b) => n + this.winsAgainst(game, b), 0); }
  allWins() { return this.totalWins("chess") + this.totalWins("checkers"); }

  recordWin(game, bot) {
    const key = this.#winsKey(game, bot);
    const count = (this.wins.get(key) ?? 0) + 1;
    this.wins.set(key, count);
    this.#set(key, count);
    this.#changed();
  }

  resetProgress() {
    for (const key of [...this.stars.keys(), ...this.wins.keys()]) this.#remove(key);
    this.stars.clear();
    this.wins.clear();
    this.#changed();
  }
}
