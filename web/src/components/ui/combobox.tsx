import * as React from 'react';
import { Command } from 'cmdk';
import { Check, ChevronDown, Search, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { fieldClass } from './input';
import { Popover, PopoverContent, PopoverTrigger } from './popover';
import type { Option } from './select';

function List({ options, isSelected, onPick, searchPlaceholder, emptyText }: { options: Option[]; isSelected: (v: string) => boolean; onPick: (v: string) => void; searchPlaceholder: string; emptyText: string }) {
  return (
    <Command>
      <div className="flex items-center gap-2 border-b border-steel-200 px-3">
        <Search className="size-4 shrink-0 text-ink-500" aria-hidden="true" />
        <Command.Input placeholder={searchPlaceholder} className="h-10 w-full bg-transparent text-sm outline-none placeholder:text-ink-500" />
      </div>
      <Command.List className="max-h-64 overflow-y-auto p-1">
        <Command.Empty className="px-3 py-6 text-center text-sm text-ink-500">{emptyText}</Command.Empty>
        {options.map((o) => (
          <Command.Item key={o.value} value={o.label} keywords={[o.value]} disabled={o.disabled} onSelect={() => onPick(o.value)} className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-sm text-ink-900 data-[disabled=true]:opacity-50 data-[selected=true]:bg-petrol-50 data-[selected=true]:text-petrol-800">
            <Check className={cn('size-4 text-petrol-700', isSelected(o.value) ? 'opacity-100' : 'opacity-0')} aria-hidden="true" />
            <span className="truncate">{o.label}</span>
            {o.hint && <span className="ml-auto text-xs text-ink-500">{o.hint}</span>}
          </Command.Item>
        ))}
      </Command.List>
    </Command>
  );
}

type Common = { options: Option[]; id?: string; placeholder?: string; searchPlaceholder?: string; emptyText?: string; invalid?: boolean; disabled?: boolean; className?: string };

/** Select con búsqueda (cmdk + Popover). Ideal para ciudades, vehículos, conductores. `allowClear` agrega una "x" para vaciar (filtros). */
export function Combobox({ value, onValueChange, options, id, placeholder = 'Selecciona…', searchPlaceholder = 'Buscar…', emptyText = 'Sin resultados', invalid, disabled, className, allowClear }: Common & { value: string; onValueChange: (v: string) => void; allowClear?: boolean }) {
  const [open, setOpen] = React.useState(false);
  const current = options.find((o) => o.value === value);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <div className="relative">
        <PopoverTrigger asChild>
          <button type="button" id={id} role="combobox" aria-expanded={open} aria-invalid={invalid || undefined} disabled={disabled} className={cn(fieldClass, 'flex h-10 items-center justify-between gap-2 text-left', allowClear && current && 'pr-14', className)}>
            <span className={cn('truncate', !current && 'text-ink-500')}>{current?.label ?? placeholder}</span>
            <ChevronDown className="size-4 shrink-0 text-ink-500" aria-hidden="true" />
          </button>
        </PopoverTrigger>
        {allowClear && current && !disabled && (
          <button type="button" aria-label="Quitar selección" onClick={() => onValueChange('')} className="absolute right-8 top-1/2 -translate-y-1/2 rounded p-1 text-ink-500 hover:bg-steel-100"><X className="size-3.5" /></button>
        )}
      </div>
      <PopoverContent className="w-[var(--radix-popover-trigger-width)] min-w-56 p-0">
        <List options={options} isSelected={(v) => v === value} onPick={(v) => { onValueChange(v === value && allowClear ? '' : v); setOpen(false); }} searchPlaceholder={searchPlaceholder} emptyText={emptyText} />
      </PopoverContent>
    </Popover>
  );
}

/** Selección múltiple con chips. `value` es un arreglo de valores. */
export function MultiSelect({ value, onValueChange, options, id, placeholder = 'Selecciona…', searchPlaceholder = 'Buscar…', emptyText = 'Sin resultados', invalid, disabled, className }: Common & { value: string[]; onValueChange: (v: string[]) => void }) {
  const [open, setOpen] = React.useState(false);
  const toggle = (v: string) => onValueChange(value.includes(v) ? value.filter((x) => x !== v) : [...value, v]);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <button type="button" id={id} role="combobox" aria-expanded={open} aria-invalid={invalid || undefined} disabled={disabled} className={cn(fieldClass, 'flex min-h-10 items-center justify-between gap-2 py-1.5 text-left', className)}>
          <span className="flex flex-wrap gap-1">
            {value.length === 0 && <span className="text-ink-500">{placeholder}</span>}
            {value.map((v) => (
              <span key={v} className="inline-flex items-center gap-1 rounded-full bg-petrol-50 px-2 py-0.5 text-xs font-medium text-petrol-700">
                {options.find((o) => o.value === v)?.label ?? v}
                <span role="button" tabIndex={-1} aria-label={`Quitar ${options.find((o) => o.value === v)?.label ?? v}`} onClick={(e) => { e.stopPropagation(); toggle(v); }} className="rounded-full hover:bg-petrol-700/10"><X className="size-3" /></span>
              </span>
            ))}
          </span>
          <ChevronDown className="size-4 shrink-0 text-ink-500" aria-hidden="true" />
        </button>
      </PopoverTrigger>
      <PopoverContent className="w-[var(--radix-popover-trigger-width)] min-w-56 p-0">
        <List options={options} isSelected={(v) => value.includes(v)} onPick={toggle} searchPlaceholder={searchPlaceholder} emptyText={emptyText} />
      </PopoverContent>
    </Popover>
  );
}
