import { useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { Bell, Lock, Mail } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/form-controls';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { cn } from '@/lib/utils';

export type PreferenceRow = { category: string; label: string; description?: string; inApp: boolean; email: boolean; inAppLocked: boolean };

/**
 * `/cuenta/avisos`: matriz categoría × canal (en la app, por correo) de la persona (V16). Cualquier rol, también Lector:
 * es su propia cuenta. Se guardan solo las filas cambiadas; «Cuenta y accesos» siempre llega a la app (`inAppLocked`).
 */
export default function NotificationPreferences({ initial }: { initial: PreferenceRow[] }) {
  const [saved, setSaved] = useState<PreferenceRow[]>(initial);
  const [rows, setRows] = useState<PreferenceRow[]>(initial);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const changed = useMemo(() => rows.filter((r) => {
    const s = saved.find((x) => x.category === r.category);
    return !s || s.inApp !== r.inApp || s.email !== r.email;
  }), [rows, saved]);

  const set = (category: string, patch: Partial<PreferenceRow>) => setRows((l) => l.map((r) => (r.category === category ? { ...r, ...patch } : r)));

  async function save() {
    setBusy(true);
    setError(null);
    const { data, error: err } = await actions.notifications.updatePreferences({ preferences: changed.map(({ category, inApp, email }) => ({ category, inApp, email })) });
    setBusy(false);
    if (err || !data) { setError(err ? splitActionError(err).message : 'No pudimos guardar tus avisos.'); return; }
    const next = data as PreferenceRow[];
    setSaved(next);
    setRows(next);
    toast.success('Avisos guardados');
  }

  return (
    <Card className="min-w-0 overflow-hidden">
      <div className="hidden grid-cols-[minmax(0,1fr)_6.5rem_6.5rem] items-center gap-3 border-b border-steel-200 bg-steel-100 px-5 py-2 text-xs font-semibold uppercase tracking-wide text-ink-500 sm:grid" aria-hidden="true">
        <span>Tipo de aviso</span><span className="text-center">En la app</span><span className="text-center">Por correo</span>
      </div>
      <ul className="divide-y divide-steel-200" aria-label="Preferencias de aviso">
        {rows.map((r) => {
          const appId = `pref-${r.category}-app`;
          const mailId = `pref-${r.category}-mail`;
          return (
            <li key={r.category} data-testid={`pref-${r.category}`} className="grid gap-3 px-4 py-3.5 sm:grid-cols-[minmax(0,1fr)_6.5rem_6.5rem] sm:items-center sm:px-5">
              <div className="min-w-0">
                <p className="text-sm font-medium text-ink-900" id={`pref-${r.category}-label`}>{r.label}</p>
                {r.description && <p className="text-xs text-ink-500">{r.description}</p>}
              </div>
              <div className="flex items-center gap-2 sm:justify-center">
                <Switch id={appId} checked={r.inApp} disabled={r.inAppLocked} onCheckedChange={(v) => set(r.category, { inApp: v })} aria-label={`${r.label}: en la app`} />
                <label htmlFor={appId} className={cn('flex items-center gap-1 text-xs text-ink-700 sm:sr-only')}><Bell className="size-3.5" aria-hidden="true" /> En la app</label>
                {r.inAppLocked && <span className="inline-flex items-center gap-1 text-[11px] text-ink-500" title="Siempre llega a la app"><Lock className="size-3" aria-hidden="true" /><span className="sm:sr-only">Siempre</span></span>}
              </div>
              <div className="flex items-center gap-2 sm:justify-center">
                <Switch id={mailId} checked={r.email} onCheckedChange={(v) => set(r.category, { email: v })} aria-label={`${r.label}: por correo`} />
                <label htmlFor={mailId} className="flex items-center gap-1 text-xs text-ink-700 sm:sr-only"><Mail className="size-3.5" aria-hidden="true" /> Por correo</label>
              </div>
            </li>
          );
        })}
      </ul>
      <div className="flex flex-col gap-3 border-t border-steel-200 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
        <p className="text-xs text-ink-500">{changed.length === 0 ? 'Sin cambios por guardar.' : changed.length === 1 ? '1 cambio sin guardar.' : `${changed.length} cambios sin guardar.`} Los correos salen a tu dirección de acceso.</p>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={() => setRows(saved)} disabled={changed.length === 0 || busy}>Descartar</Button>
          <Button onClick={save} loading={busy} disabled={changed.length === 0}>Guardar avisos</Button>
        </div>
      </div>
      {error && <div className="px-4 pb-4 sm:px-5"><Alert tone="error">{error}</Alert></div>}
    </Card>
  );
}
