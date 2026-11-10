import { useState } from 'react';
import { actions } from 'astro:actions';
import { Building2, Truck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Field, Input, Select } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { cn } from '@/lib/utils';

type City = { code?: string; name?: string };

export default function RegisterForm({ cities, initialType }: { cities: City[]; initialType?: 'PYME' | 'CARRIER' }) {
  const [type, setType] = useState<'PYME' | 'CARRIER'>(initialType ?? 'PYME');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setErrors({});
    const f = new FormData(e.currentTarget);
    const g = (k: string) => String(f.get(k) ?? '').trim();
    const local: Record<string, string> = {};
    if (!/^\d{11}$/.test(g('ruc'))) local.ruc = 'El RUC debe tener 11 dígitos';
    if (g('password').length < 8) local.password = 'La contraseña debe tener al menos 8 caracteres';
    if (f.get('acceptPrivacyPolicy') !== 'on') local.acceptPrivacyPolicy = 'Debes aceptar la política de privacidad para continuar';
    if (Object.keys(local).length) return setErrors(local);

    setBusy(true);
    const { data, error } = await actions.auth.register({
      organizationType: type,
      legalName: g('legalName'),
      ruc: g('ruc'),
      city: g('city') || undefined,
      phone: g('phone') || undefined,
      fullName: g('fullName'),
      email: g('email'),
      password: g('password'),
      acceptPrivacyPolicy: true,
    });
    if (error) {
      setError(error.message);
      setBusy(false);
      return;
    }
    window.location.href = data.home;
  }

  const opt = (v: 'PYME' | 'CARRIER', title: string, desc: string, Icon: typeof Building2) => (
    <label className={cn('flex cursor-pointer items-start gap-3 rounded-card border p-3 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-petrol-700', type === v ? 'border-petrol-700 bg-petrol-50' : 'border-steel-200 bg-surface hover:bg-steel-100')}>
      <input type="radio" name="orgType" className="sr-only" checked={type === v} onChange={() => setType(v)} />
      <Icon className={cn('mt-0.5 size-5', type === v ? 'text-petrol-700' : 'text-ink-500')} aria-hidden="true" />
      <span>
        <span className="block text-sm font-semibold">{title}</span>
        <span className="block text-xs text-ink-500">{desc}</span>
      </span>
    </label>
  );

  return (
    <form onSubmit={onSubmit} method="post" className="space-y-4" noValidate>
      {error && <Alert tone="error">{error}</Alert>}
      <fieldset>
        <legend className="mb-1.5 text-[13px] font-medium text-ink-700">Tipo de cuenta</legend>
        <div className="grid gap-2 sm:grid-cols-2">
          {opt('PYME', 'Soy una empresa', 'Necesito enviar carga', Building2)}
          {opt('CARRIER', 'Soy transportista', 'Quiero conseguir cargas', Truck)}
        </div>
      </fieldset>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Razón social" htmlFor="legalName" className="sm:col-span-2"><Input id="legalName" name="legalName" required autoComplete="organization" /></Field>
        <Field label="RUC" htmlFor="ruc" error={errors.ruc} hint="11 dígitos"><Input id="ruc" name="ruc" inputMode="numeric" maxLength={11} aria-invalid={!!errors.ruc} className="font-mono" required /></Field>
        <Field label="Ciudad" htmlFor="city">
          <Select id="city" name="city" defaultValue="">
            <option value="">Selecciona…</option>
            {cities.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
          </Select>
        </Field>
        <Field label="Teléfono" htmlFor="phone"><Input id="phone" name="phone" type="tel" autoComplete="tel" /></Field>
        <Field label="Tu nombre completo" htmlFor="fullName"><Input id="fullName" name="fullName" required autoComplete="name" /></Field>
        <Field label="Correo electrónico" htmlFor="email"><Input id="email" name="email" type="email" required autoComplete="email" /></Field>
        <Field label="Contraseña" htmlFor="password" error={errors.password} hint="Mínimo 8 caracteres"><Input id="password" name="password" type="password" autoComplete="new-password" aria-invalid={!!errors.password} required /></Field>
      </div>
      <div>
        <label className="flex items-start gap-2 text-sm text-ink-700">
          <input type="checkbox" name="acceptPrivacyPolicy" className="mt-0.5 size-4 accent-[#0B4F6C]" aria-describedby="privacy-err" />
          <span>Acepto la <a href="/privacidad" target="_blank" className="font-medium text-petrol-700 underline">política de privacidad</a> y el tratamiento de mis datos personales (Ley 29733).</span>
        </label>
        {errors.acceptPrivacyPolicy && <p id="privacy-err" role="alert" className="mt-1 text-xs text-red-600">{errors.acceptPrivacyPolicy}</p>}
      </div>
      <Button type="submit" className="w-full" disabled={busy}>{busy ? 'Creando cuenta…' : 'Crear cuenta'}</Button>
    </form>
  );
}
