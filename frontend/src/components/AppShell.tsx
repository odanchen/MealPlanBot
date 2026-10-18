import { useState, type ReactNode } from "react";
import { useSession } from "../auth/SessionProvider";
import { useViewerTimeZone } from "../hooks/useViewerTimeZone";
export function AppShell({
  children,
  protectedPage = false,
}: {
  children: ReactNode;
  protectedPage?: boolean;
}) {
  const { session, loading, error, signInLocal, logout } = useSession();
  const viewer = useViewerTimeZone();
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function action(work: () => Promise<void>) {
    setBusy(true);
    setActionError(null);
    try {
      await work();
    } catch {
      setActionError("Unable to complete the action. Please try again.");
    } finally {
      setBusy(false);
    }
  }
  const login =
    session?.authMode === "local" ? (
      <button
        disabled={busy}
        onClick={() => {
          void action(signInLocal);
        }}
      >
        Sign in locally
      </button>
    ) : (
      <a
        href={`/auth/login?returnTo=${encodeURIComponent(window.location.pathname + window.location.search)}`}
      >
        Sign in with Google
      </a>
    );
  return (
    <>
      <a href="#main">Skip to content</a>
      <header>
        <h1>Meal planner</h1>
        <nav aria-label="Main">
          <a href="/">Recipes</a> <a href="/schedules">Schedules</a>{" "}
          <a href="/lists">Shopping lists</a>
        </nav>
        {session?.authenticated ? (
          <p>
            {session.displayName || session.email || "Signed in"}{" "}
            <button
              disabled={busy}
              onClick={() => {
                void action(logout);
              }}
            >
              Sign out
            </button>
          </p>
        ) : (
          !loading && login
        )}
      </header>
      <main id="main">
        {(error || actionError) && <p role="alert">{error || actionError}</p>}
        {protectedPage ? (
          loading ? (
            <p role="status">Checking sign-in…</p>
          ) : session?.authenticated ? (
            children
          ) : (
            <>
              <h2>Sign in to continue</h2>
              <p>Your schedules and shopping lists are private.</p>
              {login}
            </>
          )
        ) : (
          children
        )}
      </main>
      <footer>
        <p>
          Dates shown in {viewer.label}. Today: <time dateTime={viewer.today}>{viewer.today}</time>.
        </p>
      </footer>
    </>
  );
}
