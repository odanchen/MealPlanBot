import { ConfirmDialog } from './ConfirmDialog';
export function DeleteUserScheduleDialog({ linkedLists, busy, error, onConfirm, onCancel }: { linkedLists: { id: string; name: string }[]; busy: boolean; error: string; onConfirm: () => void; onCancel: () => void }) {
 return <ConfirmDialog title="Delete personal schedule?" description={linkedLists.length ? `Automatic additions will stop for: ${linkedLists.map(list => list.name).join(', ')}. Their purchases and source history will remain.` : 'This deletes your personal configuration. It does not delete any recipes or purchases.'} confirmLabel="Delete schedule" busy={busy} error={error} onConfirm={onConfirm} onCancel={onCancel} />;
}
