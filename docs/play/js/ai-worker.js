// Web Worker entry: thinks about moves off the main thread so the page never freezes.
import { handle } from "./ai-handlers.js";

self.onmessage = (e) => {
  const { id, ...msg } = e.data;
  try {
    self.postMessage({ id, result: handle(msg) });
  } catch (err) {
    self.postMessage({ id, error: String(err && err.message ? err.message : err) });
  }
};
