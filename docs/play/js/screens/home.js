// Home, lesson map, practice setup and settings screens. Ports of HomeScreen.kt, LessonMapScreen.kt,
// PracticeSetupScreen.kt and SettingsScreen.kt.
import { h } from "../ui/dom.js";
import { pieceIcon, checkerIcon, animalAvatar, sirHops, lessonIcon } from "../ui/art.js";
import { kidButton, roundIconButton, topBar, card, countPill, sectionLabel, badge, confirmDialog } from "../ui/components.js";
import { THEMES } from "../ui/board.js";
import { BOTS, botById } from "../chess/ai.js";
import { KNIGHT, KING } from "../chess/position.js";
import { Checker } from "../checkers/checkers.js";
import { GAMES } from "../lessons/catalog.js";

// ---------- Home ----------

function menuCard({ emoji, title, subtitle, color, badgeText, onClick, avatar }) {
  return h("button", { class: `menu-card ${color}`, type: "button", onclick: onClick },
    h("div", { class: "menu-icon" }, avatar ?? h("span", null, emoji)),
    h("div", { class: "menu-text" }, h("div", { class: "menu-title" }, title), h("div", { class: "menu-sub" }, subtitle)),
    badgeText ? h("div", { class: "menu-badge" }, badgeText) : null);
}

function gameSwitcher(game, onPick) {
  return h("div", { class: "switcher", role: "tablist" },
    ["chess", "checkers"].map((g) => {
      const selected = g === game;
      return h("button", {
        class: `switch-tab${selected ? " selected" : ""}`, type: "button", role: "tab", "aria-selected": String(selected),
        onclick: () => onPick(g),
      }, g === "chess" ? pieceIcon(selected ? KNIGHT : -KNIGHT, "icon tab-icon") : checkerIcon(Checker.RED_KING, "icon tab-icon"),
      h("span", null, GAMES[g].title));
    }));
}

function hero(game) {
  const art = game === "chess"
    ? [animalAvatar(BOTS[0], { cls: "avatar hero-side left" }), sirHops({ happy: true, cls: "avatar hero-main" }), animalAvatar(BOTS[1], { cls: "avatar hero-side right" })]
    : [animalAvatar(BOTS[2], { cls: "avatar hero-side left" }), checkerIcon(Checker.RED_KING, "icon hero-main checker"), animalAvatar(BOTS[0], { cls: "avatar hero-side right" })];
  return h("div", { class: "hero" },
    h("div", { class: "hero-art" }, h("div", { class: "hero-glow" }), h("div", { class: "hero-row" }, art)),
    h("h2", { class: "hero-title" }, "Chess Buddies"),
    h("p", { class: "hero-sub" }, "Hi, I'm Sir Hops! Let's learn chess and checkers together."));
}

export function homeScreen(ctx) {
  const { progress, navigate } = ctx;
  const root = h("div", { class: "screen home" });
  const render = () => {
    const game = progress.selectedGame;
    const next = progress.nextLesson(game);
    const title = GAMES[game].title;
    const menu = h("div", { class: "menu" },
      gameSwitcher(game, (g) => { progress.selectedGame = g; }),
      menuCard({
        emoji: "📚", title: `Learn ${title}`, color: "purple",
        subtitle: next ? `Next up: ${next.title}` : "All lessons done. Amazing!",
        badgeText: `⭐ ${progress.totalStars(game)} / ${progress.maxStars(game)}`,
        avatar: sirHops({ happy: true, cls: "avatar" }),
        onClick: () => navigate({ name: "lessons", game }),
      }),
      menuCard({
        emoji: "🦊", title: "Practice Match", color: "green",
        subtitle: `Play ${title.toLowerCase()} with the forest friends`,
        badgeText: `🏆 ${progress.totalWins(game)}`,
        avatar: animalAvatar(BOTS[2]),
        onClick: () => navigate({ name: "practice", game }),
      }),
      menuCard({
        emoji: "🧑‍🤝‍🧑", title: "Play a Friend", color: "orange", subtitle: "Two players, one screen", badgeText: null,
        onClick: () => navigate({ name: "match", game, botId: null, playerFirst: true }),
      }),
      // The download page sits one level up from the web version.
      h("a", { class: "get-app", href: "../" }, h("span", { "aria-hidden": "true" }, "📲"), h("span", null, "Get the Android app")));
    root.replaceChildren(
      h("div", { class: "home-body" }, hero(game), menu),
      h("div", { class: "home-settings" }, roundIconButton("⚙️", () => navigate({ name: "settings" }), "Settings")));
  };
  render();
  const unsub = progress.subscribe(render);
  return { el: root, destroy: unsub };
}

