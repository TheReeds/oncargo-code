import { Clock, Phone, StickyNote, UserRound } from 'lucide-react';
import { CITY_LABELS } from '@/lib/format';
import type { StopView } from '@/lib/api/server';

/** `987654321` → `987 654 321` (lectura y dictado por teléfono). */
export const formatPhone = (p: string) => p.replace(/^(\d{3})(\d{3})(\d{3})$/, '$1 $2 $3');

const hasContact = (s: StopView) => !!(s.contactName || s.contactPhone || s.windowFrom || s.instructions);

/**
 * Contacto, ventana horaria e instrucciones de cada parada (RN07 post-adjudicación, roadmap 1.3). La API solo los entrega
 * a las partes, al conductor asignado y al operador; si no vienen, no se muestra nada. El teléfono es un enlace `tel:`.
 */
export function StopContacts({ stops }: { stops: StopView[] }) {
  const list = [...stops].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0)).filter(hasContact);
  if (!list.length) return null;
  return (
    <section aria-labelledby="stop-contacts-title" className="space-y-3">
      <h3 id="stop-contacts-title" className="text-sm font-semibold text-ink-900">Contactos de las paradas</h3>
      <ul className="grid gap-3 sm:grid-cols-2">
        {list.map((s) => (
          <li key={s.id} className="space-y-1.5 rounded-input border border-steel-200 p-3 text-sm">
            <div className="text-xs font-medium text-ink-500">{s.type === 'PICKUP' ? 'Recojo' : 'Entrega'} · {CITY_LABELS[s.city!] ?? s.city}</div>
            {s.contactName && <div className="flex items-center gap-2 font-medium text-ink-900"><UserRound className="size-4 shrink-0 text-ink-500" aria-hidden="true" />{s.contactName}</div>}
            {s.contactPhone && (
              <div className="flex items-center gap-2">
                <Phone className="size-4 shrink-0 text-ink-500" aria-hidden="true" />
                <a className="font-mono font-medium text-petrol-700 underline-offset-2 hover:underline" href={`tel:+51${s.contactPhone}`} aria-label={`Llamar a ${s.contactName ?? 'contacto'}: ${formatPhone(s.contactPhone)}`}>{formatPhone(s.contactPhone)}</a>
              </div>
            )}
            {s.windowFrom && s.windowTo && <div className="flex items-center gap-2 text-ink-700"><Clock className="size-4 shrink-0 text-ink-500" aria-hidden="true" />Recibe de <span className="font-mono">{s.windowFrom}</span> a <span className="font-mono">{s.windowTo}</span></div>}
            {s.instructions && <div className="flex items-start gap-2 text-ink-700"><StickyNote className="mt-0.5 size-4 shrink-0 text-ink-500" aria-hidden="true" /><span>{s.instructions}</span></div>}
          </li>
        ))}
      </ul>
      <p className="text-xs text-ink-500">Datos visibles solo para tu empresa, la contraparte del envío y el conductor asignado.</p>
    </section>
  );
}
