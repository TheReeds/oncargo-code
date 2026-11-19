import { useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { BookmarkPlus, Calculator, ChevronUp, Copy, ChevronRight, FileText } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea, Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { DatePicker } from '@/components/ui/date-picker';
import { NumberInput, MoneyInput } from '@/components/ui/number-input';
import { TimeSelect } from '@/components/ui/time-select';
import { Checkbox, InlineLabel } from '@/components/ui/form-controls';
import { flash, toast } from '@/components/ui/toaster';
import { Alert } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { RouteLine } from '@/components/domain/RouteLine';
import { CITY_LABELS, formatDate, formatKm, formatMoney } from '@/lib/format';
import { splitActionError, toFormErrors, isVerificationFailure, type ActionFailure } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { VerificationBlock } from '@/components/domain/VerificationBlock';
import { SaveTemplateDialog } from '@/features/templates/components/SaveTemplateDialog';
import { Can } from '@/lib/permissions/react';
import { HolidayHint } from '@/features/config/components/HolidayHint';
import { cn } from '@/lib/utils';
import type { Quote } from '@/lib/api/server';
import type { PlaceView } from '@/features/places/server';

type Item = { code?: string; name?: string };
/** Valores del formulario. `initial` (de «Duplicar») llega con los mismos nombres; todo es texto salvo los ids de dirección. */
export type RequestFormValues = {
  originCity: string; originAddress: string; destinationCity: string; destinationAddress: string; cargoType: string; cargoDescription: string; weightKg: string; volumeM3: string; pickupDate: string;
  customerReference: string; declaredValue: string;
  pickupContactName: string; pickupContactPhone: string; pickupWindowFrom: string; pickupWindowTo: string; pickupInstructions: string;
  deliveryContactName: string; deliveryContactPhone: string; deliveryWindowFrom: string; deliveryWindowTo: string; deliveryInstructions: string;
  originPlaceId: string; destinationPlaceId: string;
};
type Values = RequestFormValues;
const EMPTY: Values = {
  originCity: '', originAddress: '', destinationCity: '', destinationAddress: '', cargoType: 'GENERAL', cargoDescription: '', weightKg: '', volumeM3: '', pickupDate: '',
  customerReference: '', declaredValue: '',
  pickupContactName: '', pickupContactPhone: '', pickupWindowFrom: '', pickupWindowTo: '', pickupInstructions: '',
  deliveryContactName: '', deliveryContactPhone: '', deliveryWindowFrom: '', deliveryWindowTo: '', deliveryInstructions: '',
  originPlaceId: '', destinationPlaceId: '',
};
/** `error.field` de la API → id del control del formulario. */
const FIELD_KEYS: Record<string, keyof Values> = Object.fromEntries(
  (['originCity', 'originAddress', 'destinationCity', 'destinationAddress', 'cargoType', 'cargoDescription', 'weightKg', 'volumeM3', 'pickupDate', 'customerReference', 'declaredValue',
    'pickupContactName', 'pickupContactPhone', 'pickupWindowFrom', 'pickupWindowTo', 'pickupInstructions', 'deliveryContactName', 'deliveryContactPhone', 'deliveryWindowFrom', 'deliveryWindowTo', 'deliveryInstructions'] as (keyof Values)[]).map((k) => [k, k]),
);
/** Los campos de «Contactos y horarios»: si fallan, se abre la sección. */
const CONTACT_KEYS: (keyof Values)[] = ['pickupContactName', 'pickupContactPhone', 'pickupWindowFrom', 'pickupWindowTo', 'pickupInstructions', 'deliveryContactName', 'deliveryContactPhone', 'deliveryWindowFrom', 'deliveryWindowTo', 'deliveryInstructions'];
/** Orden visual (para enfocar el primer campo inválido). */
const ORDER: (keyof Values)[] = ['originCity', 'originAddress', 'destinationCity', 'destinationAddress', 'cargoType', 'pickupDate', 'weightKg', 'volumeM3', 'customerReference', 'declaredValue', 'cargoDescription', ...CONTACT_KEYS];

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-1.5 text-sm">
      <dt className="text-ink-700">{label}</dt>
      <dd className="font-mono tabular-nums text-ink-900">{value}</dd>
    </div>
  );
}

