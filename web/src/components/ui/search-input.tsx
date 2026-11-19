import * as React from 'react';
import { Search, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { fieldClass } from './input';

/**
 * Buscador de texto: se confirma con Enter (no mientras se compone con un IME) o con el botón de lupa; la X lo limpia
 * y confirma el vacío. Úsalo dentro de `Field` (pon el `id` en el `htmlFor`) y deja que el padre navegue o filtre
 * en `onSearch`. `value` es el texto ya aplicado: si cambia desde fuera, el campo se sincroniza.
 * <Field label="Buscar" htmlFor="q"><SearchInput id="q" value={q} onSearch={setQ} placeholder="Razón social, RUC…" /></Field>
 */
export function SearchInput({ value, onSearch, className, ...p }: {
  value: string;
  onSearch: (text: string) => void;
} & Omit<React.InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type'>) {
  const [text, setText] = React.useState(value);
  React.useEffect(() => setText(value), [value]);
  const apply = (t: string) => onSearch(t.trim());
  return (
    <div className={cn('relative', className)}>
      <button type="button" onClick={() => apply(text)} aria-label="Buscar" className="absolute inset-y-0 left-0 grid w-10 place-items-center rounded-l-input text-ink-500 hover:text-ink-900 focus-visible:outline-2 focus-visible:outline-offset-[-2px]">
        <Search className="size-4" aria-hidden="true" />
      </button>
      <input
        {...p}
        type="search"
        enterKeyHint="search"
        autoComplete="off"
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.nativeEvent.isComposing) { e.preventDefault(); apply(text); }
        }}
        className={cn(fieldClass, 'h-10 pl-10 pr-9 [&::-webkit-search-cancel-button]:hidden [&::-webkit-search-decoration]:hidden')}
      />
      {text && (
        <button type="button" onClick={() => { setText(''); onSearch(''); }} aria-label="Borrar búsqueda" className="absolute inset-y-0 right-0 grid w-9 place-items-center rounded-r-input text-ink-500 hover:text-ink-900 focus-visible:outline-2 focus-visible:outline-offset-[-2px]">
          <X className="size-4" aria-hidden="true" />
        </button>
      )}
    </div>
  );
}
