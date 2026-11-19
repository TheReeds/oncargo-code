import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { CalendarDays } from 'lucide-react';

type Day = { name?: string; kindLabel?: string; national?: boolean };

/**
 * Aviso al elegir una fecha (2.14): si ese día es feriado o hay un paro que afecta a la ciudad de origen. No bloquea:
 * solo informa (la recojo puede ser igual ese día).
 */
export function HolidayHint({ date, city }: { date: string; city?: string }) {
  const [days, setDays] = useState<Day[]>([]);
  useEffect(() => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) { setDays([]); return; }
    let alive = true;
    actions.config.calendar({ from: date, to: date, city: city || undefined })
      .then(({ data }) => { if (alive) setDays((data as Day[] | undefined) ?? []); })
      .catch(() => { if (alive) setDays([]); });
    return () => { alive = false; };
  }, [date, city]);
  if (days.length === 0) return null;
  return (
    <p role="status" className="mt-1.5 flex items-start gap-1.5 text-xs text-amber-700" data-testid="holiday-hint">
      <CalendarDays className="mt-px size-3.5 shrink-0" aria-hidden="true" />
      <span>{days.map((d) => `${d.kindLabel}: ${d.name}`).join(' · ')}. Confirma que se pueda recoger ese día.</span>
    </p>
  );
}
