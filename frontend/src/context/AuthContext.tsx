import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";

import { api, TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from "@/lib/api";
import type { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from "@/types/auth";

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (payload: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<void>;
  logout: () => void;
  updateUser: (patch: Partial<AuthUser>) => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function readStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

function persistSession(auth: AuthResponse) {
  localStorage.setItem(TOKEN_STORAGE_KEY, auth.token);
  const user: AuthUser = { id: auth.id, name: auth.name, email: auth.email };
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
  return user;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => readStoredUser());
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_STORAGE_KEY));

  const login = useCallback(async (payload: LoginRequest) => {
    const { data } = await api.post<AuthResponse>("/auth/login", payload);
    setUser(persistSession(data));
    setToken(data.token);
  }, []);

  const register = useCallback(async (payload: RegisterRequest) => {
    const { data } = await api.post<AuthResponse>("/auth/register", payload);
    setUser(persistSession(data));
    setToken(data.token);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
    setUser(null);
    setToken(null);
  }, []);

  const updateUser = useCallback((patch: Partial<AuthUser>) => {
    setUser((current) => {
      if (!current) return current;
      const updated = { ...current, ...patch };
      localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(updated));
      return updated;
    });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      isAuthenticated: user !== null && token !== null,
      login,
      register,
      logout,
      updateUser,
    }),
    [user, token, login, register, logout, updateUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
