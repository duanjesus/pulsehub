import { create } from "zustand";

interface ChatState {
  /** Keyed by the peer's user id — a typing event only ever tells us who is typing to us. */
  typingByUserId: Record<number, boolean>;
  setTyping: (userId: number, typing: boolean) => void;
}

export const useChatStore = create<ChatState>((set) => ({
  typingByUserId: {},
  setTyping: (userId, typing) =>
    set((state) => ({
      typingByUserId: { ...state.typingByUserId, [userId]: typing },
    })),
}));
