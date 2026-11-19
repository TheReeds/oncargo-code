import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { actions } from 'astro:actions';
import { ArrowLeftRight, ArrowRight, MapPin, Route as RouteIcon } from 'lucide-react';
import { Select } from '@/components/ui/select';
import { Field } from '@/components/ui/field';
import { NumberInput } from '@/components/ui/number-input';
import { formatMoney, formatNumber, CARGO_LABELS } from '@/lib/format';
import { BASE_EDGES, CITIES, MAP_SIZE, ROUTE_PATHS, type CityCode } from '../corridor-data';
import { splitActionError } from '@/lib/action-error';
import { CARGO_MULT, driveMinutes, formatDrive, km as kmBetween, quote, r2, routeKey, type CargoKey } from '../pricing';

/** Posición de la etiqueta de cada ciudad respecto del punto. */
const LABEL: Record<CityCode, { dx: number; dy: number; anchor: 'start' | 'end' | 'middle' }> = {
  LIMA: { dx: 12, dy: 4, anchor: 'start' }, ICA: { dx: -12, dy: 4, anchor: 'end' }, NAZCA: { dx: -12, dy: 4, anchor: 'end' },
  AREQUIPA: { dx: -12, dy: 4, anchor: 'end' }, MOQUEGUA: { dx: -12, dy: 4, anchor: 'end' }, TACNA: { dx: -12, dy: 4, anchor: 'end' },
  JULIACA: { dx: -12, dy: 4, anchor: 'end' }, PUNO: { dx: -12, dy: 4, anchor: 'end' }, CUSCO: { dx: 12, dy: 4, anchor: 'start' },
  ABANCAY: { dx: 0, dy: 22, anchor: 'middle' }, AYACUCHO: { dx: 12, dy: 4, anchor: 'start' },
};
const cityOptions = CITIES.map((c) => ({ value: c.code, label: c.label }));
const cargoOptions = (Object.keys(CARGO_MULT) as CargoKey[]).map((k) => ({ value: k, label: CARGO_LABELS[k] ?? k }));
const PRESETS: [CityCode, CityCode][] = [['LIMA', 'AREQUIPA'], ['LIMA', 'CUSCO'], ['LIMA', 'JULIACA'], ['AREQUIPA', 'TACNA']];
const byCode = Object.fromEntries(CITIES.map((c) => [c.code, c])) as Record<CityCode, (typeof CITIES)[number]>;
const VAT = 0.18;

/** Cotización de la API pública (`POST /public/pricing/quote`): mismos números que al publicar. */
type Remote = { fuel: number; transport: number; tolls: number; handling: number; suggested: number; floor: number; vat: number; net: number };

