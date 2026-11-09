// Reading QR codes through the device camera, for the Play Nearby "Scan" buttons.
// The reader is jsQR (vendor/jsQR.js, Apache-2.0). It is big and rarely needed, so it is loaded only when someone taps Scan.

let reader = null;

/** The jsQR function. Loaded on first use. */
export async function loadReader() {
  if (reader) return reader;
  const mod = await import("../vendor/jsQR.js");
  // In a browser the file puts jsQR on the global object; under Node it is the module's default export.
  reader = globalThis.jsQR ?? mod.default ?? mod.jsQR;
  if (typeof reader !== "function") throw new Error("The QR reader didn't load.");
  return reader;
}

/** Looks for a QR code in an image ({ data: RGBA pixels, width, height }). Returns its text, or null. */
export function readQr(jsQR, { data, width, height }) {
  // On-screen codes are dark on light, so the (slower) inverted attempt is skipped.
  const hit = jsQR(data, width, height, { inversionAttempts: "dontInvert" });
  return hit ? hit.data : null;
}

/** True when this page may use a camera at all (it needs a secure page, https or localhost, and a camera API). */
export const cameraSupported = (nav = globalThis.navigator) => typeof nav?.mediaDevices?.getUserMedia === "function";

/** A sentence for each way opening the camera can fail. */
export function cameraProblem(err) {
  switch (err?.name) {
    case "NotAllowedError":
    case "SecurityError":
      return "The camera isn't allowed. Allow it for this page in the browser's settings, or paste the code instead.";
    case "NotFoundError":
    case "OverconstrainedError":
      return "This device has no camera the page can use. Paste the code instead.";
    case "NotReadableError":
    case "AbortError":
      return "The camera is being used by another app. Close that app, or paste the code instead.";
    default:
      return "The camera didn't start. Paste the code instead.";
  }
}

/**
 * Opens the camera into `video` and looks for QR codes until stop() is called. Each time one is read, onText(text) is called
 * (it may be called again for the same code, so the caller decides what is worth stopping for). Rejects with the camera's
 * own error if it can't be opened. Returns stop(), which also switches the camera off.
 */
export async function startScanning(video, onText, { intervalMs = 120, maxSide = 960 } = {}) {
  const jsQR = await loadReader();
  const stream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } },
  });
  let stopped = false, timer = null;
  const stop = () => {
    stopped = true;
    clearTimeout(timer);
    for (const track of stream.getTracks()) track.stop();
    video.srcObject = null;
  };
  try {
    video.setAttribute("playsinline", "");
    video.muted = true;
    video.srcObject = stream;
    await video.play();
  } catch (err) {
    stop();
    throw err;
  }
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  const look = () => {
    if (stopped) return;
    if (video.readyState >= 2 && video.videoWidth > 0) {
      const scale = Math.min(1, maxSide / Math.max(video.videoWidth, video.videoHeight));
      canvas.width = Math.round(video.videoWidth * scale);
      canvas.height = Math.round(video.videoHeight * scale);
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      const text = readQr(jsQR, ctx.getImageData(0, 0, canvas.width, canvas.height));
      if (text) onText(text);
    }
    if (!stopped) timer = setTimeout(look, intervalMs);
  };
  look();
  return stop;
}