function validate(v: Values): Partial<Record<keyof Values, string>> {
  const e: Partial<Record<keyof Values, string>> = {};
  if (!v.originCity) e.originCity = 'Selecciona la ciudad de origen.';
  if (!v.destinationCity) e.destinationCity = 'Selecciona la ciudad de destino.';
  if (v.originCity && v.originCity === v.destinationCity) e.destinationCity = 'Elige un destino distinto al origen.';
  if (v.originAddress.trim().length < 3) e.originAddress = 'Ingresa la dirección de recojo.';
  if (v.destinationAddress.trim().length < 3) e.destinationAddress = 'Ingresa la dirección de entrega.';
  if (v.cargoDescription.trim().length < 3) e.cargoDescription = 'Describe la carga.';
  if (!(Number(v.weightKg) > 0)) e.weightKg = 'Ingresa el peso en kg.';
  if (!v.pickupDate) e.pickupDate = 'Selecciona la fecha de recojo.';
  if (Number(v.declaredValue) < 0) e.declaredValue = 'El valor declarado debe ser mayor a 0.';
  for (const p of ['pickup', 'delivery'] as const) {
    const from = v[`${p}WindowFrom` as keyof Values], to = v[`${p}WindowTo` as keyof Values];
    if (!!from !== !!to) e[`${p}WindowTo` as keyof Values] = 'Indica el inicio y el fin de la ventana, o ninguno.';
    else if (from && to && from >= to) e[`${p}WindowTo` as keyof Values] = 'La ventana debe terminar después de empezar.';
    const phone = String(v[`${p}ContactPhone` as keyof Values]).replace(/[\s.\-()]/g, '').replace(/^\+?51(?=\d{9}$)/, '');
    if (phone && !/^\d{9}$/.test(phone)) e[`${p}ContactPhone` as keyof Values] = 'Ingresa un teléfono de 9 dígitos.';
  }
  return e;
}

export type DuplicateSource = { id: string; code?: string };
/** Plantilla de la que se abrió el formulario (`?plantilla=<id>`): permite actualizarla con lo que se edite aquí. */
export type TemplateSource = { id: string; name: string; invitePreferred?: boolean };

