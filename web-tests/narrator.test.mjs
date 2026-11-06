// Tests for the browser narrator: what gets read out, which voice, and when a line is repeated.
// Run with: node --test web-tests/narrator.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { cleanForSpeech, chooseVoice, Narrator } from "../docs/play/js/narrator.js";
import { GAMES } from "../docs/play/js/lessons/catalog.js";

const LESSONS = [...GAMES.chess.lessons, ...GAMES.checkers.lessons];

class FakeUtterance {
  constructor(text) { this.text = text; }
}

function fakeWindow(voices = [{ lang: "en-US", localService: true, name: "Test" }]) {
  const spoken = [];
  const synth = {
    cancelled: 0,
    getVoices: () => voices,
    addEventListener() {},
    cancel() { this.cancelled++; },
    speak(u) { spoken.push(u); },
  };
  return { win: { speechSynthesis: synth, SpeechSynthesisUtterance: FakeUtterance }, spoken, synth };
}

test("pictures and arrows are not read", () => {
  assert.equal(cleanForSpeech("💡 How about this: Knight to f3?"), "How about this: Knight to f3?");
  assert.equal(cleanForSpeech("✨ You have a double jump! Can you find it?"), "You have a double jump! Can you find it?");
  assert.equal(cleanForSpeech("Next ➔"), "Next");
  assert.equal(cleanForSpeech("⚠️ Warning!"), "Warning!");
  assert.equal(cleanForSpeech("🧑‍🏫 Coach tips"), "Coach tips");
  assert.equal(cleanForSpeech("🎉"), "");
  assert.equal(cleanForSpeech("Got it! 2 more stars to go."), "Got it! 2 more stars to go.");
  assert.equal(cleanForSpeech("  one \n  two  "), "one two");
});

test("every lesson line has something to say", () => {
  let lines = 0;
  for (const lesson of LESSONS) {
    const texts = [...lesson.intro, ...lesson.challenges.flatMap((c) => [c.instruction, c.wrongText].filter(Boolean))];
    for (const t of texts) {
      lines++;
      const spoken = cleanForSpeech(t);
      assert.ok(spoken.length > 0, `nothing left to say in: ${t}`);
      assert.ok(!/\p{So}/u.test(spoken), `symbols left in: ${spoken}`);
    }
  }
  assert.ok(lines > 100);
});

test("voice choice prefers US English that runs on the device", () => {
  const voices = [
    { lang: "fr-FR", localService: true, name: "fr" },
    { lang: "en-GB", localService: true, name: "gb" },
    { lang: "en-US", localService: false, name: "us-online" },
    { lang: "en-US", localService: true, name: "us-local" },
  ];
  assert.equal(chooseVoice(voices).name, "us-local");
  assert.equal(chooseVoice(voices.slice(0, 3)).name, "us-online");
  assert.equal(chooseVoice(voices.slice(0, 2)).name, "gb");
  assert.equal(chooseVoice([voices[0]]), null);
  assert.equal(chooseVoice([]), null);
});

test("speaks cleaned text, interrupting what was being said", () => {
  const { win, spoken, synth } = fakeWindow();
  const n = new Narrator(win);
  n.say("💡 Move the pawn!");
  assert.equal(spoken.length, 1);
  assert.equal(spoken[0].text, "Move the pawn!");
  assert.equal(spoken[0].lang, "en-US");
  assert.ok(spoken[0].rate < 1);
  assert.equal(synth.cancelled, 1);
});

test("the same line is not repeated straight away, unless asked for", () => {
  let now = 1000;
  const { win, spoken } = fakeWindow();
  const n = new Narrator(win, () => now);
  n.say("Capture all the pawns.");
  now += 5000;
  n.say("Capture all the pawns.");
  assert.equal(spoken.length, 1, "repeat within 25 s is skipped");
  n.say("Capture all the pawns.", { force: true });
  assert.equal(spoken.length, 2, "the Listen button always repeats");
  now += 30_000;
  n.say("Capture all the pawns.");
  assert.equal(spoken.length, 3, "after a while it can be read again");
});

test("a new lesson screen starts fresh and an old one can't silence it", () => {
  const { win, spoken, synth } = fakeWindow();
  const n = new Narrator(win);
  const a = {}, b = {};
  n.attach(a);
  n.say("Welcome!");
  n.attach(b);          // the next lesson opens before the old one is torn down
  n.say("Welcome!");
  assert.equal(spoken.length, 2, "the new screen reads its first line even if it matches the old one");
  const cancelled = synth.cancelled;
  n.detach(a);          // the old screen goes away: must not cut the new one off
  assert.equal(synth.cancelled, cancelled);
  n.detach(b);
  assert.equal(synth.cancelled, cancelled + 1);
});

test("no speech support means no button and no errors", () => {
  const n = new Narrator({});
  assert.equal(n.available, false);
  n.say("Hello");
  n.stop();
});

test("a browser with no voices is treated as silent once it has had time to report them", async () => {
  const { win, spoken } = fakeWindow([]);
  const n = new Narrator(win);
  assert.equal(n.available, true, "voices may still be loading");
  await new Promise((r) => setTimeout(r, 1700));
  assert.equal(n.available, false);
  n.say("Hello");
  assert.equal(spoken.length, 0);
});
