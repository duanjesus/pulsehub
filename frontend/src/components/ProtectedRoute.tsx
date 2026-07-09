import { Navigate, Outlet } from "react-router-dom";

import { useAuth } from "@/context/AuthContext";
import { useChatSocket } from "@/hooks/useChatSocket";

export function ProtectedRoute() {
  const { isAuthenticated } = useAuth();
  useChatSocket();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
