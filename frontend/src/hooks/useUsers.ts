import { useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type { UserSummary } from "@/types/user";

export function useContacts() {
  return useQuery({
    queryKey: ["users", "contacts"],
    queryFn: async () => {
      const { data } = await api.get<UserSummary[]>("/users");
      return data;
    },
  });
}
