import { create } from "zustand";

export type CallPhase = "idle" | "outgoing" | "incoming" | "connecting" | "active";

export interface CallPeer {
  id: number;
  name: string;
  avatarUrl: string | null;
}

export interface CallState {
  phase: CallPhase;
  callId: string | null;
  conversationId: number | null;
  peer: CallPeer | null;
  localStream: MediaStream | null;
  remoteStream: MediaStream | null;
  micEnabled: boolean;
  cameraEnabled: boolean;
  /** Epoch ms at which media first connected; drives the call timer. */
  activeSince: number | null;
  /** One-line outcome of the last call ("Ada declined the call."), shown briefly once idle. */
  notice: string | null;
}

export const IDLE_CALL: CallState = {
  phase: "idle",
  callId: null,
  conversationId: null,
  peer: null,
  localStream: null,
  remoteStream: null,
  micEnabled: true,
  cameraEnabled: true,
  activeSince: null,
  notice: null,
};

/** Written only by lib/call.ts, which owns the RTCPeerConnection; components just read it. */
export const useCallStore = create<CallState>(() => IDLE_CALL);
