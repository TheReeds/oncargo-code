import { useEffect, useState } from 'react';
import { Command } from 'cmdk';
import { CornerDownLeft, Moon, Monitor, Search, Sun } from 'lucide-react';
import { savePref, type ThemePref } from '@/lib/theme';
import { Kbd } from '@/components/ui/kbd';

export type NavTarget = { label: string; href: string; group?: string; keywords?: string[] };

/**
 * Paleta de comandos del topbar (Ctrl/⌘ + K): ir a cualquier sección del rol y cambiar de tema, solo con teclado.
 * Es un diálogo modal (cmdk sobre Radix Dialog): foco atrapado, Esc cierra, el botón disparador es un `button` real.
 */
export default function CommandMenu({ items }: { items: NavTarget[] }) {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setOpen((o) => !o); }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);
  const groups = [...new Set(items.map((i) => i.group ?? 'Ir a'))];
  const go = (href: string) => { setOpen(false); window.location.href = href; };
  const theme = (v: ThemePref) => { savePref(v); setOpen(false); };
  const itemCls = 'flex cursor-pointer items-center gap-2 rounded-md px-2.5 py-2 text-sm text-ink-900 data-[selected=true]:bg-petrol-50 data-[selected=true]:text-petrol-800';
  const headCls = '[&_[cmdk-group-heading]]:px-2.5 [&_[cmdk-group-heading]]:pb-1 [&_[cmdk-group-heading]]:pt-2 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:text-ink-500';
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-haspopup="dialog"
        className="inline-flex h-9 items-center gap-2 rounded-input border border-steel-200 bg-surface px-2.5 text-sm text-ink-500 transition-colors hover:border-ink-400 hover:text-ink-900 md:w-52"
      >
        <Search className="size-4 shrink-0" aria-hidden="true" />
        <span className="hidden flex-1 text-left md:inline">Ir a…</span>
        <span className="sr-only md:hidden">Buscar o ir a una sección</span>
        <Kbd className="hidden md:inline">Ctrl K</Kbd>
      </button>
      <Command.Dialog
        open={open}
        onOpenChange={setOpen}
        label="Ir a una sección"
        overlayClassName="fixed inset-0 z-[70] bg-scrim"
        contentClassName="fixed left-1/2 top-[12vh] z-[70] w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 overflow-hidden rounded-card border border-steel-200 bg-surface shadow-xl"
      >
        <div className="flex items-center gap-2 border-b border-steel-200 px-3">
          <Search className="size-4 shrink-0 text-ink-500" aria-hidden="true" />
          <Command.Input placeholder="Ir a una sección o cambiar el tema…" className="h-12 w-full bg-transparent text-sm outline-none placeholder:text-ink-500" />
        </div>
        <Command.List className={`max-h-80 overflow-y-auto p-1.5 ${headCls}`}>
          <Command.Empty className="px-3 py-8 text-center text-sm text-ink-500">No encontramos esa sección.</Command.Empty>
          {groups.map((g) => (
            <Command.Group key={g} heading={g}>
              {items.filter((i) => (i.group ?? 'Ir a') === g).map((i) => (
                <Command.Item key={i.href} value={i.label} keywords={i.keywords} onSelect={() => go(i.href)} className={itemCls}>
                  {i.label}
                  <CornerDownLeft className="ml-auto size-3.5 text-ink-500 opacity-0 data-[selected=true]:opacity-100" aria-hidden="true" />
                </Command.Item>
              ))}
            </Command.Group>
          ))}
          <Command.Group heading="Apariencia">
            <Command.Item value="Tema claro" keywords={['claro', 'light']} onSelect={() => theme('light')} className={itemCls}><Sun className="size-4" aria-hidden="true" /> Tema claro</Command.Item>
            <Command.Item value="Tema oscuro" keywords={['oscuro', 'dark']} onSelect={() => theme('dark')} className={itemCls}><Moon className="size-4" aria-hidden="true" /> Tema oscuro</Command.Item>
            <Command.Item value="Tema del sistema" keywords={['sistema', 'auto']} onSelect={() => theme('system')} className={itemCls}><Monitor className="size-4" aria-hidden="true" /> Tema del sistema</Command.Item>
          </Command.Group>
        </Command.List>
        <div className="flex items-center justify-between border-t border-steel-200 px-3 py-2 text-xs text-ink-500">
          <span><Kbd>↑</Kbd> <Kbd>↓</Kbd> para moverte · <Kbd>Enter</Kbd> para abrir</span>
          <span><Kbd>Esc</Kbd> cierra</span>
        </div>
      </Command.Dialog>
    </>
  );
}
