import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { api, onUnauthorized, tokenStore } from "@/api/client";
import type { MeResponse } from "@/api/types";

interface AuthState {
  user: MeResponse | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(!!tokenStore.get());

  // Restauration de session : le jeton est en localStorage, l'identité vient de /auth/me
  useEffect(() => {
    if (!tokenStore.get()) return;
    api.GET("/api/v1/auth/me").then(({ data }) => {
      setUser(data ?? null);
      setLoading(false);
    });
  }, []);

  useEffect(() => onUnauthorized(() => setUser(null)), []);

  const login = useCallback(async (username: string, password: string) => {
    const { data, error } = await api.POST("/api/v1/auth/login", { body: { username, password } });
    if (error || !data?.accessToken) throw error ?? new Error("Connexion impossible");
    tokenStore.set(data.accessToken);
    const me = await api.GET("/api/v1/auth/me");
    setUser(me.data ?? null);
  }, []);

  const logout = useCallback(() => {
    tokenStore.clear();
    setUser(null);
  }, []);

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth doit être utilisé sous AuthProvider");
  return ctx;
}

/** Garde de route : redirige vers /login en conservant la destination. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) {
    return <div className="flex h-screen items-center justify-center text-slate-500">Chargement de la session…</div>;
  }
  if (!user) return <Navigate to="/login" state={{ from: location }} replace />;
  return <>{children}</>;
}