export default function RequestForm({ cities, cargoTypes, today, places = [], initial, duplicatedFrom, template }: {
  cities: Item[]; cargoTypes: Item[]; today: string;
  /** Direcciones guardadas de la organización (activas). */
  places?: PlaceView[];
  /** «Duplicar»: valores precargados; la fecha ya viene propuesta. */
  initial?: Partial<Values>;
  duplicatedFrom?: DuplicateSource;
  template?: TemplateSource;
}) {
  const [v, setV] = useState<Values>({ ...EMPTY, ...initial });
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [blocked, setBlocked] = useState<ActionFailure | null>(null);
  const [errors, setErrors] = useState<Partial<Record<keyof Values, string>>>({});
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false); // desglose plegable en móvil
  const [contactsOpen, setContactsOpen] = useState(() => CONTACT_KEYS.some((k) => !!initial?.[k]));
  const [dupes, setDupes] = useState<{ id?: string; code?: string }[] | null>(null);
  const [save, setSave] = useState({ origin: false, destination: false, originName: '', destinationName: '' });
  const [invitePreferred, setInvitePreferred] = useState(template?.invitePreferred ?? false);
  const [savingTemplate, setSavingTemplate] = useState(false);
  const [updatingTemplate, setUpdatingTemplate] = useState(false);
  const seq = useRef(0);
  /** Una clave por formulario: un doble clic o un reintento de red devuelven la misma solicitud, no otra. */
  const idemKey = useRef<string>('');

  const set = (k: keyof Values) => (val: string) => { setV((s) => ({ ...s, [k]: val })); setErrors((e) => (e[k] ? { ...e, [k]: '' } : e)); };
  /** Editar a mano la dirección (o la ciudad) suelta la dirección guardada elegida: ya no es esa. */
  const setAddress = (side: 'origin' | 'destination', k: 'Address' | 'City') => (val: string) => {
    setV((s) => ({ ...s, [`${side}${k}`]: val, [`${side}PlaceId`]: '' }));
    setErrors((e) => (e[`${side}${k}` as keyof Values] ? { ...e, [`${side}${k}`]: '' } : e));
  };
  /** Valida un campo al salir de él (no mientras escribe). */
  const touch = (k: keyof Values) => () => setErrors((e) => ({ ...e, [k]: validate(v)[k] ?? '' }));
  const cityOptions = cities.map((c) => ({ value: c.code ?? '', label: c.name ?? '' }));
  const cargoOptions = cargoTypes.map((c) => ({ value: c.code ?? '', label: c.name ?? '' }));
  const weight = Number(v.weightKg);
  const sameCity = v.originCity !== '' && v.originCity === v.destinationCity;
  const ready = !!(v.originCity && v.destinationCity && !sameCity && weight > 0);

  /** Elegir una dirección guardada copia dirección, contacto, ventana e instrucciones a ese extremo. */
  function pickPlace(side: 'origin' | 'destination', id: string) {
    const p = places.find((x) => x.id === id);
    if (!p) { setV((s) => ({ ...s, [`${side}PlaceId`]: '' })); return; }
    const k = side === 'origin' ? 'pickup' : 'delivery';
    setV((s) => ({
      ...s, [`${side}PlaceId`]: p.id!, [`${side}City`]: p.city ?? s[`${side}City` as keyof Values], [`${side}Address`]: p.address ?? '',
      [`${k}ContactName`]: p.contactName ?? '', [`${k}ContactPhone`]: p.contactPhone ?? '', [`${k}WindowFrom`]: p.windowFrom ?? '', [`${k}WindowTo`]: p.windowTo ?? '', [`${k}Instructions`]: p.instructions ?? '',
    }));
    setErrors((e) => ({ ...e, [`${side}City`]: '', [`${side}Address`]: '' }));
    if (p.contactName || p.contactPhone || p.windowFrom || p.instructions) setContactsOpen(true);
  }
  const placeOptions = (side: 'origin' | 'destination') => {
    const city = side === 'origin' ? v.originCity : v.destinationCity;
    const wanted = side === 'origin' ? 'PICKUP' : 'DELIVERY';
    return places
      .filter((p) => (p.kind === 'BOTH' || p.kind === wanted) && (!city || p.city === city))
      .map((p) => ({ value: p.id!, label: p.label ?? '', hint: CITY_LABELS[p.city ?? ''] ?? p.city }));
  };

  // Cotización en vivo con debounce (también al abrir un formulario precargado por «Duplicar»)
  useEffect(() => {
    if (!ready) {
      setQuote(null);
      setQuoteError(null);
      setQuoting(false);
      return;
    }
    const my = ++seq.current;
    setQuoting(true);
    const t = setTimeout(async () => {
      const { data, error } = await actions.requests.quote({ originCity: v.originCity, destinationCity: v.destinationCity, weightKg: weight, cargoType: v.cargoType as any });
      if (my !== seq.current) return;
      setQuoting(false);
      if (error) {
        setQuote(null);
        setQuoteError(splitActionError(error).message);
      } else {
        setQuote(data);
        setQuoteError(null);
      }
    }, 400);
    return () => clearTimeout(t);
  }, [v.originCity, v.destinationCity, v.cargoType, weight, ready]);

  const str = (x: string) => x.trim() || undefined;

  /** `force`: ya se avisó del posible duplicado y la persona quiere publicar igual. */
  async function publish(force = false) {
    setError(null);
    setBlocked(null);
    const errs = validate(v);
    setErrors(errs);
    const first = ORDER.find((k) => errs[k]);
    if (first) {
      if (CONTACT_KEYS.includes(first)) setContactsOpen(true);
      requestAnimationFrame(() => document.getElementById(first)?.focus());
      return;
    }

    setBusy(true);
    if (!force) {
      // «Ya publicaste una igual hoy»: misma ruta, tipo, peso y fecha en las últimas 24 h. Si la consulta falla, no estorba.
      const { data: similar } = await actions.requests.similar({ originCity: v.originCity, destinationCity: v.destinationCity, cargoType: v.cargoType as any, weightKg: weight, pickupDate: v.pickupDate });
      if (similar && similar.length > 0) {
        setDupes(similar);
        setBusy(false);
        requestAnimationFrame(() => document.getElementById('dup-warning')?.focus());
        return;
      }
    }
    setDupes(null);
    idemKey.current ||= crypto.randomUUID();
    const vol = Number(v.volumeM3);
    const declared = Number(v.declaredValue);
    const { data, error } = await actions.requests.create({
      originCity: v.originCity, originAddress: v.originAddress.trim(), destinationCity: v.destinationCity, destinationAddress: v.destinationAddress.trim(),
      cargoType: v.cargoType as any, cargoDescription: v.cargoDescription.trim(), weightKg: weight, volumeM3: vol > 0 ? vol : undefined, pickupDate: v.pickupDate,
      customerReference: str(v.customerReference), declaredValue: declared > 0 ? declared : undefined,
      pickupContactName: str(v.pickupContactName), pickupContactPhone: str(v.pickupContactPhone), pickupWindowFrom: v.pickupWindowFrom || undefined, pickupWindowTo: v.pickupWindowTo || undefined, pickupInstructions: str(v.pickupInstructions),
      deliveryContactName: str(v.deliveryContactName), deliveryContactPhone: str(v.deliveryContactPhone), deliveryWindowFrom: v.deliveryWindowFrom || undefined, deliveryWindowTo: v.deliveryWindowTo || undefined, deliveryInstructions: str(v.deliveryInstructions),
      originPlaceId: v.originPlaceId || undefined, destinationPlaceId: v.destinationPlaceId || undefined, idempotencyKey: idemKey.current,
      invitePreferred: invitePreferred || undefined,
    });
    if (error) {
      // `field` de la API → error bajo el campo (RN07: `LOGYX-NEG-001` en dirección o descripción); `ORG-003/004` → explicación; el resto → Alert.
      const r = toFormErrors(error, FIELD_KEYS);
      const key = Object.keys(r.errors)[0] as keyof Values | undefined;
      if (key) {
        setErrors(r.errors);
        if (CONTACT_KEYS.includes(key)) setContactsOpen(true);
        requestAnimationFrame(() => document.getElementById(key)?.focus());
      } else if (isVerificationFailure(r.failure)) {
        setBlocked(r.failure);
      } else {
        setError(r.formError);
      }
      setBusy(false);
      return;
    }
    // «Guardar esta dirección»: tras publicar; si falla no estorba (la solicitud ya existe).
    for (const side of ['origin', 'destination'] as const) {
      if (!save[side] || v[`${side}PlaceId`]) continue;
      const k = side === 'origin' ? 'pickup' : 'delivery';
      await actions.places.create({
        label: (side === 'origin' ? save.originName : save.destinationName).trim() || v[`${side}Address` as keyof Values].slice(0, 80), city: v[`${side}City` as keyof Values], address: v[`${side}Address` as keyof Values].trim(),
        contactName: str(v[`${k}ContactName` as keyof Values]), contactPhone: str(v[`${k}ContactPhone` as keyof Values]),
        windowFrom: (v[`${k}WindowFrom` as keyof Values] || undefined) as never, windowTo: (v[`${k}WindowTo` as keyof Values] || undefined) as never, instructions: str(v[`${k}Instructions` as keyof Values]),
        kind: side === 'origin' ? 'PICKUP' : 'DELIVERY',
      } as never);
    }
    flash('Solicitud publicada');
    window.location.href = `/pyme/solicitudes/${data.id}`;
  }

  /** Los datos del formulario como plantilla (sin fecha de recojo). `null` si falta algo: los errores quedan bajo cada campo. */
  function templateValues() {
    const errs = validate(v);
    delete errs.pickupDate;
    setErrors(errs);
    const first = ORDER.find((k) => errs[k]);
    if (first) {
      if (CONTACT_KEYS.includes(first)) setContactsOpen(true);
      requestAnimationFrame(() => document.getElementById(first)?.focus());
      return null;
    }
    const vol = Number(v.volumeM3), declared = Number(v.declaredValue);
    return {
      originCity: v.originCity, originAddress: v.originAddress.trim(), destinationCity: v.destinationCity, destinationAddress: v.destinationAddress.trim(),
      cargoType: v.cargoType as any, cargoDescription: v.cargoDescription.trim(), weightKg: weight, volumeM3: vol > 0 ? vol : undefined,
      customerReference: str(v.customerReference), declaredValue: declared > 0 ? declared : undefined,
      pickupContactName: str(v.pickupContactName), pickupContactPhone: str(v.pickupContactPhone), pickupWindowFrom: v.pickupWindowFrom || undefined, pickupWindowTo: v.pickupWindowTo || undefined, pickupInstructions: str(v.pickupInstructions),
      deliveryContactName: str(v.deliveryContactName), deliveryContactPhone: str(v.deliveryContactPhone), deliveryWindowFrom: v.deliveryWindowFrom || undefined, deliveryWindowTo: v.deliveryWindowTo || undefined, deliveryInstructions: str(v.deliveryInstructions),
      originPlaceId: v.originPlaceId || undefined, destinationPlaceId: v.destinationPlaceId || undefined,
    };
  }
  function openSaveTemplate() { if (templateValues()) setSavingTemplate(true); }
  async function saveTemplate(name: string, invite: boolean) {
    const values = templateValues();
    if (!values) return null;
    const { error } = await actions.templates.create({ ...values, name, invitePreferred: invite });
    if (error) return splitActionError(error);
    toast.success('Plantilla guardada', 'La encuentras en Plantillas.');
    return null;
  }
  async function updateTemplate() {
    const values = templateValues();
    if (!values || !template) return;
    setUpdatingTemplate(true);
    const { error } = await actions.templates.update({ ...values, id: template.id, name: template.name, invitePreferred });
    setUpdatingTemplate(false);
    if (error) { setError(splitActionError(error).message); return; }
    toast.success('Plantilla actualizada');
  }

  const onSubmit = (e: React.FormEvent<HTMLFormElement>) => { e.preventDefault(); void publish(false); };

  const err = (k: keyof Values) => errors[k] || undefined;

  /** Extremo de la ruta: ciudad, dirección guardada (si hay) y dirección. `side` define ids y textos. */
  const stopFields = (side: 'origin' | 'destination') => {
    const isO = side === 'origin';
    const cityKey = `${side}City` as keyof Values, addrKey = `${side}Address` as keyof Values, placeKey = `${side}PlaceId` as keyof Values;
    const options = placeOptions(side);
    const canSave = !v[placeKey] && v[addrKey].trim().length >= 3 && !!v[cityKey];
    const cityOpts = isO ? cityOptions : cityOptions.filter((c) => c.value !== v.originCity);
    const idBase = isO ? 'origin' : 'destination';
    return (
      <fieldset className="space-y-4">
        <legend className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-ink-500"><span className={isO ? 'size-2.5 rounded-full border-2 border-petrol-700' : 'size-2.5 bg-petrol-700'} aria-hidden="true" />{isO ? 'Recojo' : 'Entrega'}</legend>
        {places.length > 0 && (
          <Field label="Mis direcciones" htmlFor={`${idBase}Place`} hint="Elige una guardada para completar la dirección y el contacto.">
            <Combobox id={`${idBase}Place`} value={v[placeKey]} onValueChange={(id) => pickPlace(side, id)} options={options} placeholder={options.length ? 'Elegir una dirección guardada' : 'Sin direcciones para esta ciudad'} searchPlaceholder="Buscar dirección…" emptyText="Sin direcciones guardadas" allowClear disabled={options.length === 0} />
          </Field>
        )}
        <Field label={isO ? 'Ciudad de origen' : 'Ciudad de destino'} htmlFor={cityKey} error={err(cityKey)}>
          <Combobox id={cityKey} value={v[cityKey]} onValueChange={setAddress(side, 'City')} options={cityOpts} placeholder="Selecciona la ciudad" searchPlaceholder="Buscar ciudad…" invalid={!!err(cityKey)} />
        </Field>
        <Field label={isO ? 'Dirección de recojo' : 'Dirección de entrega'} htmlFor={addrKey} error={err(addrKey)}>
          <Input id={addrKey} name={addrKey} autoComplete="off" maxLength={LIMITS.originAddress} value={v[addrKey]} onChange={(e) => setAddress(side, 'Address')(e.target.value)} onBlur={touch(addrKey)} placeholder={isO ? 'Av. Argentina 1234, Callao' : 'Jr. Lima 456, Juliaca'} aria-invalid={!!err(addrKey)} />
        </Field>
        {canSave && (
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Checkbox id={`${idBase}Save`} checked={isO ? save.origin : save.destination} onCheckedChange={(c) => setSave((s) => ({ ...s, [side]: c === true }))} />
              <InlineLabel htmlFor={`${idBase}Save`}>Guardar esta dirección en «Mis direcciones»</InlineLabel>
            </div>
            {(isO ? save.origin : save.destination) && (
              <Field label="Nombre de la dirección" htmlFor={`${idBase}SaveName`}>
                <Input id={`${idBase}SaveName`} maxLength={LIMITS.placeLabel} value={isO ? save.originName : save.destinationName} onChange={(e) => setSave((s) => ({ ...s, [isO ? 'originName' : 'destinationName']: e.target.value }))} placeholder={isO ? 'Ej.: Almacén Callao' : 'Ej.: Tienda Juliaca'} />
              </Field>
            )}
          </div>
        )}
      </fieldset>
    );
  };

  /** Contacto, ventana e instrucciones de un extremo (privados hasta adjudicar). */
  const contactFields = (k: 'pickup' | 'delivery') => {
    const f = (x: string) => `${k}${x}` as keyof Values;
    const clearWindow = () => setV((s) => ({ ...s, [f('WindowFrom')]: '', [f('WindowTo')]: '' }));
    return (
      <fieldset className="space-y-4">
        <legend className="mb-3 text-xs font-semibold uppercase tracking-wide text-ink-500">{k === 'pickup' ? 'En el recojo' : 'En la entrega'}</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Nombre de contacto" htmlFor={f('ContactName')} error={err(f('ContactName'))}><Input id={f('ContactName')} autoComplete="off" maxLength={LIMITS.contactName} value={v[f('ContactName')]} onChange={(e) => set(f('ContactName'))(e.target.value)} aria-invalid={!!err(f('ContactName'))} /></Field>
          <Field label="Teléfono de contacto" htmlFor={f('ContactPhone')} error={err(f('ContactPhone'))}><Input id={f('ContactPhone')} type="tel" inputMode="tel" autoComplete="off" maxLength={LIMITS.contactPhone} value={v[f('ContactPhone')]} onChange={(e) => set(f('ContactPhone'))(e.target.value)} onBlur={touch(f('ContactPhone'))} placeholder="987 654 321" aria-invalid={!!err(f('ContactPhone'))} /></Field>
          <Field label="Atienden desde" htmlFor={f('WindowFrom')}><TimeSelect id={f('WindowFrom')} value={v[f('WindowFrom')]} onValueChange={set(f('WindowFrom'))} placeholder="Hora" /></Field>
          <Field label="Hasta" htmlFor={f('WindowTo')} error={err(f('WindowTo'))}><TimeSelect id={f('WindowTo')} value={v[f('WindowTo')]} onValueChange={set(f('WindowTo'))} placeholder="Hora" invalid={!!err(f('WindowTo'))} /></Field>
        </div>
        {(v[f('WindowFrom')] || v[f('WindowTo')]) && <button type="button" onClick={clearWindow} className="text-xs font-medium text-petrol-700 hover:underline">Quitar horario</button>}
        <Field label="Instrucciones" htmlFor={f('Instructions')} error={err(f('Instructions'))} hint={`Hasta ${LIMITS.stopInstructions} caracteres. Cómo llegar, a quién preguntar, qué documentos llevar.`}><Textarea id={f('Instructions')} maxLength={LIMITS.stopInstructions} value={v[f('Instructions')]} onChange={(e) => set(f('Instructions'))(e.target.value)} aria-invalid={!!err(f('Instructions'))} /></Field>
      </fieldset>
    );
  };

  return (
    <form onSubmit={onSubmit} noValidate className="grid items-start gap-5 pb-20 lg:grid-cols-[minmax(0,1fr)_360px] lg:pb-0">
      <div className="min-w-0 space-y-5">
        {duplicatedFrom && (
          <Alert tone="info" title="Solicitud prellenada">
            <Copy className="mr-1 inline size-3.5" aria-hidden="true" />Copiamos los datos de {duplicatedFrom.code ? <a className="font-medium underline" href={`/pyme/solicitudes/${duplicatedFrom.id}`}>{duplicatedFrom.code}</a> : 'tu solicitud anterior'}. Revisa la fecha de recojo, que ya propusimos, y publica.
          </Alert>
        )}
        {template && (
          <Alert tone="info" title="Plantilla cargada">
            <FileText className="mr-1 inline size-3.5" aria-hidden="true" />Usamos «{template.name}». Elige la fecha de recojo y publica; si cambias algo y quieres conservarlo, actualiza la plantilla.
          </Alert>
        )}
        {error && <Alert tone="error" title="No pudimos publicar la solicitud">{error}</Alert>}
        {blocked && <VerificationBlock failure={blocked} />}
        {dupes && (
          <div id="dup-warning" tabIndex={-1} role="alert" className="rounded-card border border-amber-500/40 bg-amber-50 p-4 text-sm text-ink-900 focus:outline-none">
            <p className="font-semibold">Ya publicaste una igual hoy</p>
            <p className="mt-1 text-ink-700">
              Hay una solicitud publicada en las últimas 24 horas con la misma ruta, tipo de carga, peso y fecha de recojo{dupes[0]?.code && <>: <a className="font-medium text-petrol-700 underline" href={`/pyme/solicitudes/${dupes[0].id}`}>{dupes[0].code}</a></>}. Si es otra carga distinta, puedes publicarla igual.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button type="button" size="sm" loading={busy} onClick={() => void publish(true)}>Publicar de todos modos</Button>
              {dupes[0]?.id && <Button asChild type="button" size="sm" variant="outline"><a href={`/pyme/solicitudes/${dupes[0].id}`}>Ver la anterior</a></Button>}
              <Button type="button" size="sm" variant="ghost" onClick={() => setDupes(null)}>Revisar mis datos</Button>
            </div>
          </div>
        )}
        <Card>
          <CardHeader><CardTitle>Ruta</CardTitle><span className="text-xs text-ink-500">¿De dónde a dónde va tu carga?</span></CardHeader>
          <CardBody>
            <div className="grid gap-x-6 gap-y-5 sm:grid-cols-2">
              {stopFields('origin')}
              {stopFields('destination')}
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader><CardTitle>Carga</CardTitle><span className="text-xs text-ink-500">Define el vehículo y el precio sugerido</span></CardHeader>
          <CardBody className="space-y-5">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Tipo de carga" htmlFor="cargoType">
                <Select id="cargoType" value={v.cargoType} onValueChange={set('cargoType')} options={cargoOptions} />
              </Field>
              <Field label="Fecha de recojo" htmlFor="pickupDate" error={err('pickupDate')}><DatePicker id="pickupDate" value={v.pickupDate} onValueChange={set('pickupDate')} min={today} invalid={!!err('pickupDate')} placeholder="Elige el día" /><HolidayHint date={v.pickupDate} city={v.originCity} /></Field>
              <Field label="Peso" htmlFor="weightKg" error={err('weightKg')}><div onBlur={touch('weightKg')}><NumberInput id="weightKg" value={v.weightKg} onValueChange={set('weightKg')} decimals={1} suffix="kg" placeholder="3,200" invalid={!!err('weightKg')} /></div></Field>
              <Field label="Volumen" htmlFor="volumeM3" hint="Opcional. Ayuda a elegir el vehículo."><NumberInput id="volumeM3" value={v.volumeM3} onValueChange={set('volumeM3')} decimals={1} suffix="m³" placeholder="12" /></Field>
              <Field label="Tu referencia" htmlFor="customerReference" error={err('customerReference')} hint="Opcional. N.º de orden de compra o de pedido. Solo lo ves tú y, cuando adjudiques, el transportista."><Input id="customerReference" name="customerReference" autoComplete="off" maxLength={LIMITS.customerReference} value={v.customerReference} onChange={(e) => set('customerReference')(e.target.value)} placeholder="OC 4500123" aria-invalid={!!err('customerReference')} /></Field>
              <Field label="Valor declarado de la carga" htmlFor="declaredValue" error={err('declaredValue')} hint="Opcional. El transportista responde según su póliza; tú declaras el valor."><MoneyInput id="declaredValue" value={v.declaredValue} onValueChange={set('declaredValue')} placeholder="18,500.00" invalid={!!err('declaredValue')} /></Field>
            </div>
            <Field label="Descripción de la carga" htmlFor="cargoDescription" error={err('cargoDescription')} hint={`Hasta ${LIMITS.cargoDescription} caracteres. No incluyas teléfonos ni correos: el contacto va en «Contactos y horarios» o por el chat.`}><Textarea id="cargoDescription" name="cargoDescription" maxLength={LIMITS.cargoDescription} value={v.cargoDescription} onChange={(e) => set('cargoDescription')(e.target.value)} onBlur={touch('cargoDescription')} placeholder="Ej.: 120 rollos de tela, paletizados" aria-invalid={!!err('cargoDescription')} /></Field>
          </CardBody>
        </Card>

        <Card>
          <details open={contactsOpen} onToggle={(e) => setContactsOpen((e.currentTarget as HTMLDetailsElement).open)} className="group">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-3 rounded-card px-5 py-4 [&::-webkit-details-marker]:hidden">
              <span>
                <span className="block text-sm font-semibold text-ink-900">Contactos y horarios <span className="font-normal text-ink-500">(opcional)</span></span>
                <span className="mt-0.5 block text-xs text-ink-500">A quién llamar, en qué horario te reciben y cómo llegar.</span>
              </span>
              <ChevronRight className="size-4 shrink-0 text-ink-500 transition-transform group-open:rotate-90" aria-hidden="true" />
            </summary>
            <div className="space-y-5 border-t border-steel-200 px-5 py-5">
              <p className="rounded-input bg-petrol-50 px-3 py-2 text-xs text-ink-700">Estos datos son privados: los transportistas <strong>no</strong> los ven al ofertar. Solo los verá el transportista que gane la solicitud (y su conductor) cuando la adjudiques.</p>
              <div className="grid gap-x-6 gap-y-5 sm:grid-cols-2">
                {contactFields('pickup')}
                {contactFields('delivery')}
              </div>
            </div>
          </details>
          <div className="flex items-center gap-2 border-t border-steel-200 px-5 py-3.5">
            <Checkbox id="invitePreferred" checked={invitePreferred} onCheckedChange={(c) => setInvitePreferred(c === true)} />
            <InlineLabel htmlFor="invitePreferred">Avisar a mis transportistas preferidos al publicar</InlineLabel>
          </div>
          <div className="flex flex-wrap items-center justify-end gap-2 border-t border-steel-200 bg-steel-100/50 px-5 py-3.5">
            <Button asChild variant="outline"><a href="/pyme/solicitudes">Volver</a></Button>
            <Can fallback={null}>
              {template
                ? <Button type="button" variant="outline" loading={updatingTemplate} onClick={() => void updateTemplate()}><BookmarkPlus />Actualizar plantilla</Button>
                : <Button type="button" variant="outline" onClick={openSaveTemplate}><BookmarkPlus />Guardar como plantilla</Button>}
            </Can>
            <Button type="submit" loading={busy && !dupes}>{busy && !dupes ? 'Publicando…' : 'Publicar solicitud'}</Button>
          </div>
        </Card>
      </div>

      {/* Cotización: panel lateral sticky en escritorio; en móvil, barra inferior con el precio y desglose plegable. */}
      <Card
        className={cn('max-lg:fixed max-lg:inset-x-0 max-lg:bottom-0 max-lg:z-30 max-lg:rounded-b-none max-lg:border-x-0 max-lg:border-b-0 max-lg:shadow-[0_-10px_28px_-14px_rgb(14_23_38/0.4)] lg:sticky lg:top-20')}
        style={{ paddingBottom: 'env(safe-area-inset-bottom, 0px)' }}
        aria-live="polite"
      >
        <CardHeader className="max-lg:py-3">
          <CardTitle className="flex items-center gap-2"><Calculator className="size-4 text-petrol-700" aria-hidden="true" /> Cotización estimada</CardTitle>
          <div className="flex items-center gap-2 lg:hidden">
            {quoting ? <Skeleton className="h-5 w-20" /> : quote && ready ? <span className="money after:content-[attr(data-price)]" data-price={formatMoney(quote.suggestedPrice)} /> : <span className="text-xs text-ink-500">Completa ruta y peso</span>}
            <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-controls="quote-body" className="grid size-8 place-items-center rounded-input text-ink-700 hover:bg-steel-100">
              <ChevronUp className={cn('size-4 transition-transform', open && 'rotate-180')} aria-hidden="true" />
              <span className="sr-only">{open ? 'Ocultar desglose' : 'Ver desglose'}</span>
            </button>
          </div>
        </CardHeader>
        <CardBody id="quote-body" className={cn('space-y-4', !open && 'max-lg:hidden')}>
          {v.originCity && v.destinationCity && !sameCity && (
            <div className="rounded-input bg-steel-100 p-3">
              <RouteLine size="sm" origin={v.originCity} destination={v.destinationCity} originSub={v.originAddress || undefined} destinationSub={v.destinationAddress || undefined} />
              {v.pickupDate && <p className="mt-2 border-t border-steel-200 pt-2 text-xs text-ink-500">Recojo el <span className="font-medium text-ink-700">{formatDate(v.pickupDate)}</span>{weight > 0 && <> · <span className="font-mono">{new Intl.NumberFormat('es-PE').format(weight)} kg</span></>}</p>}
            </div>
          )}
          {sameCity && <Alert tone="error">El origen y el destino deben ser distintos.</Alert>}
          {!ready && !sameCity && <p className="text-sm text-ink-500">Elige origen, destino y peso para ver el precio sugerido al instante.</p>}
          {quoteError && <Alert tone="error">{quoteError}</Alert>}
          {quoting && !quote && ready && <div className="space-y-2" role="status" aria-label="Calculando cotización">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-5 w-full" />)}</div>}
          {quote && ready && (
            <dl className={cn('transition-opacity', quoting && 'opacity-60')}>
              <Row label="Distancia" value={formatKm(quote.distanceKm)} />
              <Row label="Combustible" value={formatMoney(quote.fuelCost)} />
              <Row label="Transporte" value={formatMoney(quote.transportCost)} />
              <Row label="Peajes" value={formatMoney(quote.tollCost)} />
              <Row label="Manipuleo" value={formatMoney(quote.handlingFee)} />
              {/* dl > div > dt+dd: los pares van directos en el grupo (anidar otro div rompe la semántica de la lista) */}
              <div className="mt-3 grid grid-cols-[1fr_auto] items-baseline gap-x-3 gap-y-1.5 rounded-card border border-emerald-500/30 bg-emerald-50 p-3.5">
                <dt className="text-sm font-semibold text-ink-900">Precio sugerido</dt>
                <dd className="text-right font-mono text-2xl font-semibold tabular-nums text-emerald-700">{formatMoney(quote.suggestedPrice)}</dd>
                <dt className="border-t border-emerald-500/20 pt-1.5 text-sm text-ink-700">Piso de subasta</dt>
                <dd className="border-t border-emerald-500/20 pt-1.5 text-right font-mono text-sm tabular-nums text-ink-900">{formatMoney(quote.priceFloor)}</dd>
              </div>
            </dl>
          )}
          {quote && ready && <p className="text-xs text-ink-500">Los transportistas ofertarán por debajo del precio sugerido, nunca por debajo del piso.</p>}
        </CardBody>
      </Card>
      <SaveTemplateDialog open={savingTemplate} onOpenChange={setSavingTemplate} onSave={saveTemplate}
        suggested={v.originCity && v.destinationCity ? `${CITY_LABELS[v.originCity] ?? v.originCity} a ${CITY_LABELS[v.destinationCity] ?? v.destinationCity}${weight > 0 ? `, ${new Intl.NumberFormat('es-PE').format(weight)} kg` : ''}` : ''} />
    </form>
  );
}