/** Mapa del corredor (rutas reales de routes.json) + cotizador de ejemplo que usa las reglas de tarifa de la plataforma. Solo cliente. */
export default function CorridorExplorer({ intro, aside }: { intro?: ReactNode; aside?: ReactNode }) {
  const [origin, setOrigin] = useState<CityCode>('LIMA');
  const [dest, setDest] = useState<CityCode>('AREQUIPA');
  const [kg, setKg] = useState('3200');
  const [cargo, setCargo] = useState<CargoKey>('GENERAL');
  const [motion, setMotion] = useState(false);
  const [remote, setRemote] = useState<{ key: string; q: Remote } | null>(null);
  const [remoteError, setRemoteError] = useState<string | null>(null);
  const seq = useRef(0);

  useEffect(() => {
    const m = matchMedia('(prefers-reduced-motion: no-preference)');
    setMotion(m.matches);
    const on = () => setMotion(m.matches);
    m.addEventListener('change', on);
    return () => m.removeEventListener('change', on);
  }, []);

  const key = routeKey(origin, dest);
  const reversed = key !== `${origin}-${dest}`;
  const distance = kmBetween(origin, dest);
  const tons = Math.max(0, Number(kg) || 0) / 1000;
  const q = useMemo(() => quote(distance, tons, cargo), [distance, tons, cargo]);
  // La estimación local responde al instante; la API pública (con los km por carretera y las tarifas vigentes) la confirma.
  const reqKey = `${origin}|${dest}|${kg}|${cargo}`;
  const live = remote && remote.key === reqKey ? remote.q : null;
  const shown = live ?? { ...q, vat: r2(q.suggested - q.suggested / (1 + VAT)), net: r2(q.suggested / (1 + VAT)) };
  const rows: [string, number][] = [['Combustible', shown.fuel], ['Transporte', shown.transport], ['Peajes', shown.tolls], ['Manipuleo', shown.handling]];

  useEffect(() => {
    const weight = Number(kg);
    if (!(weight >= 1 && weight <= 60000)) return;
    const mine = ++seq.current;
    const t = setTimeout(async () => {
      try {
        const { data, error } = await actions.landing.quote({ originCity: origin, destinationCity: dest, weightKg: weight, cargoType: cargo });
        if (mine !== seq.current) return;
        if (error || !data) { setRemote(null); setRemoteError(error ? splitActionError(error).message : null); return; }
        setRemoteError(null);
        setRemote({ key: reqKey, q: { fuel: data.fuelCost!, transport: data.transportCost!, tolls: data.tollCost!, handling: data.handlingFee!, suggested: data.suggestedPrice!, floor: data.priceFloor!, vat: data.suggestedVat!, net: data.suggestedNetOfVat! } });
      } catch {
        if (mine === seq.current) setRemote(null);
      }
    }, 400);
    return () => clearTimeout(t);
  }, [origin, dest, kg, cargo, reqKey]);

  const publishHref = `/registro?next=${encodeURIComponent(`/pyme/solicitudes/nueva?origen=${origin}&destino=${dest}&peso=${Math.round(Number(kg) || 0)}&tipo=${cargo}`)}`;

  const pickDest = (c: CityCode) => {
    if (c === dest) return;
    if (c === origin) { setOrigin(dest); setDest(c); return; }
    setDest(c);
  };
  const swap = () => { setOrigin(dest); setDest(origin); };

  return (
    <div className="grid gap-6 lg:grid-cols-[1.05fr_0.95fr] lg:gap-8">
      <div className="flex min-w-0 flex-col gap-6">
        {intro}
      <div className="relative overflow-hidden rounded-card border border-white/15 bg-chrome/40 p-3 backdrop-blur-sm sm:p-5">
        <div className="mk-grid absolute inset-0 opacity-70" aria-hidden="true" />
        <div className="relative flex items-center justify-between gap-3 px-1 pb-2 text-xs text-white/80">
          <span className="inline-flex items-center gap-1.5 font-semibold uppercase tracking-[0.12em]"><RouteIcon className="size-3.5 text-emerald-500" aria-hidden="true" /> Rutas reales del corredor</span>
          <span className="hidden sm:inline">Toca una ciudad para elegirla como destino</span>
        </div>
        <svg viewBox={`0 0 ${MAP_SIZE.w} ${MAP_SIZE.h}`} className="relative mx-auto block h-auto w-full max-w-[620px]" role="group" aria-label="Mapa esquemático del corredor Lima – Sierra Sur con 11 ciudades">
          <g fill="none" stroke="currentColor" className="text-white/25" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            {BASE_EDGES.map((e) => <path key={e} d={ROUTE_PATHS[e]} />)}
          </g>
          <path key={key} id="cx-route" className="cx-route" d={ROUTE_PATHS[key]} pathLength={1} fill="none" stroke="var(--color-emerald-500)" strokeWidth={5} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" />
          {motion && (
            <circle r={7} fill="white" stroke="var(--color-emerald-500)" strokeWidth={3} aria-hidden="true">
              <animateMotion key={key} dur="6s" repeatCount="indefinite" keyPoints={reversed ? '1;0' : '0;1'} keyTimes="0;1" calcMode="linear"><mpath href="#cx-route" /></animateMotion>
            </circle>
          )}
          {CITIES.map((c) => {
            const isO = c.code === origin, isD = c.code === dest, l = LABEL[c.code];
            return (
              <g key={c.code} className="cx-city" role="button" tabIndex={0} aria-label={`Elegir ${c.label} como destino`} aria-pressed={isD}
                onClick={() => pickDest(c.code)} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pickDest(c.code); } }}>
                <circle cx={c.x} cy={c.y} r={20} fill="transparent" />
                <circle className="cx-ring" cx={c.x} cy={c.y} r={13} fill="none" strokeWidth={2.5} />
                {isD ? <rect x={c.x - 7} y={c.y - 7} width={14} height={14} rx={2} fill="var(--color-emerald-500)" stroke="white" strokeWidth={2.5} />
                  : <circle cx={c.x} cy={c.y} r={isO ? 8 : 6} fill={isO ? "white" : 'var(--color-chrome)'} stroke={isO ? 'var(--color-emerald-500)' : 'white'} strokeWidth={isO ? 4 : 2.5} />}
                <text x={c.x + l.dx} y={c.y + l.dy}  textAnchor={l.anchor} className="cx-label" data-active={isO || isD ? '' : undefined} fill="white" stroke="var(--color-chrome)" strokeWidth={4} paintOrder="stroke" style={{ fontFamily: 'var(--font-sans)' }}>{c.label}</text>
              </g>
            );
          })}
        </svg>
        <p className="relative mt-1 flex flex-wrap items-center justify-between gap-2 px-1 text-xs text-white/80">
          <span>Trazado: OSRM · © OpenStreetMap contributors</span>
          <span className="mk-num">{formatNumber(distance, 1)} km · {formatDrive(driveMinutes(origin, dest))}</span>
        </p>
      </div>
      </div>

      <div className="flex min-w-0 flex-col justify-between gap-6">
        {aside}
        <div>
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-white/75">Rutas frecuentes</p>
          <ul className="grid grid-cols-2 gap-2">
            {PRESETS.map(([a, b]) => {
              const on = a === origin && b === dest;
              return (
                <li key={a + b}>
                  <button type="button" aria-pressed={on} onClick={() => { setOrigin(a); setDest(b); }} className={'flex w-full flex-col items-start justify-between gap-0.5 rounded-input border px-3 py-2 text-left text-sm sm:flex-row sm:items-center sm:gap-2 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white ' + (on ? 'border-emerald-500 bg-emerald-500/15 text-white' : 'border-white/20 bg-chrome/40 text-white hover:border-white/40 hover:bg-chrome/60')}>
                    <span className="font-medium">{byCode[a].label} → {byCode[b].label}</span>
                    <span className="mk-num text-xs text-white/80">{formatNumber(kmBetween(a, b), 0)} km</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      <div className="relative z-10 rounded-card border border-steel-200 bg-surface p-5 text-ink-900 shadow-2xl sm:p-6 lg:-mb-28">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-semibold tracking-tight">Cotizador del corredor</h3>
            <p className="mt-0.5 text-xs text-ink-500">Cuánto sale mover tu carga, antes de crear tu cuenta.</p>
          </div>
          <span className={'shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ' + (live ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700')}>{live ? 'Cotización de referencia' : 'Estimación'}</span>
        </div>

        <div className="mt-4 grid grid-cols-[1fr_auto_1fr] items-end gap-2">
          <Field label="Origen" htmlFor="cx-origin"><Select id="cx-origin" value={origin} onValueChange={(v) => (v === dest ? swap() : setOrigin(v as CityCode))} options={cityOptions} /></Field>
          <button type="button" onClick={swap} aria-label="Invertir origen y destino" className="mb-0.5 grid size-9 place-items-center rounded-input border border-steel-200 text-ink-700 transition-colors hover:bg-steel-100"><ArrowLeftRight className="size-4" aria-hidden="true" /></button>
          <Field label="Destino" htmlFor="cx-dest"><Select id="cx-dest" value={dest} onValueChange={(v) => (v === origin ? swap() : setDest(v as CityCode))} options={cityOptions} /></Field>
        </div>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <Field label="Peso" htmlFor="cx-kg"><NumberInput id="cx-kg" value={kg} onValueChange={setKg} suffix="kg" /></Field>
          <Field label="Tipo de carga" htmlFor="cx-cargo"><Select id="cx-cargo" value={cargo} onValueChange={(v) => setCargo(v as CargoKey)} options={cargoOptions} /></Field>
        </div>

        <dl className="mt-4 divide-y divide-steel-200 rounded-input border border-steel-200 px-3.5 text-sm">
          {rows.map(([k, v]) => <div key={k} className="flex items-center justify-between py-1.5"><dt className="text-ink-700">{k}</dt><dd className="mk-num text-ink-900">{formatMoney(v)}</dd></div>)}
          <div className="flex items-center justify-between py-2.5"><dt className="font-semibold">Precio sugerido</dt><dd className="money text-xl" aria-live="polite">{formatMoney(shown.suggested)}</dd></div>
          <div className="flex items-center justify-between py-1.5"><dt className="text-ink-700">Incluye IGV <span className="text-ink-500">(18 %)</span></dt><dd className="mk-num text-ink-900">{formatMoney(shown.vat)}</dd></div>
          <div className="flex items-center justify-between py-1.5"><dt className="text-ink-700">Piso de la subasta <span className="text-ink-500">(75 %)</span></dt><dd className="mk-num text-ink-900">{formatMoney(shown.floor)}</dd></div>
        </dl>
        {remoteError && <p className="mt-2 text-xs text-amber-700" role="status">{remoteError} Mostramos una estimación con las tarifas del corredor.</p>}
        <p className="sr-only" role="status">{`${byCode[origin].label} a ${byCode[dest].label}: ${formatNumber(distance, 1)} kilómetros, precio sugerido ${formatMoney(shown.suggested)}, IGV incluido.`}</p>
        <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="max-w-[30ch] text-xs text-ink-500"><MapPin className="mr-1 inline size-3 -translate-y-px" aria-hidden="true" />Precio con IGV y km por carretera. Publicar y comparar ofertas no tiene costo para tu empresa. <a href="/precios" className="font-medium text-petrol-700 underline underline-offset-2">Ver precios</a></p>
          <a href={publishHref} className="inline-flex h-11 items-center justify-center gap-2 whitespace-nowrap rounded-input bg-petrol-700 px-5 text-sm font-semibold text-on-solid transition-colors hover:bg-petrol-800">Publicar esta carga <ArrowRight className="size-4 shrink-0" aria-hidden="true" /></a>
        </div>
      </div>
      </div>
    </div>
  );
}
