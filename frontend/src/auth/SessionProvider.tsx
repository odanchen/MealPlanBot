import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api, type Session } from "../api/client";
type SessionState = {
  session: Session | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  signInLocal: () => Promise<void>;
  logout: () => Promise<void>;
};
const SessionContext = createContext<SessionState | null>(null);
export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const refresh = useCallback(async () => {
    setSession(await api.session());
    setError(null);
  }, []);
  const signInLocal = useCallback(async () => {
    await api.localSignIn();
    await refresh();
  }, [refresh]);
  const logout = useCallback(async () => {
    await api.logout();
    await refresh();
  }, [refresh]);
  useEffect(() => {
    let active = true;
    // Defer bootstrap so StrictMode's effect probe does not issue duplicate sign-ins.
    void Promise.resolve().then(async () => {
      if (!active) return;
      try {
        let current = await api.session();
        if (!active) return;
        if (!current.authenticated && current.authMode === "local") {
          await api.localSignIn();
          current = await api.session();
        }
        if (active) {
          setSession(current);
          setError(null);
        }
      } catch {
        if (active) setError("Cannot connect. Check your connection and try again.");
      } finally {
        if (active) setLoading(false);
      }
    });
    return () => {
      active = false;
    };
  }, []);
  return (
    <SessionContext.Provider value={{ session, loading, error, refresh, signInLocal, logout }}>
      {children}
    </SessionContext.Provider>
  );
}
export function useSession(): SessionState {
  const context = useContext(SessionContext);
  if (!context) throw new Error("SessionProvider is required");
  return context;
}
