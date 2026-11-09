// Play Nearby: finding a friend in another browser. Port of NearbyScreen.kt / NearbyController.kt, except that browsers
// can't scan the Wi-Fi or Bluetooth for each other. Instead a grown-up swaps two codes: the host sends an invite (as a
// link), the friend opens it and sends a reply code back. After that the two browsers talk directly (see nearby/rtc.js).
import { h, fromMarkup } from "../ui/dom.js";
import { encodeQR, qrSvg } from "../ui/qr.js";
import { pieceIcon, checkerIcon } from "../ui/art.js";
import { kidButton, topBar, card, sectionLabel, openDialog } from "../ui/components.js";
import { cameraSupported, cameraProblem, startScanning } from "../ui/qr-scan.js";
import { KING } from "../chess/position.js";
import { Checker } from "../checkers/checkers.js";
import { GAMES } from "../lessons/catalog.js";
import { NearbySession, runSession } from "../nearby/session.js";
import { RtcHost, RtcGuest, nearbySupported } from "../nearby/rtc.js";
import { inviteLink, extractCode, SignalError } from "../nearby/signal.js";

const REJECTIONS = {
  version: "Your friend has a different version of Chess Buddies. Reload this page on both devices and try again.",
  busy: "That game already has two players.",
};
const NO_CONNECTION = "It didn't connect. Both devices need to be on the same Wi-Fi, or switch on \"My friend is far away\". Then tap Try again for a fresh invite.";

function waitingDots(text) {
  return h("div", { class: "waiting thinking", role: "status" }, text, h("i", null, "."), h("i", null, "."), h("i", null, "."));
}

function stepCard(number, title, children, { done = false } = {}) {
  return card(h("div", { class: `step${done ? " done" : ""}` },
    h("div", { class: "step-head" }, h("span", { class: "step-num" }, done ? "✓" : String(number)), h("span", null, title)),
    children), {});
}

