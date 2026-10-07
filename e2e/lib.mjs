import { chromium } from "playwright";

// Run inside the Playwright image, sharing the web container's network so http://localhost is a secure context (see CLAUDE.md).
export const BASE = process.env.BASE_URL ?? "http://localhost";
const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR;
const INSTANCE_HEADER = "x-pulsehub-instance";

const run = Date.now().toString(36);
const results = [];
const pageErrors = [];
/** page -> backend instance of every WebSocket handshake that page has made, oldest first. */
const socketInstances = new WeakMap();

export function check(name, ok, detail = "") {
  results.push({ name, ok, detail });
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}${detail ? "  — " + detail : ""}`);
}

export async function step(name, fn) {
  try {
    const detail = await fn();
    check(name, true, detail ?? "");
  } catch (error) {
    check(name, false, String(error.message ?? error).split("\n")[0]);
  }
}

/** Prints the tally and exits non-zero if anything failed. Browser errors count as a failure. */
export function finish({ ignoreErrors = () => false } = {}) {
  const relevant = pageErrors.filter((error) => !ignoreErrors(error));
  check("no page errors or console errors in any browser", relevant.length === 0, relevant.slice(0, 5).join(" | "));
  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  process.exit(failed.length ? 1 : 0);
}

export async function screenshot(page, name) {
  if (SCREENSHOT_DIR) await page.screenshot({ path: `${SCREENSHOT_DIR}/${name}.png` });
}

export function launchBrowser() {
  return chromium.launch({
    args: ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"],
  });
}

export async function register(name) {
  const response = await fetch(`${BASE}/api/v1/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, email: `${name.toLowerCase()}.${run}@pulsehub.dev`, password: "Secret123!" }),
  });
  if (!response.ok) throw new Error(`register ${name}: ${response.status} ${await response.text()}`);
  return response.json();
}

export async function openSession(browser, auth, label, { camera = true } = {}) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  await context.addInitScript(
    ([token, user, hasCamera]) => {
      localStorage.setItem("pulsehub.token", token);
      localStorage.setItem("pulsehub.user", JSON.stringify(user));
      if (!hasCamera) {
        const original = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
        navigator.mediaDevices.getUserMedia = (constraints) =>
          constraints?.video
            ? Promise.reject(new DOMException("Requested device not found", "NotFoundError"))
            : original(constraints);
      }
    },
    [auth.token, { id: auth.id, name: auth.name, email: auth.email }, camera],
  );
  const page = await newPage(context, label);
  return { context, page, auth, label };
}

export async function newPage(context, label) {
  const page = await context.newPage();
  page.on("pageerror", (error) => pageErrors.push(`${label}: ${error.message}`));
  page.on("console", (message) => {
    if (message.type() === "error" && !message.text().includes("favicon")) pageErrors.push(`${label} console: ${message.text()}`);
  });

  // The backend stamps its instance id on every response, the WebSocket handshake included.
  const instances = [];
  socketInstances.set(page, instances);
  const cdp = await context.newCDPSession(page);
  await cdp.send("Network.enable");
  cdp.on("Network.webSocketHandshakeResponseReceived", ({ response }) => {
    const header = Object.keys(response.headers).find((name) => name.toLowerCase() === INSTANCE_HEADER);
    instances.push(header ? response.headers[header] : "unknown");
  });
  return page;
}

/** How many WebSocket connections this page has opened so far. */
export const socketCount = (page) => socketInstances.get(page).length;

/** The backend instance holding this page's WebSocket, once it has opened at least `minSockets` of them. */
export async function socketInstance(page, { minSockets = 1, timeout = 30000 } = {}) {
  const instances = socketInstances.get(page);
  const deadline = Date.now() + timeout;
  while (instances.length < minSockets) {
    if (Date.now() > deadline) throw new Error(`no WebSocket handshake #${minSockets} within ${timeout}ms`);
    await page.waitForTimeout(200);
  }
  return instances[instances.length - 1];
}

export async function openChatWith(session, other) {
  await session.page.goto(`${BASE}/chat?with=${other.id}`);
  await startButton(session.page).waitFor({ timeout: 15000 });
}

export const callDialog = (page, peerName) => page.getByRole("dialog", { name: `Call with ${peerName}` });
export const incomingDialog = (page, peerName) => page.getByRole("dialog", { name: `Incoming call from ${peerName}` });
export const startButton = (page) => page.getByRole("button", { name: "Start a video call" });

/** Resolves once the labelled <video> is really rendering frames (dimensions known and playback advancing). */
export async function expectPlayingVideo(page, label) {
  const video = page.getByLabel(label);
  await video.waitFor({ state: "visible", timeout: 20000 });
  const handle = await video.elementHandle();
  await page.waitForFunction((el) => el.videoWidth > 0 && el.currentTime > 0.3 && !el.paused, handle, { timeout: 20000 });
  return page.evaluate((el) => `${el.videoWidth}x${el.videoHeight} t=${el.currentTime.toFixed(1)}s`, handle);
}

export async function expectActive(page, peerName) {
  await callDialog(page, peerName).getByText(/^\d\d:\d\d$/).waitFor({ timeout: 20000 });
}

export async function expectNoCallUi(page) {
  await page.getByRole("dialog").waitFor({ state: "detached", timeout: 10000 });
}
