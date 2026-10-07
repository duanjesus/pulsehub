import fs from "node:fs";
import http from "node:http";

import {
  BASE,
  callDialog,
  expectActive,
  expectNoCallUi,
  expectPlayingVideo,
  finish,
  incomingDialog,
  launchBrowser,
  openChatWith,
  openSession,
  register,
  socketCount,
  socketInstance,
  startButton,
  step,
} from "./lib.mjs";

// An instance id is its container id, so with the Docker socket mounted this script can crash a replica itself.
const DOCKER_SOCKET = "/var/run/docker.sock";
const canControlDocker = fs.existsSync(DOCKER_SOCKET);

function docker(method, path) {
  return new Promise((resolve, reject) => {
    const request = http.request({ socketPath: DOCKER_SOCKET, path, method }, (response) => {
      response.resume();
      response.on("end", () => resolve(response.statusCode));
    });
    request.on("error", reject);
    request.end();
  });
}

/** Which instances answer a burst of REST calls through the load balancer. */
async function instancesServingRest(token, requests = 12) {
  const seen = new Map();
  for (let i = 0; i < requests; i++) {
    const response = await fetch(`${BASE}/api/v1/users/me`, { headers: { Authorization: `Bearer ${token}` } });
    const instance = response.headers.get("x-pulsehub-instance") ?? `status ${response.status}`;
    seen.set(instance, (seen.get(instance) ?? 0) + 1);
  }
  return seen;
}

const describe = (seen) => [...seen].map(([instance, count]) => `${instance}×${count}`).join(", ");

/** (Re)opens a page until its WebSocket lands on an instance other than `avoid`. */
async function connectAwayFrom(session, avoid, open) {
  for (let attempt = 1; attempt <= 12; attempt++) {
    const before = socketCount(session.page);
    await open();
    const instance = await socketInstance(session.page, { minSockets: before + 1 });
    if (instance !== avoid) return instance;
  }
  throw new Error(`${session.label} still lands on ${avoid} after 12 connections`);
}

const chatHeader = (page, name) => page.locator("header").filter({ hasText: name });

async function sendText(page, peerName, text) {
  await page.getByPlaceholder(`Message ${peerName}`).fill(text);
  await page.getByRole("button", { name: "Send" }).click();
}

const browser = await launchBrowser();
let failoverRan = false;

