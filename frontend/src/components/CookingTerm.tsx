import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import './CookingTerm.css';
const OPEN = 'mealplan-cooking-term-open';
export function CookingTerm({ label, explanation }: { label: string; explanation: string }) {
 const id = useId(); const button = useRef<HTMLButtonElement>(null); const bubble = useRef<HTMLSpanElement>(null); const suppressed = useRef(false); const closing = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
 const [open, setOpen] = useState(false); const [pinned, setPinned] = useState(false); const [position, setPosition] = useState({ top: 0, left: 0 });
 function dismiss(suppress = true) { clearTimeout(closing.current); suppressed.current = suppress; setOpen(false); setPinned(false); }
 function show() { clearTimeout(closing.current); if (suppressed.current) return; window.dispatchEvent(new CustomEvent(OPEN, { detail: id })); setOpen(true); }
 useEffect(() => { const other = (event: Event) => { if ((event as CustomEvent<string>).detail !== id) { if (button.current?.getAttribute('aria-expanded') === 'true') suppressed.current = true; setOpen(false); setPinned(false); } }; window.addEventListener(OPEN, other); return () => { clearTimeout(closing.current); window.removeEventListener(OPEN, other); }; }, [id]);
 useEffect(() => {
  if (!open) return;
  const outside = (event: PointerEvent) => { if (!button.current?.contains(event.target as Node) && !bubble.current?.contains(event.target as Node)) dismiss(); };
  const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') dismiss(); };
  document.addEventListener('pointerdown', outside); document.addEventListener('keydown', escape); return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); };
 }, [open]);
 useLayoutEffect(() => {
  if (!open) return;
  const place = () => { if (!button.current || !bubble.current) return; const rect = button.current.getBoundingClientRect(), box = bubble.current.getBoundingClientRect(); const above = rect.top - box.height - 8; const top = above >= 8 ? above : rect.bottom + 8; setPosition({ top: Math.max(8, Math.min(top, window.innerHeight - box.height - 8)), left: Math.max(8, Math.min(rect.left + rect.width / 2 - box.width / 2, window.innerWidth - box.width - 8)) }); };
  place(); window.addEventListener('resize', place); window.addEventListener('scroll', place, true); return () => { window.removeEventListener('resize', place); window.removeEventListener('scroll', place, true); };
 }, [open, explanation]);
 function leave(related: EventTarget | null) { suppressed.current = false; if (!pinned && document.activeElement !== button.current && !(related instanceof Node && bubble.current?.contains(related))) closing.current = setTimeout(() => setOpen(false),150); }
 return <><button ref={button} type="button" className="cooking-term" aria-expanded={open} aria-describedby={open ? id : undefined} onFocus={show} onBlur={() => { suppressed.current = false; if (!pinned) setOpen(false); }} onPointerEnter={show} onPointerLeave={event => leave(event.relatedTarget)} onClick={() => { if (pinned) dismiss(); else { suppressed.current = false; show(); setPinned(true); } }}>{label}</button>
 {open && createPortal(<span ref={bubble} id={id} role="tooltip" className="cooking-explanation" style={position} onPointerEnter={() => clearTimeout(closing.current)} onPointerLeave={event => { if (!pinned && document.activeElement !== button.current && event.relatedTarget !== button.current) closing.current = setTimeout(() => setOpen(false),150); }}>{explanation}</span>, document.querySelector('main') || document.body)}</>;
}
