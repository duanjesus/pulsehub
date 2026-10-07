import {
  BASE,
  callDialog,
  expectActive,
  expectNoCallUi,
  expectPlayingVideo,
  finish,
  incomingDialog,
  launchBrowser,
  newPage,
  openChatWith,
  openSession,
  register,
  screenshot,
  startButton,
  step,
} from "./lib.mjs";

async function liveLocalTracks(page) {
  // Every track the app opened must be stopped once the call is over (camera light off).
  return page.evaluate(() => window.__openedTracks?.filter((t) => t.readyState === "live").length ?? -1);
}

const browser = await launchBrowser();

try {
  const [adaAuth, graceAuth, linusAuth, noCamAuth] = await Promise.all(
    ["Ada", "Grace", "Linus", "Nocam"].map(register),
  );

  await step("GET /calls/ice-servers returns the configured STUN server", async () => {
    const response = await fetch(`${BASE}/api/v1/calls/ice-servers`, {
      headers: { Authorization: `Bearer ${adaAuth.token}` },
    });
    const body = await response.json();
    if (response.status !== 200 || body[0]?.urls?.[0] !== "stun:stun.l.google.com:19302") {
      throw new Error(`${response.status} ${JSON.stringify(body)}`);
    }
    return JSON.stringify(body);
  });

  await step("GET /calls/ice-servers requires authentication", async () => {
    const response = await fetch(`${BASE}/api/v1/calls/ice-servers`);
    if (response.status !== 401 && response.status !== 403) throw new Error(`status ${response.status}`);
    return `status ${response.status}`;
  });

  const ada = await openSession(browser, adaAuth, "ada");
  const grace = await openSession(browser, graceAuth, "grace");
  for (const session of [ada, grace]) {
    await session.context.addInitScript(() => {
      window.__openedTracks = [];
      const original = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
      navigator.mediaDevices.getUserMedia = async (constraints) => {
        const stream = await original(constraints);
        window.__openedTracks.push(...stream.getTracks());
        return stream;
      };
    });
  }

  // ---------- 1. Callee offline ----------
  await openChatWith(ada, linusAuth);
  await step("calling a user with no open session reports them offline", async () => {
    await startButton(ada.page).click();
    await ada.page.getByRole("status").getByText("Linus is offline.").waitFor({ timeout: 10000 });
    await expectNoCallUi(ada.page);
  });

  // ---------- 2. Happy path ----------
  await openChatWith(grace, adaAuth);
  await openChatWith(ada, graceAuth);
  await ada.page.waitForTimeout(1500);

  await step("caller sees 'Calling…' and callee gets the incoming-call prompt", async () => {
    await startButton(ada.page).click();
    await callDialog(ada.page, "Grace").getByText("Calling…").first().waitFor({ timeout: 10000 });
    await incomingDialog(grace.page, "Ada").waitFor({ timeout: 10000 });
  });
  await screenshot(grace.page, "call-incoming");
  await screenshot(ada.page, "call-outgoing");

  await step("accepting connects both sides (call timer running)", async () => {
    await incomingDialog(grace.page, "Ada").getByRole("button", { name: "Accept" }).click();
    await Promise.all([expectActive(ada.page, "Grace"), expectActive(grace.page, "Ada")]);
  });
  await step("Ada receives Grace's video over WebRTC", () => expectPlayingVideo(ada.page, "Grace's video"));
  await step("Grace receives Ada's video over WebRTC", () => expectPlayingVideo(grace.page, "Ada's video"));
  await step("both see their own camera preview", async () => {
    const a = await expectPlayingVideo(ada.page, "Your camera");
    const g = await expectPlayingVideo(grace.page, "Your camera");
    return `ada ${a}, grace ${g}`;
  });
  await step("the remote stream carries live audio and video tracks on both sides", async () => {
    const stats = async (page) =>
      page.evaluate(async () => {
        const video = document.querySelector("video:not([muted])");
        return { tracks: video.srcObject.getTracks().map((t) => `${t.kind}:${t.readyState}`) };
      });
    const a = await stats(ada.page);
    const g = await stats(grace.page);
    const expected = ["audio:live", "video:live"];
    for (const side of [a, g]) {
      if (expected.some((entry) => !side.tracks.includes(entry))) throw new Error(JSON.stringify(side));
    }
    return `ada ${a.tracks.join(",")} | grace ${g.tracks.join(",")}`;
  });
  await ada.page.waitForTimeout(1500);
  await screenshot(ada.page, "call-active");

  await step("mute and camera toggles flip the local tracks", async () => {
    const dialog = callDialog(ada.page, "Grace");
    await dialog.getByRole("button", { name: "Mute" }).click();
    await dialog.getByRole("button", { name: "Unmute", pressed: true }).waitFor({ timeout: 5000 });
    await dialog.getByRole("button", { name: "Turn camera off" }).click();
    await dialog.getByRole("button", { name: "Turn camera on", pressed: true }).waitFor({ timeout: 5000 });
    const enabled = await ada.page.evaluate(() => window.__openedTracks.map((t) => `${t.kind}:${t.enabled}`).join(","));
    if (enabled.includes("true")) throw new Error(enabled);
    await dialog.getByRole("button", { name: "Unmute" }).click();
    await dialog.getByRole("button", { name: "Turn camera on" }).click();
    return enabled;
  });

  await step("the call survives navigating to another page", async () => {
    // The overlay covers the sidebar, so navigate through the router instead of clicking.
    await grace.page.evaluate(() => {
      window.history.pushState({}, "", "/profile");
      window.dispatchEvent(new PopStateEvent("popstate"));
    });
    await grace.page.waitForTimeout(1000);
    await expectActive(grace.page, "Ada");
    await expectPlayingVideo(grace.page, "Ada's video");
  });

  // ---------- 3. Busy ----------
  const linus = await openSession(browser, linusAuth, "linus");
  await openChatWith(linus, adaAuth);
  await step("a third user calling someone mid-call is told they're busy", async () => {
    await startButton(linus.page).click();
    await linus.page.getByRole("status").getByText("Ada is on another call.").waitFor({ timeout: 10000 });
    await expectActive(ada.page, "Grace");
    if (await incomingDialog(ada.page, "Linus").count()) throw new Error("Ada's call was interrupted");
  });

  await step("hanging up ends the call on both sides and releases the devices", async () => {
    await callDialog(grace.page, "Ada").getByRole("button", { name: "Hang up" }).click();
    await ada.page.getByRole("status").getByText("Call ended.").waitFor({ timeout: 10000 });
    await Promise.all([expectNoCallUi(ada.page), expectNoCallUi(grace.page)]);
    const live = [await liveLocalTracks(ada.page), await liveLocalTracks(grace.page)];
    if (live[0] !== 0 || live[1] !== 0) throw new Error(`live tracks left: ${live}`);
    return "0 live tracks on either side";
  });

  // ---------- 4. Decline ----------
  await openChatWith(grace, adaAuth);
  await step("declining tells the caller", async () => {
    await startButton(ada.page).click();
    await incomingDialog(grace.page, "Ada").getByRole("button", { name: "Decline" }).click();
    await ada.page.getByRole("status").getByText("Grace declined the call.").waitFor({ timeout: 10000 });
    await Promise.all([expectNoCallUi(ada.page), expectNoCallUi(grace.page)]);
  });

  // ---------- 5. Caller cancels while ringing ----------
  await ada.page.waitForTimeout(500);
  await step("cancelling while it rings shows the callee a missed call", async () => {
    await startButton(ada.page).click();
    await incomingDialog(grace.page, "Ada").waitFor({ timeout: 10000 });
    await callDialog(ada.page, "Grace").getByRole("button", { name: "Hang up" }).click();
    await grace.page.getByRole("status").getByText("Missed call from Ada.").waitFor({ timeout: 10000 });
    await expectNoCallUi(grace.page);
  });

  // ---------- 6. Callee has two tabs ----------
  const graceTab2 = await newPage(grace.context, "grace-tab2");
  await graceTab2.goto(`${BASE}/`);
  await graceTab2.waitForTimeout(2500);
  await step("with two tabs open, answering in one stops the other ringing", async () => {
    await startButton(ada.page).click();
    await Promise.all([
      incomingDialog(grace.page, "Ada").waitFor({ timeout: 10000 }),
      incomingDialog(graceTab2, "Ada").waitFor({ timeout: 10000 }),
    ]);
    await incomingDialog(grace.page, "Ada").getByRole("button", { name: "Accept" }).click();
    await Promise.all([expectActive(ada.page, "Grace"), expectActive(grace.page, "Ada")]);
    await incomingDialog(graceTab2, "Ada").waitFor({ state: "detached", timeout: 10000 });
    await callDialog(ada.page, "Grace").getByRole("button", { name: "Hang up" }).click();
    await Promise.all([expectNoCallUi(ada.page), expectNoCallUi(grace.page)]);
  });
  await graceTab2.close();
  await ada.page.waitForTimeout(1500);

  // ---------- 7. Voice-only fallback ----------
  const nocam = await openSession(browser, noCamAuth, "nocam", { camera: false });
  await openChatWith(nocam, adaAuth);
  await openChatWith(ada, noCamAuth);
  await ada.page.waitForTimeout(1500);

  await step("a caller without a camera still receives the callee's video", async () => {
    await startButton(nocam.page).click();
    await incomingDialog(ada.page, "Nocam").getByRole("button", { name: "Accept" }).click();
    await Promise.all([expectActive(ada.page, "Nocam"), expectActive(nocam.page, "Ada")]);
    const seen = await expectPlayingVideo(nocam.page, "Ada's video");
    await callDialog(ada.page, "Nocam").getByText("Voice only").waitFor({ timeout: 10000 });
    if (await nocam.page.getByLabel("Your camera").count()) throw new Error("camera preview shown without a camera");
    await screenshot(ada.page, "call-voice-only");
    await callDialog(nocam.page, "Ada").getByRole("button", { name: "Hang up" }).click();
    await Promise.all([expectNoCallUi(ada.page), expectNoCallUi(nocam.page)]);
    return `nocam sees ${seen}`;
  });

  await ada.page.waitForTimeout(1000);
  await step("a callee without a camera still receives the caller's video", async () => {
    await startButton(ada.page).click();
    await incomingDialog(nocam.page, "Ada").getByRole("button", { name: "Accept" }).click();
    await Promise.all([expectActive(ada.page, "Nocam"), expectActive(nocam.page, "Ada")]);
    const seen = await expectPlayingVideo(nocam.page, "Ada's video");
    await callDialog(ada.page, "Nocam").getByText("Voice only").waitFor({ timeout: 10000 });
    await callDialog(ada.page, "Nocam").getByRole("button", { name: "Hang up" }).click();
    await Promise.all([expectNoCallUi(ada.page), expectNoCallUi(nocam.page)]);
    return `nocam sees ${seen}`;
  });

  // ---------- 8. Group conversations have no call button ----------
  await step("group conversations don't offer calls", async () => {
    const response = await fetch(`${BASE}/api/v1/conversations/group`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${adaAuth.token}` },
      body: JSON.stringify({ name: `Team ${Date.now()}`, memberIds: [graceAuth.id, linusAuth.id] }),
    });
    if (!response.ok) throw new Error(`create group: ${response.status} ${await response.text()}`);
    const group = await response.json();
    await ada.page.goto(`${BASE}/chat?conversation=${group.id}`);
    await ada.page.getByRole("button", { name: "Members" }).waitFor({ timeout: 10000 });
    if (await startButton(ada.page).count()) throw new Error("call button visible in a group");
  });
} finally {
  await browser.close();
}

finish();
