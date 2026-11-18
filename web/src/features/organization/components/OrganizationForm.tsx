import { useMemo, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Lock, Save } from 'lucide-react';
import { Card, CardBody, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Combobox } from '@/components/ui/combobox';
import { Alert } from '@/components/ui/alert';
import { flash } from '@/components/ui/toaster';
import { toFormErrors } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { SUPPORT_EMAIL } from '@/lib/contact';
import { isValidRuc } from '@/lib/ruc';
import { CITY_LABELS } from '@/lib/format';
import type { CityItem } from '@/lib/api/server';
import type { OrganizationProfile } from '../server';

const PHONE_RE = /^[0-9+()\s-]{6,20}$/;
type Values = { legalName: string; ruc: string; city: string; phone: string; address: string };
type Errors = Partial<Record<keyof Values, string>>;

/**
 * Edición de los datos de la empresa (solo `MANAGE_ORGANIZATION`). Ciudad, teléfono y dirección siempre; razón social y RUC
 * únicamente con `legalDataEditable` (verificación PENDING o REJECTED): corregirlos desde REJECTED devuelve la empresa a revisión.
 */
export default function OrganizationForm({ profile, cities }: { profile: OrganizationProfile; cities: CityItem[] }) {
  const initial: Values = useMemo(() => ({ legalName: profile.legalName ?? '', ruc: profile.ruc ?? '', city: profile.city ?? '', phone: profile.phone ?? '', address: profile.address ?? '' }), [profile]);
  const [v, setV] = useState<Values>(initial);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const legal = !!profile.legalDataEditable;
  const rejected = profile.verificationStatus === 'REJECTED';
  const dirty = (Object.keys(initial) as (keyof Values)[]).some((k) => (legal || (k !== 'legalName' && k !== 'ruc')) && v[k].trim() !== initial[k]);
  const legalChanged = legal && (v.legalName.trim() !== initial.legalName || v.ruc !== initial.ruc);
  const refs: Record<keyof Values, React.RefObject<HTMLInputElement | null>> = { legalName: useRef(null), ruc: useRef(null), city: useRef(null), phone: useRef(null), address: useRef(null) };

  const cityOptions = useMemo(() => {
    const list = cities.map((c) => ({ value: c.code ?? '', label: c.name ?? '' }));
    // Ciudad guardada que no está en el catálogo (texto libre antiguo): se conserva como opción.
    if (initial.city && !list.some((o) => o.value === initial.city)) list.unshift({ value: initial.city, label: CITY_LABELS[initial.city] ?? initial.city });
    return list;
  }, [cities, initial.city]);

  const set = (k: keyof Values, value: string) => { setV((p) => ({ ...p, [k]: value })); setFormError(null); if (errors[k]) setErrors((p) => ({ ...p, [k]: undefined })); };

  function validate(): Errors {
    const e: Errors = {};
    if (legal) {
      if (v.legalName.trim().length < 2) e.legalName = 'Ingresa la razón social.';
      if (!/^\d{11}$/.test(v.ruc)) e.ruc = 'El RUC debe tener 11 dígitos.';
      else if (!isValidRuc(v.ruc)) e.ruc = 'Revisa el RUC: el dígito verificador no es correcto.';
    }
    if (v.phone.trim() && !PHONE_RE.test(v.phone.trim())) e.phone = 'Ingresa un teléfono válido, por ejemplo 987 654 321.';
    return e;
  }

  async function onSubmit(ev: React.FormEvent) {
    ev.preventDefault();
    if (busy || !dirty) return;
    setFormError(null);
    const e = validate();
    setErrors(e);
    const first = (['legalName', 'ruc', 'phone'] as const).find((k) => e[k]);
    if (first) { refs[first].current?.focus(); return; }
    // Solo se envía lo que cambió (null = sin cambio; "" borra ciudad, teléfono o dirección).
    const body: Record<string, string> = {};
    if (legal && v.legalName.trim() !== initial.legalName) body.legalName = v.legalName.trim();
    if (legal && v.ruc !== initial.ruc) body.ruc = v.ruc;
    if (v.city !== initial.city) body.city = v.city;
    if (v.phone.trim() !== initial.phone) body.phone = v.phone.trim();
    if (v.address.trim() !== initial.address) body.address = v.address.trim();
    setBusy(true);
    const { error } = await actions.organization.update(body);
    if (error) {
      const r = toFormErrors(error, ['legalName', 'ruc', 'city', 'phone', 'address']);
      setErrors(r.errors as Errors);
      setFormError(r.formError);
      setBusy(false);
      const f = (Object.keys(r.errors) as (keyof Values)[])[0];
      if (f) refs[f].current?.focus();
      return;
    }
    flash(legalChanged && rejected ? 'Datos actualizados. Tu empresa volvió a revisión.' : 'Datos de la empresa actualizados');
    window.location.reload();
  }

  return (
    <form onSubmit={onSubmit} noValidate method="post">
      <Card>
        <CardHeader>
          <div>
            <CardTitle>Editar datos de la empresa</CardTitle>
            <CardDescription>Lo que ven los transportistas y la PYME con la que negocias.</CardDescription>
          </div>
        </CardHeader>
        <CardBody className="@container space-y-4">
          {formError && <Alert tone="error" title="No pudimos guardar los cambios">{formError}</Alert>}
          {legal ? (
            <>
              {rejected && <Alert tone="warning" title="Si corriges la razón social o el RUC, tu empresa vuelve a revisión">El equipo de OnCargo volverá a validarla. Mientras tanto no podrás publicar ni ofertar.</Alert>}
              <div className="grid gap-4 @md:grid-cols-2">
                <Field label="Razón social" htmlFor="org-legal-name" error={errors.legalName} className="@md:col-span-2" hint={rejected ? undefined : 'Puedes corregirla mientras tu empresa está en revisión.'}>
                  <Input ref={refs.legalName} id="org-legal-name" value={v.legalName} maxLength={LIMITS.legalName} autoComplete="organization" aria-invalid={!!errors.legalName} onChange={(e) => set('legalName', e.currentTarget.value)} />
                </Field>
                <Field label="RUC" htmlFor="org-ruc" error={errors.ruc} hint="11 dígitos">
                  <Input ref={refs.ruc} id="org-ruc" value={v.ruc} inputMode="numeric" maxLength={11} autoComplete="off" className="font-mono tabular-nums" aria-invalid={!!errors.ruc} onChange={(e) => set('ruc', e.currentTarget.value.replace(/\D/g, '').slice(0, 11))} />
                </Field>
              </div>
            </>
          ) : (
            <div className="flex items-start gap-2.5 rounded-input border border-steel-200 bg-steel-100/60 px-3.5 py-3 text-sm text-ink-700" data-testid="legal-locked">
              <Lock className="mt-0.5 size-4 shrink-0 text-ink-500" aria-hidden="true" />
              <p>La razón social y el RUC no se pueden cambiar porque tu empresa ya pasó por verificación. Si hay un error, escribe a <a className="font-medium text-petrol-700 underline underline-offset-2" href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a> con tu RUC y lo revisamos.</p>
            </div>
          )}
          <div className="grid gap-4 @md:grid-cols-2">
            <Field label="Teléfono" htmlFor="org-phone" error={errors.phone} hint="Opcional. Solo lo ve el equipo de OnCargo.">
              <Input ref={refs.phone} id="org-phone" type="tel" value={v.phone} maxLength={20} autoComplete="tel" inputMode="tel" placeholder="987 654 321" aria-invalid={!!errors.phone} onChange={(e) => set('phone', e.currentTarget.value)} />
            </Field>
            <Field label="Ciudad" htmlFor="org-city" error={errors.city}>
              <Combobox id="org-city" value={v.city} onValueChange={(c) => set('city', c)} options={cityOptions} placeholder="Selecciona tu ciudad" searchPlaceholder="Buscar ciudad…" emptyText="No hay ciudades con ese nombre" allowClear invalid={!!errors.city} />
            </Field>
            <Field label="Dirección" htmlFor="org-address" error={errors.address} className="@md:col-span-2" hint="Opcional.">
              <Input ref={refs.address} id="org-address" value={v.address} maxLength={200} autoComplete="street-address" placeholder="Av. Argentina 1234, Callao" aria-invalid={!!errors.address} onChange={(e) => set('address', e.currentTarget.value)} />
            </Field>
          </div>
        </CardBody>
        <CardFooter>
          <p className="text-xs text-ink-500">{dirty ? 'Tienes cambios sin guardar.' : 'Sin cambios por guardar.'}</p>
          <div className="flex gap-2">
            <Button type="button" variant="outline" disabled={!dirty || busy} onClick={() => { setV(initial); setErrors({}); setFormError(null); }}>Descartar</Button>
            <Button type="submit" loading={busy} disabled={!dirty}><Save aria-hidden="true" /> {busy ? 'Guardando…' : 'Guardar cambios'}</Button>
          </div>
        </CardFooter>
      </Card>
    </form>
  );
}
