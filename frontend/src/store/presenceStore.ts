import { create } from "zustand";

import type { UserStatus } from "@/types/user";

interface PresenceState {
  statusByUserId: Record<number, UserStatus>;
  setStatus: (userId: number, status: UserStatus) => void;
  hydrate: (users: Array<{ id: number; status: UserStatus }>) => void;
}

export const usePresenceStore = create<PresenceState>((set) => ({
  statusByUserId: {},
  setStatus: (userId, status) =>
    set((state) => ({ statusByUserId: { ...state.statusByUserId, [userId]: status } })),
  hydrate: (users) =>
    set(() => ({
      statusByUserId: Object.fromEntries(users.map((u) => [u.id, u.status])),
    })),
}));
