// Small synthesized sound effects, so the page ships with no audio files. Port of audio/SoundFx.kt.

const SAMPLE_RATE = 22050;
const note = (freq, ms, o = {}) => ({
  freq, ms, wave: o.wave ?? "sine", volume: o.volume ?? 0.5, endFreq: o.endFreq ?? freq, noise: o.noise ?? 0, decay: o.decay ?? 6,
});

const EFFECTS = {
  select: () => [note(1250, 30, { volume: 0.18, decay: 9 })],
  move: () => [note(240, 75, { wave: "triangle", volume: 0.55, endFreq: 150, noise: 0.25, decay: 9 })],
  capture: () => [
    note(330, 45, { wave: "triangle", volume: 0.5, endFreq: 220, noise: 0.35, decay: 7 }),
    note(170, 110, { wave: "triangle", volume: 0.6, endFreq: 100, noise: 0.3, decay: 6 }),
  ],
  check: () => [note(880, 90, { wave: "triangle", volume: 0.4, decay: 3 }), note(660, 140, { wave: "triangle", volume: 0.4, decay: 3 })],
  illegal: () => [note(160, 140, { wave: "square", volume: 0.12, endFreq: 120, decay: 4 })],
  star: () => [note(1568, 70, { volume: 0.35, decay: 3 }), note(2093, 220, { volume: 0.35, decay: 5 })],
  success: () => [523, 659, 784].map((f) => note(f, 95, { wave: "triangle", volume: 0.4, decay: 2 })).concat(note(1047, 300, { wave: "triangle", volume: 0.45, decay: 4 })),
  win: () => [523, 659, 784, 1047, 784].map((f) => note(f, 120, { wave: "triangle", volume: 0.42, decay: 1.5 })).concat(note(1047, 520, { wave: "triangle", volume: 0.45, decay: 3 })),
  lose: () => [note(392, 220, { wave: "triangle", volume: 0.35, decay: 2 }), note(330, 220, { wave: "triangle", volume: 0.35, decay: 2 }), note(262, 420, { wave: "triangle", volume: 0.35, decay: 3 })],
  oops: () => [note(440, 120, { wave: "triangle", volume: 0.35, decay: 3 }), note(330, 220, { wave: "triangle", volume: 0.35, endFreq: 300, decay: 4 })],
  promote: () => [note(600, 260, { volume: 0.35, endFreq: 1500, decay: 2 }), note(1760, 260, { volume: 0.3, decay: 5 })],
};

function render(notes) {
  const counts = notes.map((n) => Math.floor((SAMPLE_RATE * n.ms) / 1000));
  const out = new Float32Array(counts.reduce((a, b) => a + b, 0));
  let offset = 0;
  notes.forEach((n, k) => {
    const len = counts[k];
    const attack = Math.max(1, Math.floor(SAMPLE_RATE * 0.004));
    let phase = 0;
    for (let i = 0; i < len; i++) {
      const t = i / len;
      const freq = n.freq + (n.endFreq - n.freq) * t;
      phase += (2 * Math.PI * freq) / SAMPLE_RATE;
      const s = Math.sin(phase);
      let sample = n.wave === "sine" ? s : n.wave === "triangle" ? (2 / Math.PI) * Math.asin(s) : s >= 0 ? 1 : -1;
      if (n.noise > 0) sample = sample * (1 - n.noise) + (Math.random() * 2 - 1) * n.noise;
      const env = (i < attack ? i / attack : 1) * Math.exp(-n.decay * t) * (1 - t * t * t);
      out[offset + i] = Math.max(-1, Math.min(1, sample * env * n.volume));
    }
    offset += len;
  });
  return out;
}

export class SoundFx {
  constructor(isEnabled) {
    this.isEnabled = isEnabled;
    this.ctx = null;
    this.cache = new Map();
    this.playing = 0;
    // Browsers only allow audio after a tap, so create the context on the first one.
    const unlock = () => { this.#context()?.resume?.(); };
    window.addEventListener("pointerdown", unlock, { passive: true });
  }

  #context() {
    if (this.ctx) return this.ctx;
    const Ctor = window.AudioContext || window.webkitAudioContext;
    if (!Ctor) return null;
    try { this.ctx = new Ctor(); } catch { this.ctx = null; }
    return this.ctx;
  }

  play(effect) {
    if (!this.isEnabled() || this.playing >= 3) return;
    try {
      const ctx = this.#context();
      if (!ctx || ctx.state === "suspended") { ctx?.resume?.(); if (!ctx || ctx.state === "suspended") return; }
      let buffer = this.cache.get(effect);
      if (!buffer) {
        const pcm = render(EFFECTS[effect]());
        buffer = ctx.createBuffer(1, pcm.length, SAMPLE_RATE);
        buffer.copyToChannel(pcm, 0);
        this.cache.set(effect, buffer);
      }
      const src = ctx.createBufferSource();
      src.buffer = buffer;
      src.connect(ctx.destination);
      this.playing++;
      src.onended = () => { this.playing--; };
      src.start();
    } catch {
      // Sound is a nice extra; never break the game over it.
    }
  }
}
