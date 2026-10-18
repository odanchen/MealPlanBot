import { useId, type ReactNode } from 'react';
export function FormField({ label, children, hint }: { label: string; children: (id: string, hintId: string | undefined) => ReactNode; hint?: string }) {
 const id = useId();
 return <div className="form-field"><label htmlFor={id}>{label}</label>{children(id, hint ? `${id}-hint` : undefined)}{hint && <p id={`${id}-hint`} className="field-hint">{hint}</p>}</div>;
}