try {
  const [adaAuth, graceAuth, linusAuth] = await Promise.all(["Ada", "Grace", "Linus"].map(register));

  await step("REST requests are balanced across at least two API instances", async () => {
    const seen = await instancesServingRest(adaAuth.token);
    if (seen.size < 2) throw new Error(`only saw ${describe(seen)}`);
    return describe(seen);
  });

  // ---------- Presence ----------
  const ada = await openSession(browser, adaAuth, "ada");
  await openChatWith(ada, linusAuth);
  let adaInstance = await socketInstance(ada.page);

  await step("presence changes on one instance reach a browser connected to another", async () => {
    await chatHeader(ada.page, "Linus").getByText("Offline", { exact: true }).waitFor({ timeout: 10000 });

    const linus = await openSession(browser, linusAuth, "linus");
    const linusInstance = await connectAwayFrom(linus, adaInstance, () => linus.page.goto(`${BASE}/`));
    await chatHeader(ada.page, "Linus").getByText("Online", { exact: true }).waitFor({ timeout: 10000 });

    await linus.context.close();
    await chatHeader(ada.page, "Linus").getByText("Offline", { exact: true }).waitFor({ timeout: 10000 });
    return `ada on ${adaInstance}, linus on ${linusInstance}`;
  });

  // ---------- Chat ----------
  const grace = await openSession(browser, graceAuth, "grace");
  let graceInstance;

  await step("Ada and Grace hold their WebSockets on different API instances", async () => {
    const before = socketCount(ada.page);
    await openChatWith(ada, graceAuth);
    adaInstance = await socketInstance(ada.page, { minSockets: before + 1 });
    graceInstance = await connectAwayFrom(grace, adaInstance, () => openChatWith(grace, adaAuth));
    return `ada on ${adaInstance}, grace on ${graceInstance}`;
  });

  const hello = `hello across replicas ${Date.now()}`;
  await step("a message sent through one instance arrives live on the other", async () => {
    await sendText(ada.page, "Grace", hello);
    await grace.page.getByText(hello).first().waitFor({ timeout: 10000 });
  });

  await step("the read receipt comes back across instances", async () => {
    await ada.page.getByTitle("Read", { exact: true }).first().waitFor({ timeout: 10000 });
  });

  await step("the notification is pushed across instances", async () => {
    await grace.page.getByRole("button", { name: "Notifications" }).getByText(/^\d+\+?$/).waitFor({ timeout: 10000 });
  });

  await step("the typing indicator crosses instances", async () => {
    await grace.page.getByPlaceholder("Message Ada").pressSequentially("thinking", { delay: 60 });
    await chatHeader(ada.page, "Grace").getByText(/Grace is typing/).waitFor({ timeout: 10000 });
    await grace.page.getByPlaceholder("Message Ada").fill("");
  });

  // ---------- Video call ----------
  await step("a video call connects with caller and callee on different instances", async () => {
    await startButton(ada.page).click();
    await incomingDialog(grace.page, "Ada").getByRole("button", { name: "Accept" }).click();
    await Promise.all([expectActive(ada.page, "Grace"), expectActive(grace.page, "Ada")]);
    const adaSees = await expectPlayingVideo(ada.page, "Grace's video");
    const graceSees = await expectPlayingVideo(grace.page, "Ada's video");
    return `ada sees ${adaSees}, grace sees ${graceSees}`;
  });

  // ---------- Failover ----------
  if (!canControlDocker) {
    console.log("SKIP  failover checks (mount /var/run/docker.sock to run them)");
    await callDialog(ada.page, "Grace").getByRole("button", { name: "Hang up" }).click();
  } else {
    failoverRan = true;
    const killed = adaInstance;
    const socketsBefore = socketCount(ada.page);
    const remoteVideo = await ada.page.getByLabel("Grace's video").elementHandle();
    const playedBefore = await ada.page.evaluate((el) => el.currentTime, remoteVideo);

    await step("crashing the instance that holds Ada's socket (docker kill)", async () => {
      const status = await docker("POST", `/containers/${killed}/kill`);
      if (status !== 204) throw new Error(`docker kill returned ${status}`);
      return `killed ${killed}`;
    });

    await step("the call's video keeps playing through the crash, because media is peer to peer", async () => {
      await ada.page.waitForTimeout(6000);
      const played = await ada.page.evaluate((el) => (el.paused ? -1 : el.currentTime), remoteVideo);
      if (played - playedBefore < 4) throw new Error(`video advanced only ${(played - playedBefore).toFixed(1)}s in 6s`);
      return `advanced ${(played - playedBefore).toFixed(1)}s in 6s`;
    });

    await step("Ada's socket reconnects through the load balancer to a surviving instance", async () => {
      const instance = await socketInstance(ada.page, { minSockets: socketsBefore + 1, timeout: 90000 });
      if (instance === killed) throw new Error(`reconnected to the killed instance ${killed}`);
      adaInstance = instance;
      return `ada now on ${instance}`;
    });

    await step("the call is hung up through the new instance", async () => {
      await callDialog(ada.page, "Grace").getByRole("button", { name: "Hang up" }).click();
      await grace.page.getByRole("status").getByText("Call ended.").waitFor({ timeout: 10000 });
      await Promise.all([expectNoCallUi(ada.page), expectNoCallUi(grace.page)]);
    });

    const afterCrash = `still here after the crash ${Date.now()}`;
    await step("messages flow again in both directions after the failover", async () => {
      await sendText(grace.page, "Ada", afterCrash);
      await ada.page.getByText(afterCrash).first().waitFor({ timeout: 10000 });
      await sendText(ada.page, "Grace", `${afterCrash} (reply)`);
      await grace.page.getByText(`${afterCrash} (reply)`).first().waitFor({ timeout: 10000 });
    });

    await step("the restarted instance is picked up by the load balancer again", async () => {
      const status = await docker("POST", `/containers/${killed}/start`);
      if (status !== 204 && status !== 304) throw new Error(`docker start returned ${status}`);
      const deadline = Date.now() + 150000;
      let seen = new Map();
      while (Date.now() < deadline) {
        seen = await instancesServingRest(adaAuth.token);
        if (seen.has(killed)) return describe(seen);
        await new Promise((resolve) => setTimeout(resolve, 3000));
      }
      throw new Error(`${killed} never served a request again; saw ${describe(seen)}`);
    });
  }
} finally {
  await browser.close();
}

// Losing a socket to a crashed instance is the point of the failover checks, not a defect.
finish({ ignoreErrors: (error) => failoverRan && /WebSocket|\/ws\/|50[234]|ERR_|Failed to load resource/i.test(error) });
