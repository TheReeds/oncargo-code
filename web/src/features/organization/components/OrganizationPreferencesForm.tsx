import { useMemo, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Plus, Trash2 } from 'lucide-react';
import { Card, CardBody, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Combobox, MultiSelect } from '@/components/ui/combobox';
import { InlineLabel, Switch } from '@/components/ui/form-controls';
import { MoneyInput, NumberInput } from '@/components/ui/number-input';
import { TimeSelect } from '@/components/ui/time-select';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';
import { DescriptionList } from '@/components/ui/description-list';
import { Can } from '@/lib/permissions/react';
import { splitActionError } from '@/lib/action-error';
import { CARGO_LABELS, CITY_LABELS, formatMoney } from '@/lib/format';

export type Prefs = {
  cities?: string[];
  routes?: { origin?: string; destination?: string }[];
  cargoTypes?: string[];
  routeAlerts?: boolean;
  minPricePerKm?: number | null;
  defaultPickupFrom?: string | null;
  defaultPickupTo?: string | null;
  paymentTermsDays?: number | null;
};
type Option = { value: string; label: string };
type Route = { key: number; origin: string; destination: string };
type Errors = Partial<Record<'routes' | 'minPricePerKm' | 'defaultPickupFrom' | 'defaultPickupTo' | 'paymentTermsDays' | 'form', string>>;

const MAX_ROUTES = 20;
const hhmm = (t?: string | null) => (t ? t.slice(0, 5) : '');
const cityLabel = (c?: string) => (c ? CITY_LABELS[c] ?? c : '');

/**
 * `/organizacion/preferencias` (V24, 2.2): rutas, ciudades y tipos de carga de interés de la empresa. Transportista:
 * alertas por sus rutas (cargas nuevas, misma regla que las búsquedas guardadas) y precio mínimo por km. PYME: horario de
 * recojo por defecto y plazo de pago a sus transportistas. Lee cualquiera; escribe el ADMIN (`<Can MANAGE_ORGANIZATION>`).
 */
