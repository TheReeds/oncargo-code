import { useState } from 'react';
import { actions } from 'astro:actions';
import { Building2, Truck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Combobox } from '@/components/ui/combobox';
import { Checkbox, RadioGroup, RadioGroupItem } from '@/components/ui/form-controls';
import { toast } from '@/components/ui/toaster';

type City = { code?: string; name?: string };
type OrgType = 'PYME' | 'CARRIER';

const TYPES: { v: OrgType; title: string; desc: string; Icon: typeof Building2 }[] = [
  { v: 'PYME', title: 'Soy una empresa', desc: 'Necesito enviar carga', Icon: Building2 },
  { v: 'CARRIER', title: 'Soy transportista', desc: 'Quiero conseguir cargas', Icon: Truck },
];

export default function RegisterForm({ cities, initialType }: { cities: City[]; initialType?: OrgType }) {
  const [type, setType] = useState<OrgType>(initialType ?? 'PYME');
  const [city, setCity] = useState('');
  const [accepted, setAccepted] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setErrors({});
    const f = new FormData(e.currentTarget);
    const g = (k: string) => String(f.get(k) ?? '').trim();
    const local: Record<string, string> = {};
    if (!g('legalName')) local.legalName = 'Ingresa la razón social de tu empresa.';
    if (!/^\d{11}$/.test(g('ruc'))) local.ruc = 'Ingresa un RUC de 11 dígitos.';
    if (!g('fullName')) local.fullName = 'Ingresa tu nombre completo.';
    if (!/^\S+@\S+\.\S+$/.test(g('email'))) local.email = 'Ingresa un correo válido, como nombre@empresa.pe.';
    if (g('password').length < 8) local.password = 'Crea una contraseña de al menos 8 caracteres.';
    if (!accepted) local.acceptPrivacyPolicy = 'Acepta la política de privacidad para continuar.';
    if (Object.keys(local).length) {
      setErrors(local);
      requestAnimationFrame(() => document.querySelector<HTMLElement>('form [aria-invalid="true"]')?.focus());
      return;
    }

    setBusy(true);
    let res;
    try {
      res = await actions.auth.register({
        organizationType: type,
        legalName: g('legalName'),
        ruc: g('ruc'),
        city: city || undefined,
        phone: g('phone') || undefined,
        fullName: g('fullName'),
        email: g('email'),
        password: g('password'),
        acceptPrivacyPolicy: true,
      });
    } catch {
      toast.error('No pudimos crear la cuenta. Revisa tu conexión e inténtalo de nuevo.');
      setBusy(false);
      return;
    }
    const { data, error } = res;
    if (error) {
      // La acción solo entrega el mensaje (sin `field`): lo asociamos al campo por su contenido.
      const m = error.message.toLowerCase();
      const field = /\bruc\b/.test(m) ? 'ruc' : /correo|email/.test(m) ? 'email' : null;
      if (field) {
        setErrors({ [field]: error.message });
        requestAnimationFrame(() => document.getElementById(field)?.focus());
      }
      toast.error(error.message);
      setBusy(false);
      return;
    }
    window.location.href = data.home;
  }

  return (
    <form onSubmit={onSubmit} method="post" className="space-y-6" noValidate>
      <fieldset>
        <legend className="mb-1.5 text-[13px] font-medium text-ink-700">Tipo de cuenta</legend>
        <RadioGroup value={type} onValueChange={(v) => setType(v as OrgType)} className="grid gap-3 sm:grid-cols-2" aria-label="Tipo de cuenta">
          {TYPES.map(({ v, title, desc, Icon }) => (
            <label key={v} htmlFor={`type-${v}`} className="flex cursor-pointer items-start gap-3 rounded-card border border-steel-200 bg-surface p-4 transition-colors hover:bg-steel-100 has-[[data-state=checked]]:border-petrol-700 has-[[data-state=checked]]:bg-petrol-50">
              <span className={`grid size-9 shrink-0 place-items-center rounded-input ${type === v ? 'bg-petrol-700 text-white' : 'bg-steel-100 text-ink-500'}`}><Icon className="size-5" aria-hidden="true" /></span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold text-ink-900">{title}</span>
                <span className="block text-xs text-ink-500">{desc}</span>
              </span>
              <RadioGroupItem id={`type-${v}`} value={v} />
            </label>
          ))}
        </RadioGroup>
      </fieldset>

      <section aria-labelledby="sec-empresa" className="space-y-4">
        <h2 id="sec-empresa" className="text-sm font-semibold text-ink-900">Datos de tu empresa</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Razón social" htmlFor="legalName" error={errors.legalName} className="sm:col-span-2">
            <Input id="legalName" name="legalName" autoComplete="organization" placeholder="Transportes Andinos S.A.C." aria-invalid={!!errors.legalName} />
          </Field>
          <Field label="RUC" htmlFor="ruc" error={errors.ruc} hint="11 dígitos">
            <Input id="ruc" name="ruc" inputMode="numeric" maxLength={11} placeholder="20123456789" className="font-mono tabular-nums" aria-invalid={!!errors.ruc} />
          </Field>
          <Field label="Ciudad" htmlFor="city">
            <Combobox id="city" value={city} onValueChange={setCity} options={cities.map((c) => ({ value: c.code ?? '', label: c.name ?? '' }))} placeholder="Selecciona tu ciudad" searchPlaceholder="Buscar ciudad…" emptyText="No hay ciudades con ese nombre" />
          </Field>
          <Field label="Teléfono" htmlFor="phone" hint="Opcional" className="sm:col-span-2">
            <Input id="phone" name="phone" type="tel" autoComplete="tel" placeholder="987 654 321" />
          </Field>
        </div>
      </section>

      <section aria-labelledby="sec-acceso" className="space-y-4">
        <h2 id="sec-acceso" className="text-sm font-semibold text-ink-900">Tu acceso</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Tu nombre completo" htmlFor="fullName" error={errors.fullName} className="sm:col-span-2">
            <Input id="fullName" name="fullName" autoComplete="name" placeholder="María Quispe Huamán" aria-invalid={!!errors.fullName} />
          </Field>
          <Field label="Correo electrónico" htmlFor="email" error={errors.email}>
            <Input id="email" name="email" type="email" autoComplete="email" placeholder="nombre@empresa.pe" aria-invalid={!!errors.email} />
          </Field>
          <Field label="Contraseña" htmlFor="password" error={errors.password} hint="Mínimo 8 caracteres">
            <Input id="password" name="password" type="password" autoComplete="new-password" aria-invalid={!!errors.password} />
          </Field>
        </div>
      </section>

      <div>
        <div className="flex items-start gap-2.5">
          <Checkbox id="acceptPrivacyPolicy" checked={accepted} onCheckedChange={(c) => setAccepted(c === true)} aria-invalid={!!errors.acceptPrivacyPolicy} aria-describedby={errors.acceptPrivacyPolicy ? 'privacy-err' : undefined} className="mt-0.5" />
          <label htmlFor="acceptPrivacyPolicy" className="cursor-pointer text-sm text-ink-700">
            Acepto la <a href="/privacidad" target="_blank" rel="noopener" className="font-medium text-petrol-700 underline underline-offset-2">política de privacidad</a> y el tratamiento de mis datos personales (Ley 29733).
          </label>
        </div>
        {errors.acceptPrivacyPolicy && <p id="privacy-err" role="alert" className="mt-1.5 text-xs text-red-600">{errors.acceptPrivacyPolicy}</p>}
      </div>

      <Button type="submit" size="lg" className="w-full" loading={busy}>{busy ? 'Creando cuenta…' : 'Crear cuenta'}</Button>
    </form>
  );
}
