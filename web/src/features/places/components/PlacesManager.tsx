import { useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { Archive, ArchiveRestore, MapPin, MoreHorizontal, Pencil, Plus, Search, SearchX } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Combobox } from '@/components/ui/combobox';
import { Dialog } from '@/components/ui/dialog';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Field } from '@/components/ui/field';
import { FilterBar } from '@/components/ui/filter-bar';
import { Input, Textarea } from '@/components/ui/input';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Select } from '@/components/ui/select';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { TimeSelect } from '@/components/ui/time-select';
import { Alert } from '@/components/ui/alert';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { EmptyState } from '@/components/domain/EmptyState';
import { Can } from '@/lib/permissions/react';
import { CITY_LABELS } from '@/lib/format';
import { LIMITS } from '@/lib/limits';
import { toFormErrors } from '@/lib/action-error';
import type { PlaceView } from '../server';

const KIND_LABEL: Record<string, string> = { PICKUP: 'Recojo', DELIVERY: 'Entrega', BOTH: 'Recojo y entrega' };
const KIND_OPTIONS = Object.entries(KIND_LABEL).map(([value, label]) => ({ value, label }));

type Form = { label: string; city: string; address: string; contactName: string; contactPhone: string; openingHours: string; windowFrom: string; windowTo: string; instructions: string; kind: string };
const EMPTY: Form = { label: '', city: '', address: '', contactName: '', contactPhone: '', openingHours: '', windowFrom: '', windowTo: '', instructions: '', kind: 'BOTH' };
const FIELDS = ['label', 'city', 'address', 'contactName', 'contactPhone', 'openingHours', 'windowFrom', 'windowTo', 'instructions'];
const fromPlace = (p: PlaceView): Form => ({ label: p.label ?? '', city: p.city ?? '', address: p.address ?? '', contactName: p.contactName ?? '', contactPhone: p.contactPhone ?? '', openingHours: p.openingHours ?? '', windowFrom: p.windowFrom ?? '', windowTo: p.windowTo ?? '', instructions: p.instructions ?? '', kind: p.kind ?? 'BOTH' });

