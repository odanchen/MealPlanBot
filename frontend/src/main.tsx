import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { SessionProvider } from './auth/SessionProvider';
import { AppShell } from './components/AppShell';
const protectedPage = /^\/(schedules|lists)(\/|$)/.test(window.location.pathname);
createRoot(document.getElementById('root')!).render(<StrictMode><SessionProvider><AppShell protectedPage={protectedPage}><h2>{protectedPage ? 'Your planner' : 'Recipes'}</h2><p>The planner foundation is ready. Recipe and schedule features are coming next.</p></AppShell></SessionProvider></StrictMode>);
