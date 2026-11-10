import { DayPicker, type DayPickerProps } from 'react-day-picker';
import { es } from 'react-day-picker/locale';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';

/** Calendario base (react-day-picker v10, locale es, semana inicia lunes). Normalmente se usa vía DatePicker / DateRangePicker. */
export function Calendar({ className, classNames, ...props }: DayPickerProps) {
  return (
    <DayPicker
      locale={es}
      weekStartsOn={1}
      showOutsideDays
      className={cn('p-3', className)}
      classNames={{
        months: 'relative flex flex-col gap-4 sm:flex-row',
        month: 'space-y-2',
        month_caption: 'flex h-8 items-center justify-center text-sm font-semibold capitalize text-ink-900',
        nav: 'absolute inset-x-0 top-0 flex items-center justify-between',
        button_previous: 'inline-flex size-8 items-center justify-center rounded-input text-ink-700 hover:bg-steel-100 disabled:opacity-40',
        button_next: 'inline-flex size-8 items-center justify-center rounded-input text-ink-700 hover:bg-steel-100 disabled:opacity-40',
        month_grid: 'border-collapse',
        weekday: 'size-9 text-center text-xs font-medium capitalize text-ink-500',
        week: '',
        day: 'p-0 text-center text-sm',
        day_button: 'size-9 rounded-input tabular-nums hover:bg-steel-100 focus-visible:outline-2',
        selected: '[&>button]:bg-petrol-700 [&>button]:text-white [&>button:hover]:bg-petrol-800',
        range_start: 'rounded-l-input bg-petrol-50',
        range_end: 'rounded-r-input bg-petrol-50',
        range_middle: 'bg-petrol-50 [&>button]:rounded-none [&>button]:bg-transparent [&>button]:text-petrol-800 [&>button:hover]:bg-petrol-700/10',
        today: 'font-bold text-petrol-700',
        outside: 'text-ink-400',
        disabled: 'pointer-events-none text-ink-400 opacity-50',
        hidden: 'invisible',
        ...classNames,
      }}
      components={{
        Chevron: ({ orientation }) => (orientation === 'left' ? <ChevronLeft className="size-4" /> : <ChevronRight className="size-4" />),
      }}
      {...props}
    />
  );
}
