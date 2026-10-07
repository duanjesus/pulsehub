import { api } from "@/lib/api";
import { sendCallSignal } from "@/lib/ws";
import { IDLE_CALL, useCallStore, type CallPeer } from "@/store/callStore";
import type { CallSignal, OutgoingCallSignal } from "@/types/call";

const OUTGOING_RING_TIMEOUT_MS = 30_000;
/** Longer than the caller's timeout, so it only fires when the caller vanished without hanging up. */
const INCOMING_RING_TIMEOUT_MS = 45_000;
const KEEPALIVE_INTERVAL_MS = 60_000;
const MAX_BUFFERED_CANDIDATES = 50;

const { getState, setState } = useCallStore;

let connection: RTCPeerConnection | null = null;
let pendingOffer: string | null = null;
/**
 * ICE candidates that can't be applied yet. The server doesn't guarantee frame
 * order, so candidates may arrive before the OFFER/ANSWER they belong to — and
 * on the callee they always arrive before the user has accepted.
 */
let bufferedCandidates: { callId: string; candidates: RTCIceCandidateInit[] } | null = null;
let ringTimer: ReturnType<typeof setTimeout> | undefined;
let keepaliveTimer: ReturnType<typeof setInterval> | undefined;
let iceServersRequest: Promise<RTCIceServer[]> | null = null;

function fetchIceServers(): Promise<RTCIceServer[]> {
  iceServersRequest ??= api
    .get<RTCIceServer[]>("/calls/ice-servers")
    .then((response) => response.data)
    .catch((error: unknown) => {
      iceServersRequest = null;
      throw error;
    });
  return iceServersRequest;
}