// ---------- Lesson map ----------

const UNIT_COLORS = ["#6C5CE7", "#FF9F43", "#E55353", "#0984E3", "#00B894"];

function lessonCard({ number, lesson, color, stars, maxStars, isNext, finished, onClick }) {
  const pct = maxStars === 0 ? 0 : Math.round((stars / maxStars) * 100);
  const ribbon = isNext ? badge("Next!", "orange") : finished ? badge(stars === maxStars ? "Perfect!" : "Done!", "green") : null;
  return h("div", { class: "lesson-card-wrap" },
    h("button", { class: `lesson-card${isNext ? " next" : ""}`, type: "button", onclick: onClick },
      h("div", { class: "lesson-icon", style: { background: color + "2E" } }, lessonIcon(lesson, "icon")),
      h("div", { class: "lesson-text" },
        h("div", { class: "lesson-num", style: { color } }, `Lesson ${number}`),
        h("div", { class: "lesson-title" }, lesson.title),
        h("div", { class: "lesson-tag" }, lesson.tagline),
        h("div", { class: "lesson-progress" },
          h("div", { class: "bar" }, h("div", { class: "bar-fill", style: { width: `${pct}%` } })),
          h("span", null, `⭐ ${stars}/${maxStars}`)))),
    ribbon);
}

export function lessonMapScreen(ctx, { game }) {
  const { progress, navigate, back } = ctx;
  const root = h("div", { class: "screen" });
  const render = () => {
    const g = GAMES[game];
    const next = progress.nextLesson(game);
    let number = 0;
    const sections = g.units.map((unit, ui) => {
      const color = UNIT_COLORS[ui % UNIT_COLORS.length];
      return [
        h("div", { class: "unit-head" }, h("span", { class: "unit-emoji" }, unit.emoji), h("span", null, `Unit ${ui + 1}: ${unit.title}`)),
        h("div", { class: "grid lessons" }, unit.lessons.map((lesson) => lessonCard({
          number: ++number, lesson, color,
          stars: progress.lessonStars(lesson), maxStars: progress.lessonMaxStars(lesson),
          isNext: next === lesson, finished: progress.isLessonFinished(lesson),
          onClick: () => navigate({ name: "lesson", lessonId: lesson.id }),
        }))),
      ];
    });
    root.replaceChildren(
      topBar(`${g.title} Lessons`, back, countPill("⭐", `${progress.totalStars(game)} / ${progress.maxStars(game)}`)),
      h("div", { class: "scroll pad" }, sections));
  };
  render();
  return { el: root, destroy() {} };
}

// ---------- Practice setup ----------

const BOT_BG = { bunny: "#FFECEF", kitten: "#FFF0E0", fox: "#FFF2DC", owl: "#EBF3FF", dragon: "#E2F9F5" };
const levelLabel = (level) => "★".repeat(level) + "☆".repeat(5 - level);

export function practiceSetupScreen(ctx, { game }) {
  const { progress, navigate, back } = ctx;
  const root = h("div", { class: "screen" });
  let botId = "bunny";
  let color = "first";
  const render = () => {
    const bot = botById(botId);
    const botCards = BOTS.map((b) => {
      const wins = progress.winsAgainst(game, b);
      return h("button", {
        class: `bot-card${b.id === botId ? " selected" : ""}`, type: "button", role: "radio", "aria-checked": String(b.id === botId),
        onclick: () => { botId = b.id; render(); },
      },
      h("div", { class: "bot-circle", style: { background: BOT_BG[b.id] } }, animalAvatar(b)),
      h("div", { class: "bot-name" }, b.displayName),
      h("div", { class: "bot-level" }, levelLabel(b.level)),
      h("div", { class: "bot-blurb" }, b.blurb),
      wins > 0 ? h("div", { class: "bot-wins" }, `🏆 You won ${wins} ${wins === 1 ? "time" : "times"}!`) : null);
    });
    const chips = ["first", "second", "random"].map((choice) => {
      const label = choice === "first" ? (game === "chess" ? "White" : "Red") : choice === "second" ? "Black" : "Surprise me";
      const icon = choice === "random"
        ? h("span", { class: "chip-emoji" }, "🎲")
        : game === "chess"
          ? pieceIcon(choice === "first" ? KING : -KING, "icon chip-icon")
          : checkerIcon(choice === "first" ? Checker.RED : Checker.BLACK, "icon chip-icon");
      return h("button", {
        class: `chip${choice === color ? " selected" : ""}`, type: "button", role: "radio", "aria-checked": String(choice === color),
        onclick: () => { color = choice; render(); },
      }, icon, h("span", null, label));
    });
    root.replaceChildren(
      topBar(`${GAMES[game].title} Practice`, back),
      h("div", { class: "scroll pad" },
        sectionLabel("Choose your opponent"),
        h("div", { class: "grid bots" }, botCards),
        sectionLabel(game === "chess" ? "Choose your pieces" : "Choose your checkers"),
        h("div", { class: "chips" }, chips),
        kidButton({
          text: `Play ${bot.displayName.split(" ")[0]}!`, emoji: game === "chess" ? "♟️" : "🔴", color: "green", wide: true,
          onClick: () => navigate({ name: "match", game, botId: bot.id, playerFirst: color === "first" ? true : color === "second" ? false : Math.random() < 0.5 }),
        })));
  };
  render();
  return { el: root, destroy() {} };
}

