import { useCallback, useId, useState } from 'react';
import { actions } from 'astro:actions';
import { Building2, FileText, ShieldAlert, Truck, TriangleAlert, Users } from 'lucide-react';
import { Card, CardBody, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input, Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { DescriptionList } from '@/components/ui/description-list';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { toast } from '@/components/ui/toaster';
import { KpiTile } from '@/components/domain/KpiTile';
import { StatusPill } from '@/components/domain/StatusPill';
import { RouteLine } from '@/components/domain/RouteLine';
import { TrustScore } from '@/components/domain/TrustScore';
import { splitActionError } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { CITY_LABELS, INCIDENT_CATEGORY_LABELS, VEHICLE_LABELS, formatDateTime, formatMoney, formatRelative } from '@/lib/format';
import { auditActionLabel } from '../audit-labels';
import SignalsList from './SignalsList';
import type { Organization360, OrgMember } from '../server';

const ROLE_LABEL: Record<string, string> = { ADMIN: 'Administrador', OPS: 'Operaciones', VIEWER: 'Lector', DRIVER: 'Conductor' };
const pct = (r?: number | null) => (r == null ? '—' : `${Math.round(r * 100)} %`);

/** Ficha 360 de una organización para el operador (roadmap 2.15). */
export default function Organization360({ initial }: { initial: Organization360 }) {
  const [org, setOrg] = useState(initial);
  const [suspending, setSuspending] = useState<OrgMember | null>(null);
  const [reactivating, setReactivating] = useState<OrgMember | null>(null);
  const [legalOpen, setLegalOpen] = useState(false);
  const reload = useCallback(async () => {
    const { data } = await actions.operator.organization360({ id: org.id! });
    if (data) setOrg(data);
  }, [org.id]);
  const c = org.counts ?? {};
  const shipmentsTotal = (c.shipmentsPending ?? 0) + (c.shipmentsInTransit ?? 0) + (c.shipmentsDelivered ?? 0) + (c.shipmentsCancelled ?? 0);
  const carrier = org.type === 'CARRIER';

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-2">
        <StatusPill kind="verification" status={org.verificationStatus ?? 'PENDING'} />
        <Badge tone={carrier ? 'petrol' : 'neutral'}>{carrier ? 'Transportista' : 'PYME'}</Badge>
        <span className="font-mono text-sm text-ink-700">RUC {org.ruc}</span>
        <span className="text-sm text-ink-500">Registrada {formatRelative(org.createdAt)}</span>
        {org.predecessorId && <a className="text-sm font-medium text-petrol-700 hover:underline" href={`/operador/organizaciones/${org.predecessorId}`}>Ver organización anterior</a>}
        {org.successorIds?.map((sid) => <a key={sid} className="text-sm font-medium text-petrol-700 hover:underline" href={`/operador/organizaciones/${sid}`}>Ver sucesora</a>)}
      </div>
      {!!org.signals?.length && (
        <Alert tone="warning" title={org.signals.length === 1 ? '1 señal por revisar' : `${org.signals.length} señales por revisar`}>
          Son indicios, no pruebas. Revísalas en la pestaña Resumen.
        </Alert>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiTile label="Envíos" value={shipmentsTotal} context={`${c.shipmentsDelivered ?? 0} entregados · ${c.shipmentsInTransit ?? 0} en tránsito`} icon={Truck} />
        <KpiTile label="Cancelación" value={pct(org.rates?.cancellation)} context={`${c.cancelledByThisOrg ?? 0} envíos cancelados por la empresa`} icon={ShieldAlert} tone={(org.rates?.cancellation ?? 0) >= 0.3 ? 'alert' : 'default'} />
        <KpiTile label={carrier ? 'Ofertas retiradas' : 'Solicitudes canceladas'} value={carrier ? pct(org.rates?.withdrawal) : pct(org.rates?.requestCancellation)} context={carrier ? `${c.bidsWithdrawn ?? 0} de ${c.bids ?? 0} ofertas` : `${c.requestsCancelled ?? 0} de ${c.requests ?? 0} solicitudes`} icon={FileText} tone={((carrier ? org.rates?.withdrawal : org.rates?.requestCancellation) ?? 0) >= 0.3 ? 'warn' : 'default'} />
        <KpiTile label="Incidencias activas" value={c.incidentsActive ?? 0} context={`${c.incidents ?? 0} en total · ${c.incidentsReported ?? 0} reportadas por ella`} icon={TriangleAlert} tone={c.incidentsActive ? 'warn' : 'default'} />
      </div>

      <Tabs defaultValue="resumen">
        <TabsList aria-label="Ficha de la organización">
          <TabsTrigger value="resumen">Resumen</TabsTrigger>
          <TabsTrigger value="usuarios"><Users className="mr-1.5 inline size-4 align-text-bottom max-sm:hidden" aria-hidden="true" />Usuarios</TabsTrigger>
          {carrier && <TabsTrigger value="flota">Flota</TabsTrigger>}
          <TabsTrigger value="envios">Envíos</TabsTrigger>
          <TabsTrigger value="incidencias">Incidencias</TabsTrigger>
          <TabsTrigger value="verificacion">Verificación</TabsTrigger>
          <TabsTrigger value="auditoria">Auditoría</TabsTrigger>
        </TabsList>

        <TabsContent value="resumen" className="space-y-6">
          <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
            <Card>
              <CardHeader><div><CardTitle>Señales</CardTitle><CardDescription>Datos repetidos con otras empresas y tasas fuera de lo normal.</CardDescription></div></CardHeader>
              <CardBody><SignalsList signals={org.signals ?? []} compact /></CardBody>
            </Card>
            <Card className="self-start">
              <CardHeader><CardTitle>Datos</CardTitle></CardHeader>
              <CardBody className="space-y-4">
                {org.reputation && <TrustScore score={org.reputation.trustScore ?? 75} reviews={org.reputation.totalReviews ?? 0} variant="meter" />}
                <DescriptionList cols={1} items={[
                  { label: 'Ciudad', value: CITY_LABELS[(org.city ?? '').toUpperCase()] ?? org.city },
                  { label: 'Teléfono', value: org.phone, mono: true },
                  { label: 'Vehículos', value: carrier ? `${c.vehicles ?? 0} activos · ${c.vehiclesArchived ?? 0} archivados` : undefined },
                  { label: 'Conductores', value: carrier ? String(c.drivers ?? 0) : undefined },
                  { label: 'Verificación', value: org.verificationUpdatedAt ? formatDateTime(org.verificationUpdatedAt) : 'Sin decisión', hint: org.verificationNote ?? undefined },
                ]} />
              </CardBody>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="usuarios">
          <Card>
            <Table label="Usuarios de la organización" stack>
              <thead><tr><Th>Persona</Th><Th>Rol</Th><Th>Estado</Th><Th>Último acceso</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>
              <tbody>
                {(org.users ?? []).map((u) => (
                  <Tr key={u.id} data-testid="org-user">
                    <Td primary><span className="font-medium text-ink-900">{u.fullName}</span><span className="block text-xs text-ink-500">{u.email}</span></Td>
                    <Td label="Rol">{ROLE_LABEL[u.role!] ?? u.role}</Td>
                    <Td label="Estado">
                      {u.operatorSuspendedAt ? <><Badge tone="red">Suspendido por OnCargo</Badge>{u.operatorSuspensionReason && <span className="mt-1 block text-xs text-ink-500">{u.operatorSuspensionReason}</span>}</>
                        : <StatusPill kind="account" status={u.invitationPending ? 'INVITED' : u.active ? 'ACTIVE' : 'INACTIVE'} />}
                    </Td>
                    <Td label="Último acceso">{u.lastLoginAt ? formatRelative(u.lastLoginAt) : 'Nunca'}</Td>
                    <Td actions>
                      {u.operatorSuspendedAt
                        ? <Button size="sm" variant="outline" onClick={() => setReactivating(u)}>Reactivar<span className="sr-only"> a {u.fullName}</span></Button>
                        : <Button size="sm" variant="destructive" onClick={() => setSuspending(u)}>Suspender<span className="sr-only"> a {u.fullName}</span></Button>}
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          </Card>
        </TabsContent>

        {carrier && (
          <TabsContent value="flota">
            <Card>
              <Table label="Flota" stack dense>
                <thead><tr><Th>Placa</Th><Th>Tipo</Th><Th>Estado</Th></tr></thead>
                <tbody>
                  {(org.vehicles ?? []).map((v) => (
                    <Tr key={v.id}>
                      <Td primary className="font-mono">{v.plate}</Td>
                      <Td label="Tipo">{VEHICLE_LABELS[v.type!] ?? v.type}</Td>
                      <Td label="Estado">{v.archived ? <Badge tone="neutral">Archivado</Badge> : <StatusPill status={v.status!} />}</Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            </Card>
          </TabsContent>
        )}

        <TabsContent value="envios">
          <Card>
            {org.recentShipments?.length ? (
              <Table label="Envíos recientes" stack dense>
                <thead><tr><Th>Envío</Th><Th>Contraparte</Th><Th>Precio final</Th><Th>Estado</Th></tr></thead>
                <tbody>
                  {org.recentShipments.map((s) => (
                    <Tr key={s.id}>
                      <Td primary>
                        <a className="font-mono font-medium text-petrol-700 hover:underline" href={`/operador/envios/${s.id}`}>{s.code ?? s.id!.slice(0, 8)}</a>
                        {s.originCity && s.destinationCity && <div className="mt-1"><RouteLine size="sm" origin={s.originCity} destination={s.destinationCity} /></div>}
                      </Td>
                      <Td label="Contraparte">{s.requesterOrgId === org.id ? s.carrierName : s.requesterName}</Td>
                      <Td label="Precio final" className="font-mono tabular-nums">{formatMoney(s.finalPrice)}</Td>
                      <Td label="Estado"><StatusPill status={s.status!} /></Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            ) : <p className="px-4 py-6 text-center text-sm text-ink-500">Sin envíos.</p>}
          </Card>
        </TabsContent>

        <TabsContent value="incidencias">
          <Card>
            {org.recentIncidents?.length ? (
              <Table label="Incidencias recientes" stack dense>
                <thead><tr><Th>Incidencia</Th><Th>Severidad</Th><Th>Plazo</Th><Th>Estado</Th></tr></thead>
                <tbody>
                  {org.recentIncidents.map((i) => (
                    <Tr key={i.id}>
                      <Td primary><a className="font-medium text-petrol-700 hover:underline" href={`/incidencias/${i.id}`}>{INCIDENT_CATEGORY_LABELS[i.category!] ?? i.category}</a>{i.reportedByOrgId === org.id && <span className="block text-xs text-ink-500">La reportó esta empresa</span>}</Td>
                      <Td label="Severidad">{i.severity && <StatusPill kind="severity" status={i.severity} />}</Td>
                      <Td label="Plazo"><span className={i.slaBreached ? 'font-semibold text-red-600' : ''}>{formatDateTime(i.dueAt)}</span></Td>
                      <Td label="Estado"><StatusPill status={i.status!} /></Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            ) : <p className="px-4 py-6 text-center text-sm text-ink-500">Sin incidencias.</p>}
          </Card>
        </TabsContent>

        <TabsContent value="verificacion" className="space-y-6">
          <Card>
            <CardHeader>
              <div><CardTitle>Razón social y RUC</CardTitle><CardDescription>Con documento de sustento. Un RUC nuevo crea una organización sucesora.</CardDescription></div>
              <Button variant="outline" onClick={() => setLegalOpen(true)}><Building2 aria-hidden="true" /> Cambiar razón social o RUC</Button>
            </CardHeader>
            <CardBody>
              {org.legalChanges?.length ? (
                <ul className="space-y-3">
                  {org.legalChanges.map((l) => (
                    <li key={l.id} className="rounded-card border border-steel-200 p-3 text-sm">
                      <p className="font-medium text-ink-900">{l.oldRuc !== l.newRuc ? `RUC ${l.oldRuc} → ${l.newRuc}` : `${l.oldLegalName} → ${l.newLegalName}`}</p>
                      <p className="text-ink-700">{l.reason}</p>
                      <p className="mt-1 text-xs text-ink-500">{formatDateTime(l.createdAt)} · <a className="text-petrol-700 hover:underline" href={`/operador/cambios-legales/${l.id}`} target="_blank" rel="noopener">{l.documentName}</a></p>
                    </li>
                  ))}
                </ul>
              ) : <p className="text-sm text-ink-500">Sin cambios registrados.</p>}
            </CardBody>
          </Card>
          <AuditCard title="Historial de verificación" rows={org.verificationHistory ?? []} empty="Sin decisiones registradas." />
        </TabsContent>

        <TabsContent value="auditoria">
          <AuditCard title="Actividad reciente" rows={org.activity ?? []} empty="Sin actividad registrada." />
        </TabsContent>
      </Tabs>

      <ReasonDialog
        open={!!suspending} onOpenChange={(o) => !o && setSuspending(null)} title={`Suspender a ${suspending?.fullName ?? ''}`} required tone="destructive"
        description="Cierra sus sesiones y no podrá entrar. Su administrador no puede reactivarlo." confirmLabel="Suspender usuario" maxLength={LIMITS.officeReason}
        onSubmit={async (reason) => {
          const { error } = await actions.operator.suspendUser({ id: suspending!.id!, reason });
          if (error) return splitActionError(error);
          toast.success('Usuario suspendido');
          setSuspending(null);
          reload();
          return null;
        }}
      />
      <ReasonDialog
        open={!!reactivating} onOpenChange={(o) => !o && setReactivating(null)} title={`Reactivar a ${reactivating?.fullName ?? ''}`}
        description="Podrá volver a iniciar sesión." confirmLabel="Reactivar usuario" maxLength={LIMITS.officeReason} hint="Opcional."
        onSubmit={async (reason) => {
          const { error } = await actions.operator.reactivateUser({ id: reactivating!.id!, reason: reason || undefined });
          if (error) return splitActionError(error);
          toast.success('Usuario reactivado');
          setReactivating(null);
          reload();
          return null;
        }}
      />
      {legalOpen && <LegalChangeDialog org={org} onClose={() => setLegalOpen(false)} />}
    </div>
  );
}

function AuditCard({ title, rows, empty }: { title: string; rows: NonNullable<Organization360['activity']>; empty: string }) {
  return (
    <Card>
      <CardHeader><CardTitle>{title}</CardTitle></CardHeader>
      {rows.length ? (
        <Table label={title} stack dense>
          <thead><tr><Th>Fecha</Th><Th>Acción</Th><Th>Quién</Th><Th>Detalle</Th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <Tr key={r.id}>
                <Td primary className="whitespace-nowrap">{formatDateTime(r.occurredAt)}</Td>
                <Td label="Acción">{auditActionLabel(r.action ?? undefined)}</Td>
                <Td label="Quién">{r.actorName ?? r.organizationName ?? 'Sistema'}</Td>
                <Td label="Detalle" className="max-w-80 truncate text-xs text-ink-500" title={r.detail ?? undefined}>{r.detail}</Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      ) : <CardBody><p className="text-sm text-ink-500">{empty}</p></CardBody>}
    </Card>
  );
}

function LegalChangeDialog({ org, onClose }: { org: Organization360; onClose: () => void }) {
  const uid = useId();
  const [legalName, setLegalName] = useState(org.legalName ?? '');
  const [ruc, setRuc] = useState(org.ruc ?? '');
  const [reason, setReason] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const newRuc = ruc.trim() !== org.ruc;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!/^\d{11}$/.test(ruc.trim())) next.ruc = 'El RUC tiene 11 dígitos.';
    if (legalName.trim().length < 3) next.legalName = 'Ingresa la razón social.';
    if (reason.trim().length < 3) next.reason = 'Indica el motivo (mínimo 3 caracteres).';
    if (!file) next.file = 'Adjunta la ficha RUC u otro sustento.';
    if (!newRuc && legalName.trim() === org.legalName) next.legalName = 'Cambia la razón social o el RUC.';
    setErrors(next);
    setFormError(null);
    if (Object.keys(next).length) return;
    const body = new FormData();
    if (legalName.trim() !== org.legalName) body.set('legalName', legalName.trim());
    if (newRuc) body.set('ruc', ruc.trim());
    body.set('reason', reason.trim());
    body.set('file', file!, file!.name);
    setBusy(true);
    const res = await fetch(`/operador/organizaciones/${org.id}/cambio-legal`, { method: 'POST', body }).catch(() => null);
    const payload = res ? await res.json().catch(() => null) : null;
    setBusy(false);
    if (!res || !res.ok) {
      const er = payload?.error;
      if (er?.field && ['legalName', 'ruc', 'reason', 'file'].includes(er.field)) setErrors({ [er.field]: er.message });
      else setFormError(er?.message ?? 'No pudimos registrar el cambio. Inténtalo de nuevo.');
      return;
    }
    const target = payload?.data?.organizationId as string | undefined;
    toast.success(payload?.data?.successorCreated ? 'Organización sucesora creada' : 'Razón social actualizada');
    window.location.href = `/operador/organizaciones/${target ?? org.id}`;
  }

  return (
    <Dialog open onOpenChange={(o) => !o && !busy && onClose()} title="Cambiar razón social o RUC"
      description={newRuc ? 'Con un RUC nuevo se crea una organización sucesora verificada: hereda la reputación, las personas y la flota. La actual queda suspendida.' : 'Corrige la razón social de esta organización.'}>
      <form onSubmit={submit} method="post" noValidate className="space-y-4">
        <Field label="Razón social" htmlFor={`${uid}-n`} error={errors.legalName} required>
          <Input id={`${uid}-n`} value={legalName} maxLength={200} onChange={(e) => setLegalName(e.target.value)} aria-invalid={!!errors.legalName} />
        </Field>
        <Field label="RUC" htmlFor={`${uid}-r`} error={errors.ruc} required>
          <Input id={`${uid}-r`} value={ruc} inputMode="numeric" maxLength={11} onChange={(e) => setRuc(e.target.value.replace(/\D/g, ''))} aria-invalid={!!errors.ruc} className="font-mono" />
        </Field>
        <Field label="Motivo" htmlFor={`${uid}-m`} error={errors.reason} required hint={`${reason.length}/${LIMITS.officeReason}`}>
          <Textarea id={`${uid}-m`} value={reason} rows={3} maxLength={LIMITS.officeReason} onChange={(e) => setReason(e.target.value)} aria-invalid={!!errors.reason} placeholder="Cambio de razón social inscrito en SUNAT" />
        </Field>
        <Field label="Documento de sustento" htmlFor={`${uid}-f`} error={errors.file} required hint="Ficha RUC o constancia. PDF, JPG o PNG.">
          <FileDropzone id={`${uid}-f`} file={file} onFileChange={setFile} disabled={busy} />
        </Field>
        {newRuc && <Alert tone="warning">Antes, la organización no debe tener envíos en curso, solicitudes publicadas, ofertas pendientes ni viajes abiertos.</Alert>}
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Volver</Button>
          <Button type="submit" loading={busy}>{busy ? 'Guardando…' : newRuc ? 'Crear sucesora' : 'Guardar razón social'}</Button>
        </div>
      </form>
    </Dialog>
  );
}
