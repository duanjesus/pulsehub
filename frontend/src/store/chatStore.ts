import { create } from "zustand";

interface ChatState {
  /** conversationId -> set of user ids currently typing in that conversation. */
  typingByConversation: Record<number, Record<number, boolean>>;
  setTyping: (conversationId: number, userId: number, typing: boolean) => void;
}

export const useChatStore = create<ChatState>((set) => ({
  typingByConversation: {},
  setTyping: (conversationId, userId, typing) =>
    set((state) => {
      const current = { ...(state.typingByConversation[conversationId] ?? {}) };
      if (typing) {
        current[userId] = true;
      } else {
        delete current[userId];
      }
      return { typingByConversation: { ...state.typingByConversation, [conversationId]: current } };
    }),
}));
