import * as React from 'react';
import { CalendarDays, X } from 'lucide-react';
import { format } from 'date-fns';
import { es } from 'date-fns/locale';
import type { DateRange } from 'react-day-picker';
import { cn } from '@/lib/utils';
import { fieldClass } from './input';
import { Calendar } from './calendar';
import { Popover, PopoverContent, PopoverTrigger } from './popover';

/** Las fechas viajan como string ISO `YYYY-MM-DD` (LocalDate de la API), sin zona horaria. */
export const parseISODate = (s?: string | null) => {
  if (!s) return undefined;
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
};
export const toISODate = (d: Date) => format(d, 'yyyy-MM-dd');
const show = (s?: string) => (s ? format(parseISODate(s)!, 'd MMM yyyy', { locale: es }).replace('.', '') : '');

/** Botón "quitar" superpuesto al campo (hermano del disparador del popover, nunca anidado en él). */
function ClearButton({ label, onClear }: { label: string; onClear: () => void }) {
  return (
    <button type="button" aria-label={label} onClick={onClear}
      className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-ink-500 hover:bg-steel-100 hover:text-ink-700 focus-visible:outline-2 focus-visible:outline-petrol-700">
      <X className="size-3.5" aria-hidden="true" />
    </button>
  );
}

type Base = { id?: string; invalid?: boolean; disabled?: boolean; className?: string; min?: string; max?: string; name?: string };

/** Selector de una fecha. `value`/`min`/`max` = 'YYYY-MM-DD'. */
export function DatePicker({ value, onValueChange, placeholder = 'Selecciona una fecha', id, invalid, disabled, className, min, max, name, clearable }: Base & { value: string; onValueChange: (v: string) => void; placeholder?: string; clearable?: boolean }) {
  const [open, setOpen] = React.useState(false);
  const sel = parseISODate(value);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      {/* El botón de quitar va al lado del disparador, no dentro: un control interactivo anidado en otro rompe WCAG (axe nested-interactive). */}
      <div className={cn('relative', className)}>
        <PopoverTrigger asChild>
          <button type="button" id={id} aria-invalid={invalid || undefined} disabled={disabled} className={cn(fieldClass, 'flex h-10 w-full items-center gap-2 text-left', clearable && value && 'pr-9')}>
            <CalendarDays className="size-4 shrink-0 text-ink-500" aria-hidden="true" />
            <span className={cn('flex-1 truncate', !value && 'text-ink-500')}>{value ? show(value) : placeholder}</span>
          </button>
        </PopoverTrigger>
        {clearable && value && !disabled && <ClearButton label="Quitar fecha" onClear={() => onValueChange('')} />}
      </div>
      {name && <input type="hidden" name={name} value={value} />}
      <PopoverContent>
        <Calendar mode="single" selected={sel} defaultMonth={sel} onSelect={(d) => { if (d) { onValueChange(toISODate(d)); setOpen(false); } }} disabled={[...(min ? [{ before: parseISODate(min)! }] : []), ...(max ? [{ after: parseISODate(max)! }] : [])]} />
      </PopoverContent>
    </Popover>
  );
}

export type DateRangeValue = { from: string; to: string };

const startOfToday = () => { const d = new Date(); d.setHours(0, 0, 0, 0); return d; };
export const DATE_PRESETS: { label: string; range: () => DateRangeValue }[] = [
  { label: 'Hoy', range: () => { const t = toISODate(startOfToday()); return { from: t, to: t }; } },
  { label: 'Próximos 7 días', range: () => { const d = startOfToday(); const e = new Date(d); e.setDate(d.getDate() + 6); return { from: toISODate(d), to: toISODate(e) }; } },
  { label: 'Este mes', range: () => { const d = startOfToday(); return { from: toISODate(d), to: toISODate(new Date(d.getFullYear(), d.getMonth() + 1, 0)) }; } },
  { label: 'Próximos 30 días', range: () => { const d = startOfToday(); const e = new Date(d); e.setDate(d.getDate() + 29); return { from: toISODate(d), to: toISODate(e) }; } },
];

/** Rango de fechas con atajos ("Hoy", "Próximos 7 días", "Este mes"). `value` = {from,to} ('' si vacío). */
export function DateRangePicker({ value, onValueChange, placeholder = 'Rango de fechas', id, invalid, disabled, className, min, max }: Base & { value: DateRangeValue; onValueChange: (v: DateRangeValue) => void; placeholder?: string }) {
  const [open, setOpen] = React.useState(false);
  const sel: DateRange | undefined = value.from ? { from: parseISODate(value.from), to: parseISODate(value.to || value.from) } : undefined;
  const label = !value.from ? '' : value.to && value.to !== value.from ? `${show(value.from)} – ${show(value.to)}` : show(value.from);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <div className={cn('relative', className)}>
        <PopoverTrigger asChild>
          <button type="button" id={id} aria-invalid={invalid || undefined} disabled={disabled} className={cn(fieldClass, 'flex h-10 w-full items-center gap-2 text-left', label && 'pr-9')}>
            <CalendarDays className="size-4 shrink-0 text-ink-500" aria-hidden="true" />
            <span className={cn('flex-1 truncate', !label && 'text-ink-500')}>{label || placeholder}</span>
          </button>
        </PopoverTrigger>
        {label && !disabled && <ClearButton label="Quitar fechas" onClear={() => onValueChange({ from: '', to: '' })} />}
      </div>
      <PopoverContent className="flex max-w-[calc(100vw-1rem)] flex-col sm:flex-row">
        <div className="flex flex-row flex-wrap gap-1 border-b border-steel-200 p-2 sm:w-40 sm:flex-col sm:border-b-0 sm:border-r">
          {DATE_PRESETS.map((p) => (
            <button key={p.label} type="button" onClick={() => { onValueChange(p.range()); setOpen(false); }} className="rounded-input px-3 py-1.5 text-left text-sm text-ink-700 hover:bg-petrol-50 hover:text-petrol-800">{p.label}</button>
          ))}
        </div>
        <Calendar
          mode="range"
          selected={sel}
          defaultMonth={sel?.from}
          numberOfMonths={1}
          onSelect={(r) => {
            onValueChange({ from: r?.from ? toISODate(r.from) : '', to: r?.to ? toISODate(r.to) : r?.from ? toISODate(r.from) : '' });
            if (r?.from && r?.to && r.from.getTime() !== r.to.getTime()) setOpen(false);
          }}
          disabled={[...(min ? [{ before: parseISODate(min)! }] : []), ...(max ? [{ after: parseISODate(max)! }] : [])]}
        />
      </PopoverContent>
    </Popover>
  );
}
