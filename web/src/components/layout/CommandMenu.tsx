import { useEffect, useRef, useState } from 'react';
import { Command } from 'cmdk';
import { actions } from 'astro:actions';
import { Building2, CornerDownLeft, FileText, Moon, Monitor, Search, Sun, Truck } from 'lucide-react';
import { savePref, type ThemePref } from '@/lib/theme';
import { Kbd } from '@/components/ui/kbd';

export type NavTarget = { label: string; href: string; group?: string; keywords?: string[] };
type Hit = { type: 'REQUEST' | 'SHIPMENT' | 'COUNTERPART'; id: string; code?: string | null; title: string; subtitle?: string | null; href: string };
const HIT_GROUPS: { type: Hit['type']; label: string; icon: typeof FileText }[] = [
  { type: 'REQUEST', label: 'Solicitudes', icon: FileText },
  { type: 'SHIPMENT', label: 'Envíos', icon: Truck },
  { type: 'COUNTERPART', label: 'Contrapartes', icon: Building2 },
];
const fold = (t: string) => t.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

/**
 * Paleta de comandos del topbar (Ctrl/⌘ + K): ir a cualquier sección del rol, cambiar de tema y BUSCAR de verdad:
 * solicitudes, envíos y contrapartes por código (OC-7K2QX), referencia propia, ruta, placa o razón social
 * (`GET /search`, solo lo que el usuario ya puede ver; hasta 8 por tipo, con 200 ms de espera al escribir).
 * Es un diálogo modal (cmdk sobre Radix Dialog): foco atrapado, Esc cierra, el botón disparador es un `button` real.
 */
export default function CommandMenu({ items }: { items: NavTarget[] }) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<Hit[]>([]);
  const [status, setStatus] = useState<'idle' | 'loading' | 'done' | 'error'>('idle');
  const seq = useRef(0);
  const term = query.trim();

  useEffect(() => {
    const my = ++seq.current;
    if (term.length < 2) { setHits([]); setStatus('idle'); return; }
    setStatus('loading');
    const t = setTimeout(async () => {
      const { data, error } = await actions.search.query({ q: term });
      if (my !== seq.current) return;
      if (error) { setHits([]); setStatus('error'); } else { setHits((data ?? []) as Hit[]); setStatus('done'); }
    }, 200);
    return () => clearTimeout(t);
  }, [term]);
  const close = (o: boolean) => { setOpen(o); if (!o) setQuery(''); };
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setOpen((o) => { if (o) setQuery(''); return !o; }); }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);
  // Con `shouldFilter={false}` el filtrado es nuestro: las secciones se filtran aquí; los resultados ya vienen filtrados.
  const needle = fold(term);
  const navItems = needle ? items.filter((i) => fold([i.label, ...(i.keywords ?? [])].join(' ')).includes(needle)) : items;
  const groups = [...new Set(navItems.map((i) => i.group ?? 'Ir a'))];
  const themes = [
    { v: 'light' as ThemePref, label: 'Tema claro', keywords: ['claro', 'light'], icon: Sun },
    { v: 'dark' as ThemePref, label: 'Tema oscuro', keywords: ['oscuro', 'dark'], icon: Moon },
    { v: 'system' as ThemePref, label: 'Tema del sistema', keywords: ['sistema', 'auto'], icon: Monitor },
  ].filter((t) => !needle || fold([t.label, ...t.keywords].join(' ')).includes(needle));
  const nothing = navItems.length === 0 && themes.length === 0 && hits.length === 0;
  const go = (href: string) => { close(false); window.location.href = href; };
  const theme = (v: ThemePref) => { savePref(v); close(false); };
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
        onOpenChange={close}
        shouldFilter={false}
        label="Buscar o ir a una sección"
        overlayClassName="fixed inset-0 z-[70] bg-scrim"
        contentClassName="fixed left-1/2 top-[12vh] z-[70] w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 overflow-hidden rounded-card border border-steel-200 bg-surface shadow-xl"
      >
        <div className="flex items-center gap-2 border-b border-steel-200 px-3">
          <Search className="size-4 shrink-0 text-ink-500" aria-hidden="true" />
          <Command.Input value={query} onValueChange={setQuery} placeholder="Busca un código, referencia o ruta, o ve a una sección…" className="h-12 w-full bg-transparent text-sm outline-none placeholder:text-ink-500" />
        </div>
        <Command.List className={`max-h-80 overflow-y-auto p-1.5 ${headCls}`}>
          {HIT_GROUPS.map(({ type, label, icon: Icon }) => {
            const list = hits.filter((h) => h.type === type);
            if (list.length === 0) return null;
            return (
              <Command.Group key={type} heading={label}>
                {list.map((h) => (
                  <Command.Item key={`${h.type}:${h.id}`} value={`${h.type}:${h.id}`} onSelect={() => go(h.href)} className={itemCls}>
                    <Icon className="size-4 shrink-0 text-ink-500" aria-hidden="true" />
                    <span className="min-w-0 flex-1">
                      <span className="flex items-baseline gap-2">
                        {h.code && <span className="font-mono text-xs font-semibold text-petrol-700">{h.code}</span>}
                        <span className="truncate">{h.title}</span>
                      </span>
                      {h.subtitle && <span className="block truncate text-xs text-ink-500">{h.subtitle}</span>}
                    </span>
                  </Command.Item>
                ))}
              </Command.Group>
            );
          })}
          {status === 'loading' && hits.length === 0 && <div className="px-3 py-3 text-sm text-ink-500" role="status">Buscando…</div>}
          {status === 'error' && <div className="px-3 py-3 text-sm text-red-600" role="alert">No pudimos buscar ahora. Intenta de nuevo en unos segundos.</div>}
          {groups.map((g) => (
            <Command.Group key={g} heading={g}>
              {navItems.filter((i) => (i.group ?? 'Ir a') === g).map((i) => (
                <Command.Item key={i.href} value={`nav:${i.href}`} onSelect={() => go(i.href)} className={itemCls}>
                  {i.label}
                  <CornerDownLeft className="ml-auto size-3.5 text-ink-500 opacity-0 data-[selected=true]:opacity-100" aria-hidden="true" />
                </Command.Item>
              ))}
            </Command.Group>
          ))}
          {themes.length > 0 && (
            <Command.Group heading="Apariencia">
              {themes.map((t) => <Command.Item key={t.v} value={`theme:${t.v}`} onSelect={() => theme(t.v)} className={itemCls}><t.icon className="size-4" aria-hidden="true" /> {t.label}</Command.Item>)}
            </Command.Group>
          )}
          {nothing && status !== 'loading' && status !== 'error' && (
            <div className="px-3 py-8 text-center text-sm text-ink-500" role="status">{term.length >= 2 ? `No encontramos resultados para «${term}».` : 'No encontramos esa sección.'}</div>
          )}
        </Command.List>
        <div className="flex items-center justify-between border-t border-steel-200 px-3 py-2 text-xs text-ink-500">
          <span><Kbd>↑</Kbd> <Kbd>↓</Kbd> para moverte · <Kbd>Enter</Kbd> para abrir</span>
          <span><Kbd>Esc</Kbd> cierra</span>
        </div>
      </Command.Dialog>
    </>
  );
}