function newCallId(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function isCurrent(callId: string): boolean {
  return getState().callId === callId;
}

function send(type: OutgoingCallSignal["type"], payload?: string): void {
  const { callId, conversationId } = getState();
  if (callId && conversationId !== null) {
    sendCallSignal({ conversationId, callId, type, payload });
  }
}

function stopStream(stream: MediaStream | null): void {
  stream?.getTracks().forEach((track) => track.stop());
}

function endCall(notice: string | null = null): void {
  clearTimeout(ringTimer);
  clearInterval(keepaliveTimer);

  if (connection) {
    connection.onicecandidate = null;
    connection.ontrack = null;
    connection.onconnectionstatechange = null;
    connection.close();
    connection = null;
  }
  stopStream(getState().localStream);
  pendingOffer = null;
  bufferedCandidates = null;

  setState({ ...IDLE_CALL, notice });
}

async function openLocalMedia(): Promise<MediaStream> {
  if (!navigator.mediaDevices?.getUserMedia) {
    throw new Error("insecure-context");
  }
  try {
    return await navigator.mediaDevices.getUserMedia({ audio: true, video: true });
  } catch (error) {
    // No camera, or another app is holding it: fall back to a voice-only call.
    if (error instanceof DOMException && ["NotFoundError", "NotReadableError", "OverconstrainedError"].includes(error.name)) {
      return navigator.mediaDevices.getUserMedia({ audio: true });
    }
    throw error;
  }
}

function describeFailure(error: unknown): string {
  if (error instanceof DOMException && (error.name === "NotAllowedError" || error.name === "SecurityError")) {
    return "Camera and microphone access was denied.";
  }
  if (error instanceof DOMException && (error.name === "NotFoundError" || error.name === "NotReadableError")) {
    return "No microphone is available for a call.";
  }
  if (error instanceof Error && error.message === "insecure-context") {
    return "Calls only work over HTTPS or on localhost.";
  }
  return "The call could not be started.";
}

async function createConnection(callId: string): Promise<RTCPeerConnection | null> {
  const iceServers = await fetchIceServers();
  if (!isCurrent(callId)) return null;

  const pc = new RTCPeerConnection({ iceServers });

  pc.onicecandidate = (event) => {
    if (event.candidate) send("ICE_CANDIDATE", JSON.stringify(event.candidate.toJSON()));
  };
  pc.ontrack = (event) => {
    // A new MediaStream per track, so subscribers re-render when video joins audio.
    const tracks = getState().remoteStream?.getTracks() ?? [];
    setState({ remoteStream: new MediaStream([...tracks, event.track]) });
  };
  pc.onconnectionstatechange = () => {
    if (pc.connectionState === "connected" && getState().phase !== "active") {
      setState({ phase: "active", activeSince: Date.now() });
      keepaliveTimer = setInterval(() => send("KEEPALIVE"), KEEPALIVE_INTERVAL_MS);
    } else if (pc.connectionState === "failed") {
      send("HANGUP");
      endCall("The connection was lost.");
    }
  };

  connection = pc;
  return pc;
}

function bufferCandidate(callId: string, candidate: RTCIceCandidateInit): void {
  if (bufferedCandidates?.callId !== callId) {
    bufferedCandidates = { callId, candidates: [] };
  }
  if (bufferedCandidates.candidates.length < MAX_BUFFERED_CANDIDATES) {
    bufferedCandidates.candidates.push(candidate);
  }
}

async function addCandidate(candidate: RTCIceCandidateInit): Promise<void> {
  try {
    await connection?.addIceCandidate(candidate);
  } catch {
    // One unusable candidate must not end the call; ICE carries on with the rest.
  }
}

async function flushCandidates(callId: string): Promise<void> {
  const candidates = bufferedCandidates?.callId === callId ? bufferedCandidates.candidates : [];
  bufferedCandidates = null;
  for (const candidate of candidates) {
    await addCandidate(candidate);
  }
}

function describe(description: RTCSessionDescriptionInit): string {
  return JSON.stringify({ type: description.type, sdp: description.sdp });
}

export async function startCall(conversationId: number, peer: CallPeer): Promise<void> {
  if (getState().phase !== "idle") return;

  const callId = newCallId();
  setState({ ...IDLE_CALL, phase: "outgoing", callId, conversationId, peer });

  try {
    const stream = await openLocalMedia();
    if (!isCurrent(callId)) {
      stopStream(stream);
      return;
    }
    setState({ localStream: stream });

    const pc = await createConnection(callId);
    if (!pc) return;

    stream.getTracks().forEach((track) => pc.addTrack(track, stream));
    // Always offer a video line, so a caller without a camera can still see the other side.
    if (stream.getVideoTracks().length === 0) {
      pc.addTransceiver("video", { direction: "recvonly" });
    }

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    if (!isCurrent(callId)) return;

    send("OFFER", describe(offer));
    ringTimer = setTimeout(() => {
      send("HANGUP");
      endCall(`${peer.name} didn't answer.`);
    }, OUTGOING_RING_TIMEOUT_MS);
  } catch (error) {
    if (isCurrent(callId)) endCall(describeFailure(error));
  }
}

export async function acceptCall(): Promise<void> {
  const { phase, callId } = getState();
  const offer = pendingOffer;
  if (phase !== "incoming" || !callId || !offer) return;

  clearTimeout(ringTimer);
  setState({ phase: "connecting" });

  try {
    const stream = await openLocalMedia();
    if (!isCurrent(callId)) {
      stopStream(stream);
      return;
    }
    setState({ localStream: stream });

    const pc = await createConnection(callId);
    if (!pc) return;

    // Tracks go on after the remote offer, so they reuse its media lines instead of adding new ones.
    await pc.setRemoteDescription(JSON.parse(offer) as RTCSessionDescriptionInit);
    stream.getTracks().forEach((track) => pc.addTrack(track, stream));

    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    if (!isCurrent(callId)) return;

    send("ANSWER", describe(answer));
    await flushCandidates(callId);
  } catch (error) {
    if (isCurrent(callId)) {
      send("HANGUP");
      endCall(describeFailure(error));
    }
  }
}

export function declineCall(): void {
  if (getState().phase !== "incoming") return;
  send("REJECT");
  endCall();
}

export function hangUp(): void {
  const { phase } = getState();
  if (phase === "idle") return;
  send(phase === "incoming" ? "REJECT" : "HANGUP");
  endCall();
}

export function toggleMic(): void {
  const { localStream, micEnabled } = getState();
  localStream?.getAudioTracks().forEach((track) => {
    track.enabled = !micEnabled;
  });
  setState({ micEnabled: !micEnabled });
}

export function toggleCamera(): void {
  const { localStream, cameraEnabled } = getState();
  localStream?.getVideoTracks().forEach((track) => {
    track.enabled = !cameraEnabled;
  });
  setState({ cameraEnabled: !cameraEnabled });
}

export function dismissCallNotice(): void {
  setState({ notice: null });
}

async function processSignal(signal: CallSignal, currentUserId: number): Promise<void> {
  const state = getState();
  const isCurrentCall = state.callId === signal.callId;

  if (signal.senderId === currentUserId) {
    // Our own ANSWER/REJECT echoed back: another tab of this account took the call.
    if (isCurrentCall && state.phase === "incoming") endCall();
    return;
  }

  if (signal.type === "OFFER") {
    if (!signal.payload) return;
    if (state.phase !== "idle") {
      if (!isCurrentCall) {
        sendCallSignal({ conversationId: signal.conversationId, callId: signal.callId, type: "BUSY" });
      }
      return;
    }
    pendingOffer = signal.payload;
    setState({
      ...IDLE_CALL,
      phase: "incoming",
      callId: signal.callId,
      conversationId: signal.conversationId,
      peer: { id: signal.senderId, name: signal.senderName, avatarUrl: signal.senderAvatarUrl ?? null },
    });
    ringTimer = setTimeout(() => endCall(`Missed call from ${signal.senderName}.`), INCOMING_RING_TIMEOUT_MS);
    return;
  }

  if (signal.type === "ICE_CANDIDATE") {
    if (!signal.payload) return;
    const candidate = JSON.parse(signal.payload) as RTCIceCandidateInit;
    if (isCurrentCall && connection?.remoteDescription) {
      await addCandidate(candidate);
    } else if (isCurrentCall || state.phase === "idle") {
      bufferCandidate(signal.callId, candidate);
    }
    return;
  }

  if (!isCurrentCall) return;
  const peerName = state.peer?.name ?? "The other person";

  switch (signal.type) {
    case "ANSWER":
      if (state.phase !== "outgoing" || !connection || !signal.payload) return;
      clearTimeout(ringTimer);
      setState({ phase: "connecting" });
      await connection.setRemoteDescription(JSON.parse(signal.payload) as RTCSessionDescriptionInit);
      await flushCandidates(signal.callId);
      return;
    case "REJECT":
      endCall(`${peerName} declined the call.`);
      return;
    case "BUSY":
      // The HANGUP stops any other tab of theirs that is still ringing.
      send("HANGUP");
      endCall(`${peerName} is on another call.`);
      return;
    case "UNAVAILABLE":
      endCall(`${peerName} is offline.`);
      return;
    case "HANGUP":
      endCall(state.phase === "incoming" ? `Missed call from ${peerName}.` : "Call ended.");
      return;
  }
}

export function handleCallSignal(signal: CallSignal, currentUserId: number): void {
  processSignal(signal, currentUserId).catch(() => {
    if (isCurrent(signal.callId)) {
      send("HANGUP");
      endCall("The call could not be connected.");
    }
  });
}