export function nearbyScreen(ctx, { game, joinCode = null }) {
  const { back } = ctx;
  const root = h("div", { class: "screen" });
  const S = {
    stage: "choose", // choose | hosting | join | joining | reply
    game, hostFirst: true, wide: false,
    invite: "", reply: "", pasted: "", error: null, busy: false, connecting: false, copied: "",
  };
  let session = null, stopRun = null, source = null, handedOff = false, timer = null, closed = false;

  const teardown = () => {
    clearTimeout(timer);
    stopRun?.();
    session?.leave();
    session = null; source = null; stopRun = null;
    S.connecting = false;
  };

  const fail = (message, stage = S.stage) => {
    teardown();
    S.error = message; S.busy = false; S.stage = stage;
    render();
  };

  const onEvent = (e) => {
    if (closed) return;
    if (e.type === "joined") {
      handedOff = true;
      clearTimeout(timer);
      session.attach(null); // anything that arrives next waits for the match screen
      ctx.replace({ name: "match", game: e.kind, botId: null, playerFirst: e.iAmFirst, nearby: session });
    } else if (e.type === "rejected") {
      fail(REJECTIONS[e.reason] ?? NO_CONNECTION, "choose");
    } else if (e.type === "couldNotConnect") {
      fail(NO_CONNECTION, "choose");
    }
  };

  const begin = (s, src) => {
    session = s; source = src;
    s.attach(onEvent);
    stopRun = runSession(s);
  };

  // ---------- hosting ----------

  async function startHosting() {
    teardown();
    Object.assign(S, { stage: "hosting", invite: "", pasted: "", error: null, busy: true });
    render();
    try {
      const host = new RtcHost({ wide: S.wide });
      begin(NearbySession.host(S.game, S.hostFirst, host), host);
      S.invite = await host.createInvite();
    } catch {
      fail("This browser couldn't make an invite. Try a newer browser.", "choose");
      return;
    }
    S.busy = false;
    render();
  }

  async function submitReply() {
    if (S.busy || !source) return;
    S.error = null; S.busy = true; render();
    try {
      await source.acceptReply(S.pasted);
      S.connecting = true;
      timer = setTimeout(() => fail(NO_CONNECTION, "choose"), 25_000);
    } catch (err) {
      S.error = err instanceof SignalError ? err.message : "That reply didn't work. Ask your friend to send it again.";
    }
    S.busy = false;
    render();
  }

  // ---------- joining ----------

  async function startJoining(code) {
    teardown();
    Object.assign(S, { stage: "joining", error: null, busy: true, reply: "" });
    render();
    try {
      const guest = new RtcGuest();
      begin(NearbySession.guest(guest), guest);
      S.reply = await guest.join(code);
      S.stage = "reply";
    } catch (err) {
      teardown();
      S.stage = "join";
      S.pasted = code ?? "";
      S.error = err instanceof SignalError ? err.message : "That invite didn't work. Ask your friend for a new one.";
    }
    S.busy = false;
    render();
  }

  // ---------- small helpers ----------

  async function copy(text, which) {
    let ok = false;
    try { await navigator.clipboard.writeText(text); ok = true; } catch { /* fall back below */ }
    if (!ok) {
      const box = root.querySelector(".code-box");
      if (box) { box.focus(); box.select(); try { ok = document.execCommand("copy"); } catch { ok = false; } }
    }
    S.copied = ok ? which : "";
    render();
    setTimeout(() => { if (S.copied === which && !closed) { S.copied = ""; render(); } }, 1800);
  }

  const canShare = typeof navigator.share === "function";
  const share = (text) => navigator.share({ title: "Chess Buddies", text }).catch(() => {});

  const codeBox = (value, { readonly = true, onInput = null, label }) => {
    const box = h("textarea", {
      class: "code-box", rows: "4", spellcheck: "false", autocomplete: "off", autocapitalize: "off", "aria-label": label,
      readonly: readonly || null, placeholder: readonly ? null : "Paste it here",
    });
    box.value = value;
    if (readonly) box.addEventListener("focus", () => box.select());
    if (onInput) box.addEventListener("input", () => onInput(box.value));
    return box;
  };

  // ---------- scanning a friend's QR code ----------

  let scanStop = null, scanClose = null, scanToken = 0;
  const closeScanner = () => {
    scanToken++;
    scanStop?.(); scanStop = null;
    scanClose?.(); scanClose = null;
  };

  /** Opens the camera in a dialog and waits for a code of the wanted kind ("offer" = an invite, "answer" = a reply). */
  function openScanner(wanted, onCode) {
    closeScanner();
    const mine = scanToken;
    const video = h("video", { class: "scan-video", autoplay: true, muted: true, playsinline: true, "aria-label": "Camera" });
    const status = h("p", { class: "scan-status", role: "status" }, "Starting the camera…");
    scanClose = openDialog(h("div", { class: "dialog-body scan" },
      h("h2", null, wanted === "offer" ? "Scan the invite" : "Scan their reply"),
      h("div", { class: "scan-view" }, video, h("div", { class: "scan-frame", "aria-hidden": "true" })),
      status,
      kidButton({ text: "Cancel", color: "grey", small: true, onClick: closeScanner })), { onDismiss: closeScanner });
    startScanning(video, (text) => {
      if (mine !== scanToken) return;
      const code = extractCode(text);
      if (!code) { status.textContent = "That QR code isn't from Chess Buddies. Try your friend's screen."; return; }
      const role = code[3] === "O" ? "offer" : "answer";
      if (role !== wanted) {
        status.textContent = role === "offer" ? "That's an invite. Scan your friend's reply instead." : "That's a reply. Scan the invite instead.";
        return;
      }
      closeScanner();
      onCode(code);
    }).then(
      (stop) => {
        if (mine !== scanToken) { stop(); return; } // closed before the camera finished opening
        scanStop = stop;
        status.textContent = "Point the camera at the QR code on your friend's screen.";
      },
      (err) => { if (mine === scanToken) status.textContent = cameraProblem(err); },
    );
  }

  const scanButton = (text, wanted, onCode) => (cameraSupported()
    ? kidButton({ text, emoji: "📷", color: "blue", small: true, onClick: () => openScanner(wanted, onCode) })
    : null);

  /** A QR code for some text, with a line under it saying who points a camera at it. */
  const qrBox = (text, label, caption) => h("figure", { class: "qr" },
    fromMarkup(qrSvg(encodeQR(text), { label })),
    h("figcaption", { class: "fine" }, caption));

  /** Greys a button out (or not) in place, so typing in the box next to it doesn't redraw the whole screen. */
  const disabledWhen = (button, off) => { button.disabled = off; return button; };

  const errorNote = () => (S.error ? h("div", { class: "note bad", role: "alert" }, h("span", { "aria-hidden": "true" }, "⚠️"), h("span", null, S.error)) : null);

  // ---------- screens ----------

  const chooseView = () => {
    const title = GAMES[S.game].title;
    const supported = nearbySupported();
    const isChess = S.game === "chess";
    const side = (first) => {
      const label = first ? (isChess ? "White" : "Red") : "Black";
      const icon = isChess ? pieceIcon(first ? KING : -KING, "icon chip-icon") : checkerIcon(first ? Checker.RED : Checker.BLACK, "icon chip-icon");
      return h("button", {
        class: `chip${S.hostFirst === first ? " selected" : ""}`, type: "button", role: "radio", "aria-checked": String(S.hostFirst === first),
        onclick: () => { S.hostFirst = first; render(); },
      }, icon, h("span", null, label));
    };
    return [
      topBar("Play Nearby", back),
      h("div", { class: "scroll pad narrow-col" },
        card(h("div", { class: "nearby-intro" },
          h("p", null, `Play ${title} with a friend on another tablet, phone or computer. You both need Chess Buddies open in a browser.`),
          h("p", { class: "fine-left" }, "A grown-up needs to help send two short codes. Moves go straight between the two devices, never through a game server."))),
        errorNote(),
        supported ? [
          sectionLabel("I want to be…"),
          h("div", { class: "chips" }, side(true), side(false)),
          card(h("div", { class: "toggles" },
            h("button", { class: "toggle-row", type: "button", role: "switch", "aria-checked": String(S.wide), onclick: () => { S.wide = !S.wide; render(); } },
              h("span", { class: "toggle-emoji" }, "🌍"),
              h("span", { class: "toggle-text" },
                h("span", { class: "toggle-title" }, "My friend is far away"),
                h("span", { class: "toggle-sub" }, S.wide
                  ? "On: works from anywhere. It asks a free Google service for each device's internet address."
                  : "Off: both devices are on the same Wi-Fi. Nothing is contacted.")),
              h("span", { class: `switch${S.wide ? " on" : ""}` }, h("span", { class: "knob" }))))),
          kidButton({ text: "Start a game", emoji: "🎮", color: "green", wide: true, onClick: startHosting }),
          kidButton({ text: "Join a game", emoji: "🔗", color: "blue", wide: true, onClick: () => { Object.assign(S, { stage: "join", error: null, pasted: "" }); render(); } }),
        ] : h("div", { class: "note bad", role: "alert" }, h("span", { "aria-hidden": "true" }, "⚠️"),
          h("span", null, "This browser can't connect to another device. Try a recent Chrome, Edge, Firefox or Safari."))),
    ];
  };

  const hostingView = () => {
    const link = S.invite ? inviteLink(S.invite) : "";
    return [
      topBar("Invite your friend", back),
      h("div", { class: "scroll pad narrow-col" },
        stepCard(1, "Send this link to your friend", S.busy && !S.invite
          ? waitingDots("Making your invite")
          : [
            h("p", { class: "step-help" }, "Your friend points their camera at this picture, or opens the link. Either takes them straight to joining."),
            qrBox(link, "Invite QR code", "Scan with a phone or tablet camera"),
            h("p", { class: "step-help" }, "Or send them the link:"),
            codeBox(link, { label: "Invite link" }),
            h("div", { class: "row" },
              kidButton({ text: S.copied === "invite" ? "Copied!" : "Copy link", emoji: S.copied === "invite" ? "✅" : "📋", color: "blue", small: true, onClick: () => copy(link, "invite") }),
              canShare ? kidButton({ text: "Share…", emoji: "📤", color: "purple", small: true, onClick: () => share(`Let's play ${GAMES[S.game].title} together! ${link}`) }) : null),
          ], { done: S.connecting }),
        S.invite ? stepCard(2, "Paste their reply here", S.connecting ? waitingDots("Connecting") : [
          h("p", { class: "step-help" }, "After they open your link, their device shows a reply with a QR code. Scan it, or ask them to send the reply to you."),
          scanButton("Scan their reply", "answer", (code) => { S.pasted = code; submitReply(); }),
          cameraSupported() ? h("p", { class: "step-help" }, "Or paste it here:") : null,
          codeBox(S.pasted, { readonly: false, label: "Reply from your friend", onInput: (v) => { S.pasted = v; connectBtn.disabled = !v.trim(); } }),
          errorNote(),
          disabledWhen(connectBtn = kidButton({ text: "Connect", emoji: "🔌", color: "green", onClick: submitReply }), S.busy || !S.pasted.trim()),
        ]) : errorNote(),
        S.connecting ? h("div", { class: "stack" }, kidButton({ text: "Try again", emoji: "🔄", color: "grey", small: true, onClick: startHosting })) : null),
    ];
  };
  let connectBtn = null;

  const joinView = () => [
    topBar("Join a game", back),
    h("div", { class: "scroll pad narrow-col" },
      stepCard(1, "Scan or paste your friend's invite", [
        h("p", { class: "step-help" }, "If they sent a link, just open it and you will land here already filled in."),
        scanButton("Scan the invite", "offer", (code) => startJoining(code)),
        cameraSupported() ? h("p", { class: "step-help" }, "Or paste the link or code here:") : null,
        codeBox(S.pasted, { readonly: false, label: "Invite from your friend", onInput: (v) => { S.pasted = v; nextBtn.disabled = !v.trim(); } }),
        errorNote(),
        disabledWhen(nextBtn = kidButton({ text: "Next", emoji: "➡️", color: "green", onClick: () => startJoining(S.pasted) }), !S.pasted.trim()),
      ])),
  ];
  let nextBtn = null;

  const joiningView = () => [
    topBar("Join a game", back),
    h("div", { class: "scroll pad narrow-col" }, card(waitingDots("Getting ready"))),
  ];

  const replyView = () => [
    topBar("Join a game", back),
    h("div", { class: "scroll pad narrow-col" },
      stepCard(1, "Send this reply back to your friend", [
        h("p", { class: "step-help" }, "They paste it into their Chess Buddies and tap Connect, or point their camera at this picture."),
        qrBox(S.reply, "Reply QR code", "Show this to your friend's camera"),
        codeBox(S.reply, { label: "Your reply" }),
        h("div", { class: "row" },
          kidButton({ text: S.copied === "reply" ? "Copied!" : "Copy reply", emoji: S.copied === "reply" ? "✅" : "📋", color: "blue", small: true, onClick: () => copy(S.reply, "reply") }),
          canShare ? kidButton({ text: "Share…", emoji: "📤", color: "purple", small: true, onClick: () => share(S.reply) }) : null),
      ], { done: false }),
      stepCard(2, "Keep this page open", waitingDots("Waiting for your friend")),
      errorNote()),
  ];

  const VIEWS = { choose: chooseView, hosting: hostingView, join: joinView, joining: joiningView, reply: replyView };

  function render() {
    if (closed) return;
    root.replaceChildren(...VIEWS[S.stage]().flat(Infinity).filter(Boolean));
  }

  render();
  if (joinCode) startJoining(joinCode);

  return {
    el: root,
    destroy() {
      closed = true;
      clearTimeout(timer);
      closeScanner();
      if (!handedOff) teardown();
    },
  };
}
