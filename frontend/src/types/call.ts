export type CallSignalType =
  | "OFFER"
  | "ANSWER"
  | "ICE_CANDIDATE"
  | "REJECT"
  | "HANGUP"
  | "BUSY"
  | "KEEPALIVE"
  | "UNAVAILABLE";

/** What the server pushes to /user/queue/calls. */
export interface CallSignal {
  conversationId: number;
  callId: string;
  type: CallSignalType;
  /** JSON-serialized SDP description (OFFER/ANSWER) or ICE candidate (ICE_CANDIDATE). */
  payload?: string;
  senderId: number;
  senderName: string;
  senderAvatarUrl?: string;
}

/** What the client publishes to /app/call.signal. */
export interface OutgoingCallSignal {
  conversationId: number;
  callId: string;
  type: Exclude<CallSignalType, "UNAVAILABLE">;
  payload?: string;
}