/** Alta y edición de una dirección guardada (RN07 no aplica: la libreta es privada de la organización). */
function PlaceDialog({ place, cities, onClose, onSaved }: { place: PlaceView | null; cities: { code?: string; name?: string }[]; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<Form>(place ? fromPlace(place) : EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof Form) => (v: string) => { setF((s) => ({ ...s, [k]: v })); setErrors((e) => (e[k] ? { ...e, [k]: '' } : e)); };
  const cityOptions = cities.map((c) => ({ value: c.code ?? '', label: c.name ?? '' }));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const local: Record<string, string> = {};
    if (!f.label.trim()) local.label = 'Ponle un nombre a la dirección.';
    if (!f.city) local.city = 'Selecciona la ciudad.';
    if (f.address.trim().length < 3) local.address = 'Ingresa la dirección.';
    if (!!f.windowFrom !== !!f.windowTo) local.windowTo = 'Indica el inicio y el fin del horario, o ninguno.';
    setErrors(local);
    setFormError(null);
    const first = Object.keys(local)[0];
    if (first) { requestAnimationFrame(() => document.getElementById(`pl-${first}`)?.focus()); return; }
    setBusy(true);
    const body = { ...f, windowFrom: f.windowFrom || undefined, windowTo: f.windowTo || undefined, kind: f.kind as 'BOTH' };
    const { error } = place ? await actions.places.update({ id: place.id!, ...body }) : await actions.places.create(body);
    setBusy(false);
    if (error) {
      const r = toFormErrors(error, FIELDS);
      const key = Object.keys(r.errors)[0];
      if (key) { setErrors(r.errors); requestAnimationFrame(() => document.getElementById(`pl-${key}`)?.focus()); } else setFormError(r.formError);
      return;
    }
    toast.success(place ? 'Dirección actualizada' : 'Dirección guardada');
    onSaved();
  }

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={place ? 'Editar dirección' : 'Nueva dirección'} description="Es privada de tu empresa. El contacto y las instrucciones solo los verá el transportista que gane la solicitud." className="max-w-xl">
      <form onSubmit={submit} noValidate method="post" className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Nombre" htmlFor="pl-label" error={errors.label}><Input id="pl-label" autoComplete="off" maxLength={LIMITS.placeLabel} value={f.label} onChange={(e) => set('label')(e.target.value)} placeholder="Almacén Callao" aria-invalid={!!errors.label} /></Field>
          <Field label="Sirve para" htmlFor="pl-kind"><Select id="pl-kind" value={f.kind} onValueChange={set('kind')} options={KIND_OPTIONS} /></Field>
          <Field label="Ciudad" htmlFor="pl-city" error={errors.city}><Combobox id="pl-city" value={f.city} onValueChange={set('city')} options={cityOptions} placeholder="Selecciona la ciudad" searchPlaceholder="Buscar ciudad…" invalid={!!errors.city} /></Field>
          <Field label="Dirección" htmlFor="pl-address" error={errors.address}><Input id="pl-address" autoComplete="off" maxLength={LIMITS.originAddress} value={f.address} onChange={(e) => set('address')(e.target.value)} placeholder="Av. Argentina 1234" aria-invalid={!!errors.address} /></Field>
          <Field label="Nombre de contacto" htmlFor="pl-contactName" error={errors.contactName}><Input id="pl-contactName" autoComplete="off" maxLength={LIMITS.contactName} value={f.contactName} onChange={(e) => set('contactName')(e.target.value)} /></Field>
          <Field label="Teléfono de contacto" htmlFor="pl-contactPhone" error={errors.contactPhone}><Input id="pl-contactPhone" type="tel" inputMode="tel" autoComplete="off" maxLength={LIMITS.contactPhone} value={f.contactPhone} onChange={(e) => set('contactPhone')(e.target.value)} placeholder="987 654 321" aria-invalid={!!errors.contactPhone} /></Field>
          <Field label="Horario (texto)" htmlFor="pl-openingHours" hint="Se muestra tal cual. Ej.: Lun–Vie 8:00–17:00" className="sm:col-span-2"><Input id="pl-openingHours" autoComplete="off" maxLength={LIMITS.placeHours} value={f.openingHours} onChange={(e) => set('openingHours')(e.target.value)} /></Field>
          <Field label="Atienden desde" htmlFor="pl-windowFrom" hint="Ventana por omisión para precargar la solicitud."><TimeSelect id="pl-windowFrom" value={f.windowFrom} onValueChange={set('windowFrom')} /></Field>
          <Field label="Hasta" htmlFor="pl-windowTo" error={errors.windowTo}><TimeSelect id="pl-windowTo" value={f.windowTo} onValueChange={set('windowTo')} invalid={!!errors.windowTo} /></Field>
        </div>
        {(f.windowFrom || f.windowTo) && <button type="button" onClick={() => setF((s) => ({ ...s, windowFrom: '', windowTo: '' }))} className="text-xs font-medium text-petrol-700 hover:underline">Quitar horario</button>}
        <Field label="Instrucciones" htmlFor="pl-instructions" error={errors.instructions} hint={`Hasta ${LIMITS.stopInstructions} caracteres. Cómo llegar, a quién preguntar, qué llevar.`}><Textarea id="pl-instructions" maxLength={LIMITS.stopInstructions} value={f.instructions} onChange={(e) => set('instructions')(e.target.value)} /></Field>
        <div className="flex justify-end gap-2 border-t border-steel-200 pt-4">
          <Button type="button" variant="outline" onClick={onClose}>Cancelar</Button>
          <Button type="submit" loading={busy}>{place ? 'Guardar cambios' : 'Guardar dirección'}</Button>
        </div>
      </form>
    </Dialog>
  );
}

