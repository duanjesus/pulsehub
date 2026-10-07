import { Outlet } from "react-router-dom";

import { CallOverlay } from "@/components/call/CallOverlay";
import { Sidebar } from "@/components/layout/Sidebar";
import { NotificationBell } from "@/components/layout/NotificationBell";

export function AppLayout() {
  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex justify-end border-b border-slate-200 bg-white px-5 py-2">
          <NotificationBell />
        </header>
        <main className="flex-1 overflow-hidden">
          <Outlet />
        </main>
      </div>
      <CallOverlay />
    </div>
  );
}
