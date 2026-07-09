import { useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type { DashboardSummary } from "@/types/chat";

export function useDashboard() {
  return useQuery({
    queryKey: ["dashboard"],
    queryFn: async () => {
      const { data } = await api.get<DashboardSummary>("/dashboard");
      return data;
    },
    refetchInterval: 15_000,
  });
}