/** «Mis direcciones»: libreta de almacenes y direcciones frecuentes (ADMIN y OPS escriben; VIEWER solo lee). */
export default function PlacesManager({ initial, cities }: { initial: PlaceView[]; cities: { code?: string; name?: string }[] }) {
  const [places, setPlaces] = useState(initial);
  const [view, setView] = useState('active');
  const [q, setQ] = useState('');
  const [city, setCity] = useState('');
  const [editing, setEditing] = useState<PlaceView | 'new' | null>(null);

  async function reload() {
    const { data } = await actions.places.list({ includeArchived: true });
    if (data) setPlaces(data);
  }
  const cityOptions = useMemo(() => [...new Set(places.map((p) => p.city!))].map((c) => ({ value: c, label: CITY_LABELS[c] ?? c })).sort((a, b) => a.label.localeCompare(b.label, 'es')), [places]);
  const rows = places.filter((p) => (view === 'archived' ? p.archived : !p.archived))
    .filter((p) => !city || p.city === city)
    .filter((p) => { const t = q.trim().toLowerCase(); return !t || [p.label, p.address, p.contactName, p.city && CITY_LABELS[p.city]].join(' ').toLowerCase().includes(t); });
  const active = places.filter((p) => !p.archived).length, archived = places.length - active;
  const filtered = !!(q || city);

  async function archive(p: PlaceView) {
    if (!(await confirm({ title: `¿Archivar «${p.label}»?`, description: 'Dejará de ofrecerse al publicar. Las solicitudes que ya la usaron no cambian y podrás restaurarla cuando quieras.', confirmLabel: 'Archivar' }))) return;
    const { error } = await actions.places.archive({ id: p.id! });
    if (error) return void toast.error('No pudimos archivar la dirección', error.message);
    toast.success('Dirección archivada');
    await reload();
  }
  async function restore(p: PlaceView) {
    const { error } = await actions.places.restore({ id: p.id! });
    if (error) return void toast.error('No pudimos restaurar la dirección', error.message);
    toast.success('Dirección restaurada');
    await reload();
  }

  const addButton = <Can><Button onClick={() => setEditing('new')}><Plus />Nueva dirección</Button></Can>;
  if (places.length === 0) {
    return (
      <>
        <Can readOnly="No puedes agregar direcciones." noticeClassName="mb-3" fallback={<Card><EmptyState icon={MapPin} title="Aún no hay direcciones guardadas" description="Tu empresa todavía no guardó almacenes ni direcciones frecuentes." /></Card>}>
          <Card><EmptyState icon={MapPin} title="Guarda tus almacenes y direcciones frecuentes" description="Al publicar una solicitud las elegirás de una lista, con su contacto, horario e instrucciones ya cargados." action={addButton} /></Card>
        </Can>
        {editing && <PlaceDialog place={editing === 'new' ? null : editing} cities={cities} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); void reload(); }} />}
      </>
    );
  }

  return (
    <div className="min-w-0 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <QuickFilters label="Estado de la dirección" value={view} onValueChange={setView} options={[{ value: 'active', label: 'Activas', count: active }, { value: 'archived', label: 'Archivadas', count: archived }]} />
        {addButton}
      </div>
      <Can readOnly="No puedes agregar ni editar direcciones." noticeClassName="mb-1" />
      <Card className="min-w-0 overflow-hidden">
        <FilterBar count={rows.length} noun={['dirección', 'direcciones']} active={filtered} onClear={() => { setQ(''); setCity(''); }}>
          <Field label="Buscar" htmlFor="pl-q">
            <div className="relative"><Search className="pointer-events-none absolute left-3 top-3 size-4 text-ink-500" aria-hidden="true" /><Input id="pl-q" className="pl-9" placeholder="Nombre, dirección o contacto" value={q} onChange={(e) => setQ(e.target.value)} /></div>
          </Field>
          <Field label="Ciudad" htmlFor="pl-fcity"><Combobox id="pl-fcity" value={city} onValueChange={setCity} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
        </FilterBar>
        {rows.length === 0 ? (
          <EmptyState icon={filtered ? SearchX : MapPin} title={filtered ? 'No hay direcciones con estos filtros' : view === 'archived' ? 'No hay direcciones archivadas' : 'No hay direcciones activas'} description={filtered ? 'Prueba con otro texto o ciudad.' : undefined} />
        ) : (
          <Table label="Mis direcciones" stack>
            <thead><tr><Th>Dirección</Th><Th>Ciudad</Th><Th>Contacto</Th><Th>Horario</Th><Th>Sirve para</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>
            <tbody>
              {rows.map((p) => (
                <Tr key={p.id} data-testid="place-row">
                  <Td primary><div className="font-medium text-ink-900">{p.label}</div><div className="max-w-72 truncate text-xs text-ink-500" title={p.address}>{p.address}</div></Td>
                  <Td label="Ciudad">{CITY_LABELS[p.city!] ?? p.city}</Td>
                  <Td label="Contacto">{p.contactName || p.contactPhone ? <div>{p.contactName}<div className="font-mono text-xs text-ink-500">{p.contactPhone}</div></div> : <span className="text-ink-500">—</span>}</Td>
                  <Td label="Horario" className="font-mono text-xs">{p.openingHours || (p.windowFrom && p.windowTo ? `${p.windowFrom} – ${p.windowTo}` : '—')}</Td>
                  <Td label="Sirve para">{KIND_LABEL[p.kind!] ?? p.kind}{p.archived && <span className="ml-2 text-xs text-ink-500">Archivada</span>}</Td>
                  <Td actions className="text-right">
                    <Can>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" aria-label={`Acciones de ${p.label}`}><MoreHorizontal /></Button></DropdownMenuTrigger>
                        <DropdownMenuContent>
                          {!p.archived && <DropdownMenuItem onSelect={() => setEditing(p)}><Pencil />Editar</DropdownMenuItem>}
                          {p.archived ? <DropdownMenuItem onSelect={() => void restore(p)}><ArchiveRestore />Restaurar</DropdownMenuItem> : <DropdownMenuItem onSelect={() => void archive(p)}><Archive />Archivar</DropdownMenuItem>}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </Can>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      {editing && <PlaceDialog place={editing === 'new' ? null : editing} cities={cities} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); void reload(); }} />}
    </div>
  );
}