export default function OrganizationPreferencesForm({ initial, orgType, cities, cargoTypes }: { initial: Prefs; orgType: 'PYME' | 'CARRIER'; cities: Option[]; cargoTypes: Option[] }) {
  const carrier = orgType === 'CARRIER';
  const fromApi = (p: Prefs) => ({
    cities: p.cities ?? [],
    // Claves estables entre el SSR y la hidratación (los ids de los campos salen de aquí).
    routes: (p.routes ?? []).map((r, i) => ({ key: i, origin: r.origin ?? '', destination: r.destination ?? '' })) as Route[],
    cargoTypes: p.cargoTypes ?? [],
    routeAlerts: p.routeAlerts ?? true,
    minPricePerKm: p.minPricePerKm != null ? String(p.minPricePerKm) : '',
    from: hhmm(p.defaultPickupFrom),
    to: hhmm(p.defaultPickupTo),
    terms: p.paymentTermsDays != null ? String(p.paymentTermsDays) : '',
  });
  const nextKey = useRef(1000);
  const [saved, setSaved] = useState(initial);
  const [v, setV] = useState(() => fromApi(initial));
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  const set = (patch: Partial<typeof v>) => { setV((p) => ({ ...p, ...patch })); setErrors({}); };
  const setRoute = (key: number, patch: Partial<Route>) => set({ routes: v.routes.map((r) => (r.key === key ? { ...r, ...patch } : r)) });

  const summary = useMemo(() => {
    const routes = (saved.routes ?? []).map((r) => `${cityLabel(r.origin)} → ${cityLabel(r.destination)}`);
    const items = [
      { label: 'Rutas de interés', value: routes.length ? routes.join(' · ') : 'Ninguna', span: true },
      { label: 'Ciudades', value: saved.cities?.length ? saved.cities.map(cityLabel).join(', ') : 'Ninguna' },
      { label: 'Tipos de carga', value: saved.cargoTypes?.length ? saved.cargoTypes.map((c) => CARGO_LABELS[c] ?? c).join(', ') : 'Todos' },
    ];
    if (carrier) {
      items.push({ label: 'Alertas por mis rutas', value: saved.routeAlerts === false ? 'Apagadas' : 'Activas' });
      items.push({ label: 'Precio mínimo por km', value: saved.minPricePerKm != null ? formatMoney(saved.minPricePerKm) : 'Sin mínimo' });
    } else {
      items.push({ label: 'Horario de recojo', value: saved.defaultPickupFrom ? `${hhmm(saved.defaultPickupFrom)} – ${hhmm(saved.defaultPickupTo)}` : 'Sin definir' });
      items.push({ label: 'Plazo de pago', value: saved.paymentTermsDays != null ? `${saved.paymentTermsDays} días` : 'El de OnCargo' });
    }
    return items;
  }, [saved, carrier]);

  function validate(): Errors {
    const e: Errors = {};
    if (v.routes.some((r) => !r.origin || !r.destination)) e.routes = 'Elige el origen y el destino de cada ruta, o quítala.';
    else if (v.routes.some((r) => r.origin === r.destination)) e.routes = 'Una ruta necesita origen y destino distintos.';
    if (carrier && v.minPricePerKm && (Number(v.minPricePerKm) <= 0 || Number(v.minPricePerKm) > 100)) e.minPricePerKm = 'Ingresa un monto entre S/ 0.01 y S/ 100.00.';
    if (!carrier) {
      if (!!v.from !== !!v.to) e[v.from ? 'defaultPickupTo' : 'defaultPickupFrom'] = 'Elige la hora de inicio y la de fin.';
      else if (v.from && v.from >= v.to) e.defaultPickupTo = 'El recojo debe terminar después de empezar.';
      if (v.terms && Number(v.terms) > 120) e.paymentTermsDays = 'El plazo va de 0 a 120 días.';
    }
    return e;
  }

  async function save() {
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    const { data, error } = await actions.organization.updatePreferences({
      cities: v.cities,
      routes: v.routes.map(({ origin, destination }) => ({ origin, destination })),
      cargoTypes: v.cargoTypes,
      ...(carrier
        ? { routeAlerts: v.routeAlerts, minPricePerKm: v.minPricePerKm ? Number(v.minPricePerKm) : null }
        : { defaultPickupFrom: v.from || null, defaultPickupTo: v.to || null, paymentTermsDays: v.terms ? Number(v.terms) : null }),
    });
    setBusy(false);
    if (error || !data) {
      const { message, field } = error ? splitActionError(error) : { message: 'No pudimos guardar las preferencias.', field: null };
      setErrors(field && ['routes', 'minPricePerKm', 'defaultPickupFrom', 'defaultPickupTo', 'paymentTermsDays'].includes(field) ? { [field]: message } : { form: message });
      return;
    }
    setSaved(data as Prefs);
    setV(fromApi(data as Prefs));
    toast.success('Preferencias guardadas');
  }

  const readOnly = (
    <Card className="min-w-0" data-testid="org-preferences-summary">
      <CardHeader><CardTitle>Preferencias de la empresa</CardTitle><CardDescription>Solo un administrador las cambia.</CardDescription></CardHeader>
      <CardBody><DescriptionList cols={2} items={summary} /></CardBody>
    </Card>
  );

  return (
    <Can permission="MANAGE_ORGANIZATION" fallback={readOnly}>
      <div className="space-y-5" data-testid="org-preferences-form">
        <Card className="min-w-0">
          <CardHeader>
            <CardTitle>Rutas de interés</CardTitle>
            <CardDescription>{carrier ? 'Te avisamos de las cargas nuevas en estas rutas si alguno de tus vehículos puede ofertar.' : 'Las rutas que más despachas.'}</CardDescription>
          </CardHeader>
          <CardBody className="space-y-4">
            {v.routes.length === 0 && <p className="text-sm text-ink-500">Aún no declaras rutas.</p>}
            <ul className="space-y-3" aria-label="Rutas de interés">
              {v.routes.map((r, i) => (
                <li key={r.key} className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] sm:items-end">
                  <Field label={`Origen de la ruta ${i + 1}`} htmlFor={`route-${r.key}-origin`}>
                    <Combobox id={`route-${r.key}-origin`} value={r.origin} onValueChange={(o) => setRoute(r.key, { origin: o })} options={cities} placeholder="Ciudad de origen" invalid={!!errors.routes && (!r.origin || r.origin === r.destination)} />
                  </Field>
                  <Field label={`Destino de la ruta ${i + 1}`} htmlFor={`route-${r.key}-destination`}>
                    <Combobox id={`route-${r.key}-destination`} value={r.destination} onValueChange={(d) => setRoute(r.key, { destination: d })} options={cities} placeholder="Ciudad de destino" invalid={!!errors.routes && (!r.destination || r.origin === r.destination)} />
                  </Field>
                  <Button variant="ghost" size="icon" onClick={() => set({ routes: v.routes.filter((x) => x.key !== r.key) })}>
                    <Trash2 className="size-4" aria-hidden="true" /><span className="sr-only">Quitar la ruta {i + 1}</span>
                  </Button>
                </li>
              ))}
            </ul>
            {errors.routes && <p role="alert" className="text-xs text-red-700">{errors.routes}</p>}
            <Button variant="outline" onClick={() => set({ routes: [...v.routes, { key: nextKey.current++, origin: '', destination: '' }] })} disabled={v.routes.length >= MAX_ROUTES}>
              <Plus className="size-4" aria-hidden="true" /> Agregar ruta
            </Button>
            {v.routes.length >= MAX_ROUTES && <p className="text-xs text-ink-500">Llegaste al máximo de {MAX_ROUTES} rutas.</p>}
            {carrier && (
              <div className="flex items-start gap-3 border-t border-steel-200 pt-4">
                <Switch id="route-alerts" checked={v.routeAlerts} onCheckedChange={(c) => set({ routeAlerts: c })} className="mt-0.5" />
                <div>
                  <InlineLabel htmlFor="route-alerts">Alertas por mis rutas</InlineLabel>
                  <p className="text-xs text-ink-500">Llegan a todo tu equipo en «Alertas de carga». Quien ya tiene una búsqueda guardada igual recibe un solo aviso.</p>
                </div>
              </div>
            )}
          </CardBody>
        </Card>

        <Card className="min-w-0">
          <CardHeader><CardTitle>{carrier ? 'Cargas que te interesan' : 'Tu operación'}</CardTitle></CardHeader>
          <CardBody className="grid gap-4 sm:grid-cols-2">
            <Field label="Ciudades de interés" htmlFor="pref-cities">
              <MultiSelect id="pref-cities" value={v.cities} onValueChange={(c) => set({ cities: c })} options={cities} placeholder="Elige ciudades" />
            </Field>
            <Field label="Tipos de carga" htmlFor="pref-cargo" hint={carrier ? 'Sin elegir = todos.' : undefined}>
              <MultiSelect id="pref-cargo" value={v.cargoTypes} onValueChange={(c) => set({ cargoTypes: c })} options={cargoTypes} placeholder="Todos los tipos" />
            </Field>
            {carrier ? (
              <Field label="Precio mínimo por km" htmlFor="pref-min-km" hint="Opcional. Solo te avisamos si el precio sugerido llega a este monto por km." error={errors.minPricePerKm}>
                <MoneyInput id="pref-min-km" value={v.minPricePerKm} onValueChange={(m) => set({ minPricePerKm: m })} invalid={!!errors.minPricePerKm} />
              </Field>
            ) : (
              <>
                <fieldset className="sm:col-span-2">
                  <legend className="mb-2 text-sm font-medium text-ink-900">Horario de recojo por defecto</legend>
                  <div className="grid max-w-sm grid-cols-2 gap-3">
                    <Field label="Desde" htmlFor="pref-pickup-from" error={errors.defaultPickupFrom}>
                      <TimeSelect id="pref-pickup-from" value={v.from} onValueChange={(t) => set({ from: t })} invalid={!!errors.defaultPickupFrom} />
                    </Field>
                    <Field label="Hasta" htmlFor="pref-pickup-to" error={errors.defaultPickupTo}>
                      <TimeSelect id="pref-pickup-to" value={v.to} onValueChange={(t) => set({ to: t })} invalid={!!errors.defaultPickupTo} />
                    </Field>
                  </div>
                </fieldset>
                <Field label="Plazo de pago a transportistas" htmlFor="pref-terms" hint="Días desde la entrega. Vacío = el plazo de OnCargo." error={errors.paymentTermsDays}>
                  <NumberInput id="pref-terms" value={v.terms} onValueChange={(t) => set({ terms: t })} suffix="días" invalid={!!errors.paymentTermsDays} />
                </Field>
              </>
            )}
          </CardBody>
        </Card>

        {errors.form && <Alert tone="error">{errors.form}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={() => { setV(fromApi(saved)); setErrors({}); }} disabled={busy}>Descartar</Button>
          <Button onClick={save} loading={busy}>{busy ? 'Guardando…' : 'Guardar preferencias'}</Button>
        </div>
      </div>
    </Can>
  );
}
