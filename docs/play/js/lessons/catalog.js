// The games the app teaches. Port of data/GameKind.kt.
import { CHESS_UNITS, CHESS_LESSONS } from "./chess-lessons.js";
import { CHECKERS_UNITS, CHECKERS_LESSONS } from "./checkers-lessons.js";

export const GAMES = {
  chess: { id: "chess", title: "Chess", units: CHESS_UNITS, lessons: CHESS_LESSONS },
  checkers: { id: "checkers", title: "Checkers", units: CHECKERS_UNITS, lessons: CHECKERS_LESSONS },
};

export const gameById = (id) => GAMES[id] ?? GAMES.chess;

export const lessonById = (id) => CHESS_LESSONS.find((l) => l.id === id) ?? CHECKERS_LESSONS.find((l) => l.id === id) ?? null;

export function nextLessonAfter(game, id) {
  const list = gameById(game).lessons;
  const i = list.findIndex((l) => l.id === id);
  return list[i + 1] ?? null;
}
