import { useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Building2, Check, CircleCheck, Truck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { splitActionError } from '@/lib/action-error';
import { Field } from '@/components/ui/field';
import { Alert } from '@/components/ui/alert';
import { Combobox } from '@/components/ui/combobox';
import { Checkbox, RadioGroup, RadioGroupItem } from '@/components/ui/form-controls';
import { isValidRuc, rucKind } from '@/lib/ruc';
import { cn } from '@/lib/utils';

type City = { code?: string; name?: string };
type OrgType = 'PYME' | 'CARRIER';
/** Campos del formulario que la API puede señalar en `error.field`. */
const FIELDS = ['legalName', 'ruc', 'city', 'phone', 'fullName', 'email', 'password'];
/** Orden visual: sirve para llevar el foco al primer campo con error. */
const ORDER = ['legalName', 'ruc', 'fullName', 'email', 'password', 'acceptPrivacyPolicy'];

const TYPES: { v: OrgType; title: string; desc: string; perk: string; next: string; Icon: typeof Building2 }[] = [
  { v: 'PYME', title: 'Soy una empresa', desc: 'Necesito enviar carga', perk: 'Publica tu carga y compara ofertas.', next: 'Al terminar podrás publicar tu primera solicitud y recibir ofertas.', Icon: Building2 },
  { v: 'CARRIER', title: 'Soy transportista', desc: 'Quiero conseguir cargas', perk: 'Ve cargas compatibles y ofrece tu precio.', next: 'Al terminar registrarás tus vehículos y conductores para empezar a ofertar.', Icon: Truck },
];

const EMAIL_RE = /^\S+@\S+\.\S+$/;

/** Mensaje de validación de cada campo (vacío = sin error). Misma regla al salir del campo y al enviar. */
const RULES: Record<string, (v: string) => string> = {
  legalName: (v) => (v ? '' : 'Ingresa la razón social de tu empresa.'),
  ruc: (v) => (v.length === 0 || v.length < 11 ? 'Ingresa un RUC de 11 dígitos.' : isValidRuc(v) ? '' : 'El RUC no es válido. Revisa que los 11 dígitos estén bien escritos.'),
  fullName: (v) => (v ? '' : 'Ingresa tu nombre completo.'),
  email: (v) => (EMAIL_RE.test(v) ? '' : 'Ingresa un correo válido, como nombre@empresa.pe.'),
  password: (v) => (v.length >= 8 ? '' : 'Crea una contraseña de al menos 8 caracteres.'),
};

function passwordStrength(v: string): { level: 0 | 1 | 2 | 3; label: string } {
  if (!v) return { level: 0, label: '' };
  if (v.length < 8) return { level: 1, label: 'Muy corta' };
  const mixed = /[A-Za-z]/.test(v) && /\d/.test(v);
  if (v.length >= 12 && (mixed || /[^A-Za-z0-9]/.test(v))) return { level: 3, label: 'Segura' };
  return mixed || v.length >= 10 ? { level: 2, label: 'Aceptable' } : { level: 1, label: 'Débil: combina letras y números' };
}

const STEPS = [
  { id: 'tipo', short: 'Tipo', full: 'Tipo de cuenta' },
  { id: 'empresa', short: 'Empresa', full: 'Tu empresa' },
  { id: 'acceso', short: 'Acceso', full: 'Tu acceso' },
] as const;

/** Número del bloque; pasa a check cuando los campos obligatorios del bloque son válidos. */
function StepHeading({ n, id, title, desc, done }: { n: number; id: string; title: string; desc?: string; done: boolean }) {
  return (
    <div className="flex items-start gap-2.5">
      <span className={cn('mt-px grid size-6 shrink-0 place-items-center rounded-full font-mono text-xs font-semibold transition-colors', done ? 'bg-emerald-700 text-on-solid' : 'bg-petrol-50 text-petrol-700')} aria-hidden="true">
        {done ? <Check className="size-3.5" /> : n}
      </span>
      <div>
        <h2 id={id} className="text-sm font-semibold leading-6 text-ink-900">{title}</h2>
        {desc && <p className="hidden text-xs text-ink-500 @md:block">{desc}</p>}
      </div>
    </div>
  );
}

/** Indicador de avance: lista ordenada de 3 pasos + barra decorativa. El estado se anuncia en una región `polite` concisa. */
function Progress({ done, fraction }: { done: [boolean, boolean, boolean]; fraction: number }) {
  const count = done.filter(Boolean).length;
  const current = done.findIndex((d) => !d);
  return (
    <div className="sticky top-[4.0625rem] z-10 -mx-4 -mt-1 mb-5 border-b border-steel-200 bg-surface/95 px-4 pb-3 pt-2.5 backdrop-blur sm:-mx-7 sm:px-7">
      <ol aria-label="Avance del registro" className="grid grid-cols-3 gap-2">
        {STEPS.map((s, i) => (
          <li key={s.id} aria-current={i === current ? 'step' : undefined} className={cn('flex min-w-0 items-center gap-1.5 text-xs font-medium', done[i] ? 'text-emerald-700' : i === current ? 'text-ink-900' : 'text-ink-500')}>
            <span className={cn('grid size-5 shrink-0 place-items-center rounded-full border font-mono text-[11px] font-semibold transition-colors', done[i] ? 'border-emerald-700 bg-emerald-700 text-on-solid' : i === current ? 'border-petrol-700 text-petrol-700' : 'border-steel-200 text-ink-500')} aria-hidden="true">
              {done[i] ? <Check className="size-3" /> : i + 1}
            </span>
            <span className="truncate sm:hidden">{s.short}</span>
            <span className="hidden truncate sm:inline">{s.full}</span>
            <span className="sr-only">{done[i] ? ' (completo)' : ' (pendiente)'}</span>
          </li>
        ))}
      </ol>
      <div className="mt-2 h-1 overflow-hidden rounded-full bg-steel-200" aria-hidden="true">
        <div className="h-full rounded-full bg-emerald-500 transition-[width] duration-300 motion-reduce:transition-none" style={{ width: `${Math.round(fraction * 100)}%` }} />
      </div>
      <p className="sr-only" aria-live="polite">{count} de 3 pasos completos</p>
    </div>
  );
}

export default function RegisterForm({ cities, initialType }: { cities: City[]; initialType?: OrgType }) {
  const [type, setType] = useState<OrgType>(initialType ?? 'PYME');
  const [city, setCity] = useState('');
  const [ruc, setRuc] = useState('');
  const [password, setPassword] = useState('');
  const [accepted, setAccepted] = useState(false);
  // Valores de los campos no controlados, solo para calcular el avance (los inputs siguen sin `value`).
  const [vals, setVals] = useState({ legalName: '', fullName: '', email: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);

  const strength = passwordStrength(password);
  const rucOk = ruc.length === 11 && isValidRuc(ruc);
  const typeInfo = TYPES.find((t) => t.v === type)!;
  const legalOk = vals.legalName.trim().length > 0;
  const nameOk = vals.fullName.trim().length > 0;
  const emailOk = EMAIL_RE.test(vals.email.trim());
  const passOk = password.length >= 8;
  const stepDone: [boolean, boolean, boolean] = [true, legalOk && rucOk, nameOk && emailOk && passOk && accepted];
  const fraction = [true, legalOk, rucOk, nameOk, emailOk, passOk, accepted].filter(Boolean).length / 7;
  const onFormInput = (e: React.FormEvent<HTMLFormElement>) => {
    const t = e.target as HTMLInputElement;
    if (t.name === 'legalName' || t.name === 'fullName' || t.name === 'email') setVals((p) => ({ ...p, [t.name]: t.value }));
  };

  const setErr = (k: string, m: string) => setErrors((p) => ({ ...p, [k]: m }));
  /** Valida al salir del campo, y solo si el usuario escribió algo (los vacíos se marcan al enviar). */
  const onBlurValidate = (k: string) => (e: React.FocusEvent<HTMLInputElement>) => {
    const v = e.currentTarget.value.trim();
    if (v) setErr(k, RULES[k](k === 'password' ? e.currentTarget.value : v));
  };
  /** Al corregir, el error desaparece de inmediato. */
  const onChangeClear = (k: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormError(null);
    if (errors[k] && !RULES[k](k === 'password' ? e.currentTarget.value : e.currentTarget.value.trim())) setErr(k, '');
  };

  function focusFirst(errs: Record<string, string>) {
    const first = ORDER.find((k) => errs[k]);
    if (first) requestAnimationFrame(() => document.getElementById(first)?.focus());
  }

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy) return;
    setFormError(null);
    const f = new FormData(e.currentTarget);
    const g = (k: string) => String(f.get(k) ?? '').trim();
    const local: Record<string, string> = {};
    for (const k of ['legalName', 'fullName', 'email']) { const m = RULES[k](g(k)); if (m) local[k] = m; }
    const rm = RULES.ruc(ruc); if (rm) local.ruc = rm;
    const pm = RULES.password(String(f.get('password') ?? '')); if (pm) local.password = pm;
    if (!accepted) local.acceptPrivacyPolicy = 'Acepta la política de privacidad para continuar.';
    setErrors(local);
    if (Object.keys(local).length) { focusFirst(local); return; }

    setBusy(true);
    let res;
    try {
      res = await actions.auth.register({
        organizationType: type,
        legalName: g('legalName'),
        ruc,
        city: city || undefined,
        phone: g('phone') || undefined,
        fullName: g('fullName'),
        email: g('email'),
        password: String(f.get('password') ?? ''),
        acceptPrivacyPolicy: true,
      });
    } catch {
      setFormError('No pudimos crear la cuenta. Revisa tu conexión e inténtalo de nuevo.');
      setBusy(false);
      return;
    }
    const { data, error } = res;
    if (error) {
      // `field` viene de la API (ruc, email…): el error va bajo el campo; si no hay campo, banner sobre el botón.
      const { message: raw, field, code } = splitActionError(error);
      const message = code === 'LOGYX-NEG-001' ? 'La razón social no puede incluir teléfonos, correos ni enlaces. Escribe solo el nombre legal de tu empresa.' : raw;
      if (field && FIELDS.includes(field)) {
        setErrors({ [field]: message });
        requestAnimationFrame(() => document.getElementById(field)?.focus());
      } else {
        setFormError(message);
      }
      setBusy(false);
      return;
    }
    window.location.href = data.home;
  }

  return (
    <form ref={formRef} onSubmit={onSubmit} onInput={onFormInput} method="post" className="@container" noValidate>
      <Progress done={stepDone} fraction={fraction} />

      <div className="space-y-5">
        <section aria-labelledby="sec-tipo" className="space-y-2.5">
          <StepHeading n={1} id="sec-tipo" title="Tipo de cuenta" desc="¿Cómo usarás OnCargo?" done={stepDone[0]} />
          <RadioGroup value={type} onValueChange={(v) => setType(v as OrgType)} className="grid gap-2.5 @lg:grid-cols-2" aria-label="Tipo de cuenta">
            {TYPES.map(({ v, title, desc, perk, Icon }) => (
              <label key={v} htmlFor={`type-${v}`} className="flex cursor-pointer items-start gap-3 rounded-card border border-steel-200 bg-surface p-3.5 transition-colors hover:border-ink-400 has-[[data-state=checked]]:border-petrol-700 has-[[data-state=checked]]:bg-petrol-50 has-[[data-state=checked]]:shadow-sm has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-petrol-700">
                <span className={cn('grid size-10 shrink-0 place-items-center rounded-input transition-colors', type === v ? 'bg-petrol-700 text-on-solid' : 'bg-steel-100 text-ink-500')}><Icon className="size-5" aria-hidden="true" /></span>
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold text-ink-900">{title}</span>
                  <span className="block text-xs text-ink-700">{desc}</span>
                  <span className="mt-1 hidden text-xs text-ink-500 @md:block">{perk}</span>
                </span>
                <RadioGroupItem id={`type-${v}`} value={v} />
              </label>
            ))}
          </RadioGroup>
        </section>

        <section aria-labelledby="sec-empresa" className="space-y-3">
          <StepHeading n={2} id="sec-empresa" title="Tu empresa" desc="Validamos tu RUC y te mostramos ante otras empresas." done={stepDone[1]} />
          <div className="grid gap-x-3 gap-y-3 @md:grid-cols-6">
            <Field label="Razón social" htmlFor="legalName" error={errors.legalName} className="@md:col-span-4">
              <Input id="legalName" name="legalName" maxLength={200} autoComplete="organization" enterKeyHint="next" placeholder="Transportes Andinos S.A.C." aria-invalid={!!errors.legalName} aria-describedby={errors.legalName ? 'legalName-error' : undefined} onBlur={onBlurValidate('legalName')} onChange={onChangeClear('legalName')} />
            </Field>
            <Field label="RUC" htmlFor="ruc" error={errors.ruc} className="@md:col-span-2">
              <div className="relative">
                <Input
                  id="ruc"
                  name="ruc"
                  value={ruc}
                  inputMode="numeric"
                  autoComplete="off"
                  maxLength={11}
                  enterKeyHint="next"
                  placeholder="20123456789"
                  className={cn('font-mono tabular-nums', rucOk && 'pr-10')}
                  aria-invalid={!!errors.ruc}
                  aria-describedby={[errors.ruc ? 'ruc-error' : null, 'ruc-status'].filter(Boolean).join(' ')}
                  onChange={(e) => { const v = e.currentTarget.value.replace(/\D/g, '').slice(0, 11); setRuc(v); setFormError(null); if (errors.ruc && !RULES.ruc(v)) setErr('ruc', ''); }}
                  onBlur={(e) => { const v = e.currentTarget.value; if (v) setErr('ruc', RULES.ruc(v)); }}
                />
                {rucOk && <CircleCheck className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-emerald-700" aria-hidden="true" />}
              </div>
              <p id="ruc-status" className={cn('mt-1 flex items-center justify-between gap-2 text-xs', rucOk ? 'text-emerald-700' : 'text-ink-500')} aria-live="polite">
                <span>{rucOk ? `Dígito verificador correcto${rucKind(ruc) ? ` · ${rucKind(ruc)}` : ''}` : '11 dígitos'}</span>
                {!rucOk && <span className="font-mono tabular-nums" aria-hidden="true">{ruc.length}/11</span>}
              </p>
            </Field>
            <Field label="Ciudad" htmlFor="city" hint="Opcional" className="@md:col-span-3">
              <Combobox id="city" value={city} onValueChange={setCity} options={cities.map((c) => ({ value: c.code ?? '', label: c.name ?? '' }))} placeholder="Selecciona tu ciudad" searchPlaceholder="Buscar ciudad…" emptyText="No hay ciudades con ese nombre" />
            </Field>
            <Field label="Teléfono" htmlFor="phone" hint="Opcional" className="@md:col-span-3">
              <Input id="phone" name="phone" type="tel" autoComplete="tel-national" enterKeyHint="next" placeholder="987 654 321" aria-describedby="phone-hint" />
            </Field>
          </div>
        </section>

        <section aria-labelledby="sec-acceso" className="space-y-3">
          <StepHeading n={3} id="sec-acceso" title="Tu acceso" desc="Con este correo y contraseña ingresarás a OnCargo." done={stepDone[2]} />
          <div className="grid gap-x-3 gap-y-3 @md:grid-cols-2">
            <Field label="Tu nombre completo" htmlFor="fullName" error={errors.fullName} className="@md:col-span-2">
              <Input id="fullName" name="fullName" autoComplete="name" enterKeyHint="next" placeholder="María Quispe Huamán" aria-invalid={!!errors.fullName} aria-describedby={errors.fullName ? 'fullName-error' : undefined} onBlur={onBlurValidate('fullName')} onChange={onChangeClear('fullName')} />
            </Field>
            <Field label="Correo electrónico" htmlFor="email" error={errors.email}>
              <Input id="email" name="email" type="email" autoComplete="username" autoCapitalize="none" spellCheck={false} enterKeyHint="next" placeholder="nombre@empresa.pe" aria-invalid={!!errors.email} aria-describedby={errors.email ? 'email-error' : undefined} onBlur={onBlurValidate('email')} onChange={onChangeClear('email')} />
            </Field>
            <Field label="Contraseña" htmlFor="password" error={errors.password}>
              <PasswordInput
                id="password"
                name="password"
                autoComplete="new-password"
                enterKeyHint="done"
                maxLength={72}
                capsLockWarning
                aria-invalid={!!errors.password}
                aria-describedby={['password-hint', errors.password ? 'password-error' : null].filter(Boolean).join(' ')}
                onChange={(e) => { setPassword(e.currentTarget.value); onChangeClear('password')(e); }}
                onBlur={onBlurValidate('password')}
              />
              <div id="password-hint" className="mt-1.5" aria-live="polite">
                <div className="flex gap-1" aria-hidden="true">
                  {[1, 2, 3].map((i) => (
                    <span key={i} className={cn('h-1 flex-1 rounded-full transition-colors', strength.level >= i ? (strength.level === 1 ? 'bg-red-600' : strength.level === 2 ? 'bg-amber-500' : 'bg-emerald-500') : 'bg-steel-200')} />
                  ))}
                </div>
                <p className="mt-1 text-xs text-ink-500">
                  {strength.level ? <>Seguridad: <span className="font-medium text-ink-700">{strength.label}</span>. </> : null}Mínimo 8 caracteres.
                </p>
              </div>
            </Field>
          </div>

          <div className="rounded-input border border-steel-200 bg-steel-100/60 p-3">
            <div className="flex items-start gap-2.5">
              <Checkbox id="acceptPrivacyPolicy" checked={accepted} onCheckedChange={(c) => { setAccepted(c === true); if (c === true) setErr('acceptPrivacyPolicy', ''); }} aria-invalid={!!errors.acceptPrivacyPolicy} aria-describedby={errors.acceptPrivacyPolicy ? 'privacy-err' : undefined} className="mt-0.5" />
              <label htmlFor="acceptPrivacyPolicy" className="cursor-pointer text-[13px] leading-snug text-ink-700">
                Acepto la <a href="/privacidad" target="_blank" rel="noopener" className="font-medium text-petrol-700 underline underline-offset-2">política de privacidad</a> y el tratamiento de mis datos personales (Ley 29733).
              </label>
            </div>
            {errors.acceptPrivacyPolicy && <p id="privacy-err" role="alert" className="mt-2 pl-7 text-xs text-red-600">{errors.acceptPrivacyPolicy}</p>}
          </div>
        </section>

        <div className="space-y-2.5">
          {formError && <Alert tone="error">{formError}</Alert>}
          <Button type="submit" size="lg" className="w-full" loading={busy}>{busy ? 'Creando cuenta…' : 'Crear cuenta'}</Button>
          <div className="space-y-1 text-center text-xs text-ink-500">
            <p aria-live="polite">{typeInfo.next}</p>
            <p className="flex items-center justify-center gap-1.5"><Check className="size-3.5 text-emerald-700" aria-hidden="true" /> Validamos tu RUC al crear la cuenta</p>
          </div>
        </div>
      </div>
    </form>
  );
}
