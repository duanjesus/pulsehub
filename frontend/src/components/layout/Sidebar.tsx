import { NavLink } from "react-router-dom";

import { useAuth } from "@/context/AuthContext";
import { Avatar } from "@/components/ui/Avatar";
import { useProfile } from "@/hooks/useProfile";

const NAV_ITEMS = [
  { to: "/", label: "Dashboard", end: true },
  { to: "/chat", label: "Messages", end: false },
  { to: "/profile", label: "Profile", end: false },
];

export function Sidebar() {
  const { user, logout } = useAuth();
  const { data: profile } = useProfile();

  return (
    <aside className="flex w-60 shrink-0 flex-col border-r border-slate-200 bg-white">
      <div className="flex items-center gap-2 px-5 py-5">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white">
          PH
        </span>
        <span className="text-base font-semibold text-slate-900">PulseHub</span>
      </div>

      <nav className="flex-1 space-y-1 px-3">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `block rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                isActive ? "bg-brand-50 text-brand-700" : "text-slate-600 hover:bg-slate-100"
              }`
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>

      {user && (
        <div className="flex items-center gap-3 border-t border-slate-200 px-4 py-4">
          <Avatar name={user.name} avatarUrl={profile?.avatarUrl} status="ONLINE" size="sm" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-slate-900">{user.name}</p>
            <p className="truncate text-xs text-slate-500">{user.email}</p>
          </div>
          <button
            onClick={logout}
            className="text-xs font-medium text-slate-500 hover:text-slate-700"
            title="Log out"
          >
            Log out
          </button>
        </div>
      )}
    </aside>
  );
}
