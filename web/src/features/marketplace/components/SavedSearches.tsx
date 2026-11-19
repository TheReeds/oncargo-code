import { useCallback, useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { BellPlus, BookmarkCheck, ExternalLink, Search, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Combobox } from '@/components/ui/combobox';
import { Alert } from '@/components/ui/alert';
import { Switch } from '@/components/ui/form-controls';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { Skeleton } from '@/components/ui/skeleton';
import { toFormErrors, splitActionError } from '@/lib/action-error';
import { Can } from '@/lib/permissions/react';
import { VEHICLE_LABELS } from '@/lib/format';
import type { VehicleView } from '@/lib/api/server';
import type { SavedSearch } from '../server';
import { describeFilters, describeSearch, hasSearchFilter, savedSearchHref, toSavedBody, type SearchFilters } from '../saved-searches';

const NAME_MAX = 80;

/** Diálogo «Guardar búsqueda»: nombre + los filtros de la pantalla (y, si quieres, un vehículo) → alerta de carga nueva. */
function SaveSearchDialog({ open, onOpenChange, filters, vehicles, onSaved }: { open: boolean; onOpenChange: (o: boolean) => void; filters: SearchFilters; vehicles: VehicleView[]; onSaved: (s: SavedSearch) => void }) {
  const [name, setName] = useState('');
  const [vehicleId, setVehicleId] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const summary = describeFilters(filters);

  useEffect(() => { if (open) { setName(''); setVehicleId(''); setErrors({}); setFormError(null); setBusy(false); } }, [open]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    setFormError(null);
    if (!name.trim()) { setErrors({ name: 'Ponle un nombre a la búsqueda, por ejemplo «Lima a Juliaca».' }); return; }
    setErrors({});
    setBusy(true);
    const { data, error } = await actions.marketplace.saveSearch(toSavedBody(filters, name, vehicleId));
    setBusy(false);
    if (error) {
      const r = toFormErrors(error, ['name', 'originCity', 'destinationCity', 'pickupTo', 'maxWeightKg', 'vehicleId']);
      setErrors(r.errors);
      setFormError(r.formError);
      return;
    }
    toast.success('Búsqueda guardada', 'Te avisamos cuando se publique una carga que coincida.');
    onSaved(data as SavedSearch);
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !busy && onOpenChange(o)} title="Guardar búsqueda" description="Te avisamos cuando se publique una carga que coincida con estos filtros."
      footer={<><Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Volver</Button><Button type="submit" form="saved-search-form" loading={busy}>{busy ? 'Guardando…' : 'Guardar búsqueda'}</Button></>}>
      <form id="saved-search-form" onSubmit={submit} noValidate method="post" className="space-y-4">
        {formError && <Alert tone="error" title="No pudimos guardar la búsqueda">{formError}</Alert>}
        <div className="rounded-input border border-steel-200 bg-steel-100/60 px-3 py-2 text-sm text-ink-700" data-testid="saved-summary">
          <span className="text-xs text-ink-500">Filtros que se guardan</span>
          <p className="font-medium text-ink-900">{summary || 'Sin filtros'}</p>
        </div>
        <Field label="Nombre de la búsqueda" htmlFor="ss-name" error={errors.name}>
          <Input id="ss-name" value={name} maxLength={NAME_MAX} onChange={(e) => { setName(e.target.value); setErrors((x) => ({ ...x, name: '' })); }} placeholder="Lima a Juliaca, carga general" aria-invalid={!!errors.name} autoComplete="off" />
        </Field>
        {vehicles.length > 0 && (
          <Field label="Solo si puede ir este vehículo" htmlFor="ss-vehicle" hint="Opcional. Te avisamos solo de cargas que ese vehículo pueda llevar." error={errors.vehicleId}>
            <Combobox id="ss-vehicle" allowClear value={vehicleId} onValueChange={setVehicleId} placeholder="Cualquiera de mi flota" searchPlaceholder="Buscar por placa…"
              options={vehicles.map((v) => ({ value: v.id!, label: `${v.plate} · ${VEHICLE_LABELS[v.type!] ?? v.type}` }))} />
          </Field>
        )}
        {(errors.originCity || errors.destinationCity || errors.pickupTo || errors.maxWeightKg) && <Alert tone="error">{errors.originCity || errors.destinationCity || errors.pickupTo || errors.maxWeightKg}</Alert>}
        <p className="text-xs text-ink-500">Los avisos llegan por la app y por correo según tu elección. <a href="/cuenta/avisos" className="font-medium text-petrol-700 underline underline-offset-2">Elige cómo te avisamos</a></p>
      </form>
    </Dialog>
  );
}

/**
 * «Guardar búsqueda» + «Mis búsquedas» de Cargas disponibles (2.3). Guardar, pausar y borrar van bajo `<Can>` (el Lector solo
 * ve y aplica). Los canales (app/correo) se eligen en `/cuenta/avisos`, categoría «Alertas de carga».
 */
