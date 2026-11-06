// Entry point: sets up shared services and moves between screens. Port of Navigation.kt.
import { installDefs } from "./ui/art.js";
import { h } from "./ui/dom.js";
import { kidButton } from "./ui/components.js";
import { ProgressStore } from "./progress.js";
import { SoundFx } from "./sound.js";
import { AiClient } from "./ai-client.js";
import { homeScreen, lessonMapScreen, practiceSetupScreen, settingsScreen } from "./screens/home.js";
import { lessonScreen } from "./screens/lesson.js";
import { matchScreen } from "./screens/match.js";

const app = document.getElementById("app");
installDefs();

const progress = new ProgressStore();
const sound = new SoundFx(() => progress.soundOn);
const ai = new AiClient();

const FACTORIES = {
  home: homeScreen,
  lessons: lessonMapScreen,
  lesson: lessonScreen,
  practice: practiceSetupScreen,
  match: matchScreen,
  settings: settingsScreen,
};

const stack = [{ name: "home" }];
let current = null;

function show(route) {
  current?.destroy?.();
  current = null;
  app.replaceChildren();
  try {
    current = FACTORIES[route.name](ctx, route);
    app.append(current.el);
  } catch (err) {
    console.error(err);
    app.append(h("div", { class: "screen oops" },
      h("h2", null, "Oops, something went wrong"),
      h("p", null, "Sir Hops tripped over a pawn. Let's start again."),
      kidButton({ text: "Back home", emoji: "🏠", onClick: () => { stack.splice(1); history.replaceState({ d: 0 }, ""); show(stack[0]); } })));
  }
}

const ctx = {
  progress, sound, ai,
  navigate(route) {
    stack.push(route);
    history.pushState({ d: stack.length - 1 }, "");
    show(route);
  },
  /** Swaps the current screen for another (used for "Next lesson"). */
  replace(route) {
    stack[stack.length - 1] = route;
    history.replaceState({ d: stack.length - 1 }, "");
    show(route);
  },
  back() {
    if (stack.length > 1) history.back();
  },
};

// The browser's back button (and the tablet's back gesture) walk back through screens.
window.addEventListener("popstate", (e) => {
  const depth = e.state && typeof e.state.d === "number" ? Math.min(e.state.d, stack.length - 1) : 0;
  stack.length = depth + 1;
  show(stack[depth]);
});

history.replaceState({ d: 0 }, "");
show(stack[0]);
