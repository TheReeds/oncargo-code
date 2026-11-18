import { useState } from 'react';
import { actions } from 'astro:actions';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';
import { useLiveRefresh } from '@/lib/realtime';
import { BANNER, type VerificationOrg, type VerificationStatus } from '@/features/verification/copy';
import { SUPPORT_EMAIL } from '@/lib/contact';

/**
 * Banner de estado de verificación (RN01) para PYME y transportista: `PENDING` / `REJECTED` / `SUSPENDED`, con la nota del
 * operador y a quién escribir. `VERIFIED` no muestra nada. Se actualiza solo con `notification.created` (tipo
 * `ORGANIZATION_VERIFICATION`). Lo monta `AppShell`, que ya conoce el estado por `/auth/me` (sin otra petición).
 */
export default function VerificationBanner({ status: initial, note: initialNote, orgType }: { status?: VerificationStatus | null; note?: string | null; orgType: VerificationOrg }) {
  const [state, setState] = useState<{ status?: VerificationStatus | null; note?: string | null }>({ status: initial, note: initialNote });
  useLiveRefresh({
    types: ['notification.created'],
    match: (e) => e.data.relatedType === 'ORGANIZATION',
    refresh: async () => {
      const { data } = await actions.auth.verification();
      if (!data) return;
      setState((prev) => {
        if (prev.status !== 'VERIFIED' && data.status === 'VERIFIED') toast.success('Tu empresa fue verificada', 'Ya puedes usar todas las funciones de OnCargo.');
        return data;
      });
    },
    fallbackMs: 120_000,
  });
  const s = state.status;
  if (!s || s === 'VERIFIED') return null;
  const c = BANNER[s];
  return (
    <Alert tone={c.tone} title={c.title} className="mb-5" action={undefined}>
      <p>
        {c.body(orgType)}
        {s !== 'PENDING' && <> Escribe a <a className="font-medium underline" href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a> con tu RUC para revisar tu caso.</>}
      </p>
      {state.note && <p className="mt-1"><span className="font-semibold">{s === 'PENDING' ? 'Nota del equipo' : 'Motivo'}:</span> {state.note}</p>}
    </Alert>
  );
}