export default function SavedSearches({ filters, vehicles }: { filters: SearchFilters; vehicles: VehicleView[] }) {
  const [items, setItems] = useState<SavedSearch[] | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    const { data, error } = await actions.marketplace.savedSearches({});
    if (error) { setLoadError(true); return; }
    setLoadError(false);
    setItems((data as SavedSearch[]) ?? []);
  }, []);
  useEffect(() => { load(); }, [load]);

  async function toggle(s: SavedSearch, active: boolean) {
    setBusyId(s.id!);
    const { data, error } = await actions.marketplace.updateSavedSearch({
      // La API manda `null` en lo que no se filtra; la action espera «ausente».
      id: s.id!, name: s.name!, originCity: s.originCity ?? undefined, destinationCity: s.destinationCity ?? undefined, cargoType: s.cargoType ?? undefined,
      pickupFrom: s.pickupFrom ?? undefined, pickupTo: s.pickupTo ?? undefined, maxWeightKg: s.maxWeightKg ?? undefined, vehicleId: s.vehicleId ?? undefined, active,
    });
    setBusyId(null);
    if (error) { toast.error(splitActionError(error).message); return; }
    setItems((l) => (l ?? []).map((x) => (x.id === s.id ? (data as SavedSearch) : x)));
    toast.success(active ? 'Búsqueda reanudada' : 'Búsqueda en pausa');
  }

  async function remove(s: SavedSearch) {
    const ok = await confirm({ title: `¿Borrar «${s.name}»?`, description: 'Dejarás de recibir avisos de esta búsqueda. No se puede deshacer.', confirmLabel: 'Borrar búsqueda', tone: 'destructive' });
    if (!ok) return;
    setBusyId(s.id!);
    const { error } = await actions.marketplace.deleteSavedSearch({ id: s.id! });
    setBusyId(null);
    if (error) { toast.error(splitActionError(error).message); return; }
    setItems((l) => (l ?? []).filter((x) => x.id !== s.id));
    toast.success('Búsqueda borrada');
  }

  const canSave = hasSearchFilter(filters);
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Can>
        <Button variant="outline" size="sm" disabled={!canSave} onClick={() => setSaving(true)} aria-describedby={canSave ? undefined : 'ss-hint'}>
          <BellPlus aria-hidden="true" />Guardar búsqueda
        </Button>
        {!canSave && <span id="ss-hint" className="sr-only">Elige al menos un filtro para guardar la búsqueda.</span>}
      </Can>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button variant="outline" size="sm" data-testid="saved-searches-trigger"><BookmarkCheck aria-hidden="true" />Mis búsquedas{items && items.length > 0 ? <span className="rounded-full bg-petrol-700 px-1.5 font-mono text-[11px] text-on-solid">{items.length}</span> : null}</Button>
        </PopoverTrigger>
        <PopoverContent align="end" aria-label="Mis búsquedas" className="w-[min(26rem,calc(100vw-2rem))] p-0">
          <div className="border-b border-steel-200 px-4 py-3">
            <p className="text-sm font-semibold text-ink-900">Mis búsquedas</p>
            <p className="text-xs text-ink-500">Te avisamos cuando se publica una carga que coincide.</p>
          </div>
          {items === null && !loadError ? (
            <div className="space-y-2 p-4"><Skeleton className="h-10 w-full" /><Skeleton className="h-10 w-full" /></div>
          ) : loadError ? (
            <div className="p-4"><Alert tone="error">No pudimos cargar tus búsquedas. <button type="button" className="font-medium underline" onClick={load}>Reintentar</button></Alert></div>
          ) : items!.length === 0 ? (
            <p className="px-4 py-6 text-center text-sm text-ink-700" data-testid="saved-empty">Aún no guardaste búsquedas. Elige filtros y pulsa «Guardar búsqueda».</p>
          ) : (
            <ul className="lx-gutter max-h-80 divide-y divide-steel-200 overflow-y-auto" aria-label="Búsquedas guardadas">
              {items!.map((s) => (
                <li key={s.id} className="flex items-start gap-3 px-4 py-3" data-testid="saved-item">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-ink-900">{s.name}{s.active === false && <span className="ml-2 text-xs font-normal text-amber-700">En pausa</span>}</p>
                    <p className="text-xs text-ink-700">{describeSearch(s) || 'Sin filtros'}</p>
                    <div className="mt-1.5 flex flex-wrap items-center gap-1">
                      <Button asChild size="sm" variant="outline"><a href={savedSearchHref(s)}><Search aria-hidden="true" />Aplicar<span className="sr-only"> «{s.name}»</span></a></Button>
                      <Can>
                        <Button size="sm" variant="ghost" loading={busyId === s.id} onClick={() => remove(s)}><Trash2 aria-hidden="true" />Borrar<span className="sr-only"> «{s.name}»</span></Button>
                      </Can>
                    </div>
                  </div>
                  <Can>
                    <div className="flex shrink-0 items-center gap-2 pt-0.5">
                      <label htmlFor={`ss-active-${s.id}`} className="text-xs text-ink-700">{s.active === false ? 'Pausada' : 'Activa'}</label>
                      <Switch id={`ss-active-${s.id}`} checked={s.active !== false} disabled={busyId === s.id} onCheckedChange={(v) => toggle(s, v)} aria-label={`${s.active === false ? 'Reanudar' : 'Pausar'} «${s.name}»`} />
                    </div>
                  </Can>
                </li>
              ))}
            </ul>
          )}
          <div className="border-t border-steel-200 px-4 py-2.5 text-xs">
            <a href="/cuenta/avisos" className="inline-flex items-center gap-1 font-medium text-petrol-700 underline-offset-2 hover:underline">Elige cómo te avisamos<ExternalLink className="size-3" aria-hidden="true" /></a>
          </div>
        </PopoverContent>
      </Popover>
      <SaveSearchDialog open={saving} onOpenChange={setSaving} filters={filters} vehicles={vehicles} onSaved={(s) => setItems((l) => [s, ...(l ?? [])])} />
    </div>
  );
}
