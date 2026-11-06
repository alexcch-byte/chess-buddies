// Asks the bots for moves. Uses a Web Worker when the browser allows it, otherwise thinks on the main thread.
import { handle, packCheckers, revivedCheckersMove } from "./ai-handlers.js";

export class AiClient {
  constructor() {
    this.pending = new Map();
    this.nextId = 1;
    this.worker = null;
    try {
      this.worker = new Worker(new URL("./ai-worker.js", import.meta.url), { type: "module" });
      this.worker.onmessage = (e) => {
        const { id, result, error } = e.data;
        const p = this.pending.get(id);
        if (!p) return;
        this.pending.delete(id);
        if (error) p.reject(new Error(error)); else p.resolve(result);
      };
      this.worker.onerror = () => this.#fallBack();
    } catch {
      this.worker = null;
    }
  }

  /** The worker couldn't start (old browser, blocked file). Re-run anything waiting on the main thread. */
  #fallBack() {
    this.worker = null;
    const waiting = [...this.pending.values()];
    this.pending.clear();
    for (const p of waiting) this.#inline(p.msg).then(p.resolve, p.reject);
  }

  // Yield first so the screen can repaint ("Thinking...") before the search blocks the thread.
  #inline(msg) {
    return new Promise((resolve, reject) => {
      setTimeout(() => { try { resolve(handle(msg)); } catch (e) { reject(e); } }, 30);
    });
  }

  #call(msg) {
    if (!this.worker) return this.#inline(msg);
    return new Promise((resolve, reject) => {
      const id = this.nextId++;
      this.pending.set(id, { resolve, reject, msg });
      this.worker.postMessage({ id, ...msg });
    });
  }

  chessChoose(pos, bot) { return this.#call({ op: "chessChoose", fen: pos.toFen(), botId: bot.id }); }
  chessBest(pos, depth, ms) { return this.#call({ op: "chessBest", fen: pos.toFen(), depth, ms }); }
  async checkersChoose(pos, bot) { return revivedCheckersMove(await this.#call({ op: "ckChoose", pos: packCheckers(pos), botId: bot.id })); }
  async checkersBest(pos, depth, ms) { return revivedCheckersMove(await this.#call({ op: "ckBest", pos: packCheckers(pos), depth, ms })); }
  /** Returns [{ path, score }] for every legal move the search got to. */
  checkersScores(pos, depth, ms) { return this.#call({ op: "ckScore", pos: packCheckers(pos), depth, ms }); }
}