// ---------- Settings ----------

function toggleRow(emoji, title, subtitle, checked, onChange) {
  return h("button", { class: "toggle-row", type: "button", role: "switch", "aria-checked": String(checked), onclick: () => onChange(!checked) },
    h("span", { class: "toggle-emoji" }, emoji),
    h("span", { class: "toggle-text" }, h("span", { class: "toggle-title" }, title), h("span", { class: "toggle-sub" }, subtitle)),
    h("span", { class: `switch${checked ? " on" : ""}` }, h("span", { class: "knob" })));
}

function themeChoice(theme, selected, onClick) {
  const squares = [];
  for (let r = 0; r < 4; r++) for (let f = 0; f < 4; f++) squares.push(h("span", { style: { background: (r + f) % 2 === 0 ? theme.light : theme.dark } }));
  return h("button", { class: `theme-choice${selected ? " selected" : ""}`, type: "button", role: "radio", "aria-checked": String(selected), onclick: onClick },
    h("div", { class: "theme-preview" }, squares), h("span", null, theme.label));
}

export function settingsScreen(ctx) {
  const { progress, back } = ctx;
  const root = h("div", { class: "screen" });
  const render = () => {
    root.replaceChildren(
      topBar("Settings", back),
      h("div", { class: "scroll pad narrow-col" },
        sectionLabel("Helpers"),
        card(h("div", { class: "toggles" },
          toggleRow("🔊", "Sounds", "Clicks, dings and happy tunes", progress.soundOn, (v) => { progress.soundOn = v; }),
          toggleRow("🟢", "Show possible moves", "Dots show where a piece can go", progress.showLegalMoves, (v) => { progress.showLegalMoves = v; }),
          toggleRow("🧑‍🏫", "Coach tips", "Warnings about risky moves, plus helpful tips", progress.coachTips, (v) => { progress.coachTips = v; }),
          toggleRow("🚨", "Danger alerts", "Red circles on your pieces that can be captured", progress.dangerAlerts, (v) => { progress.dangerAlerts = v; }),
          toggleRow("🔤", "Board letters and numbers", "Show a-h and 1-8 around the board", progress.showCoordinates, (v) => { progress.showCoordinates = v; }))),
        sectionLabel("Board colors"),
        h("div", { class: "themes" }, THEMES.map((t) => themeChoice(t, t.id === progress.boardTheme, () => { progress.boardTheme = t.id; }))),
        sectionLabel("For grown-ups"),
        card(h("div", { class: "grownups" },
          h("p", null, `Progress is saved in this browser: ${progress.allStars()} stars and ${progress.allWins()} practice wins. It never leaves this device.`),
          kidButton({
            text: "Reset progress", emoji: "🗑️", color: "red", small: true,
            onClick: () => confirmDialog({
              title: "Reset all progress?", message: "This clears every lesson star and practice win. It can't be undone.",
              confirmText: "Reset", onConfirm: () => progress.resetProgress(),
            }),
          }))),
        h("p", { class: "fine" }, h("a", { href: "../" }, "Download the Android app"))));
  };
  render();
  const unsub = progress.subscribe(render);
  return { el: root, destroy: unsub };
}
