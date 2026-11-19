import { useState } from 'react';
import { SearchX, Truck, Plus, MoreHorizontal, Download, Trash2, Pencil, Gavel, PackageCheck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input, Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Combobox, MultiSelect } from '@/components/ui/combobox';
import { DatePicker, DateRangePicker, type DateRangeValue } from '@/components/ui/date-picker';
import { MoneyInput, NumberInput } from '@/components/ui/number-input';
import { Checkbox, InlineLabel, RadioGroup, RadioGroupItem, Switch } from '@/components/ui/form-controls';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Card, CardBody } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Tooltip } from '@/components/ui/tooltip';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Pagination } from '@/components/ui/pagination';
import { Skeleton, SkeletonRows } from '@/components/ui/skeleton';
import { Avatar } from '@/components/ui/avatar';
import { Kbd } from '@/components/ui/kbd';
import { Dialog } from '@/components/ui/dialog';
import { Sheet } from '@/components/ui/sheet';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { StatusPill } from '@/components/domain/StatusPill';
import { RouteLine } from '@/components/domain/RouteLine';
import { KpiTile, KpiGrid } from '@/components/domain/KpiTile';
import { DescriptionList } from '@/components/ui/description-list';
import { QuickFilters } from '@/components/ui/quick-filters';
import { ExportButton } from '@/components/ui/export-button';
import { CardFooter, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { SkeletonList } from '@/components/ui/skeleton';
import { EmptyState } from '@/components/domain/EmptyState';
import { Countdown } from '@/components/domain/Countdown';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { StarRating } from '@/components/ui/star-rating';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { TrustScore } from '@/components/domain/TrustScore';
import { PasswordInput } from '@/components/ui/password-input';
import { FilterBar } from '@/components/ui/filter-bar';
import { SearchInput } from '@/components/ui/search-input';
import { Timeline } from '@/components/ui/timeline';
import { TimeSelect } from '@/components/ui/time-select';
import { CapacityBar } from '@/components/domain/CapacityBar';
import { AuthCard } from '@/components/ui/auth-card';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import VerificationBanner from '@/components/domain/VerificationBanner';
import { VerificationBlock } from '@/components/domain/VerificationBlock';
import { UnfitList } from '@/features/fleet/components/UnfitList';
import { partitionFleet } from '@/features/fleet/compatibility';
import ThemeToggle from '@/components/ui/theme-toggle';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';
import { RoleChoice, type AssignableRole } from '@/features/organization/components/RoleChoice';
import { PasswordRequirements } from '@/features/auth/components/PasswordRequirements';
import { useResolvedTheme } from '@/lib/theme';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatDateTime, formatKm, formatMoney, formatTons } from '@/lib/format';

const cities = Object.entries(CITY_LABELS).map(([value, label]) => ({ value, label }));
const cargos = Object.entries(CARGO_LABELS).map(([value, label]) => ({ value, label }));

const COLORS: [string, string, string][] = [
  ['ink-900', '#0E1726', 'Texto principal'], ['ink-700', '#334155', 'Texto secundario'], ['ink-500', '#64748B', 'Texto terciario (AA)'], ['ink-400', '#94A3B8', 'Bordes fuertes'],
  ['steel-100', '#EEF2F6', 'Fondo de app'], ['steel-200', '#E2E8F0', 'Bordes'], ['surface', '#FFFFFF', 'Tarjetas'],
  ['petrol-700', '#0B4F6C', 'Primario'], ['petrol-800', '#093F56', 'Primario hover'], ['petrol-50', '#E6F1F5', 'Selección'],
  ['emerald-500', '#10B981', 'Acento'], ['emerald-700', '#047857', 'Dinero / éxito (texto)'], ['emerald-50', '#ECFDF5', 'Chip positivo'],
  ['amber-500', '#F59E0B', 'Por cerrar'], ['amber-700', '#B45309', 'Pendiente (texto)'], ['amber-50', '#FFFBEB', 'Fondo aviso'],
  ['red-600', '#DC2626', 'Error / incidencia'], ['red-50', '#FEF2F2', 'Fondo error'],
];
const DARK_HEX: Record<string, string> = {
  'ink-900': '#E8EEF6', 'ink-700': '#C0CBD9', 'ink-500': '#94A3B8', 'ink-400': '#6B7C93', 'steel-100': '#0B1220', 'steel-200': '#25324A', surface: '#111A2B',
  'petrol-700': '#38A3C9', 'petrol-800': '#7CC4DE', 'petrol-50': '#12303F', 'emerald-500': '#10B981', 'emerald-700': '#34D399', 'emerald-50': '#0F2A26',
  'amber-500': '#F59E0B', 'amber-700': '#FBBF24', 'amber-50': '#2A2210', 'red-600': '#F87171', 'red-50': '#2D1518',
};
const SW: Record<string, string> = { 'ink-900': 'bg-ink-900', 'ink-700': 'bg-ink-700', 'ink-500': 'bg-ink-500', 'ink-400': 'bg-ink-400', 'steel-100': 'bg-steel-100', 'steel-200': 'bg-steel-200', surface: 'bg-surface', 'petrol-700': 'bg-petrol-700', 'petrol-800': 'bg-petrol-800', 'petrol-50': 'bg-petrol-50', 'emerald-500': 'bg-emerald-500', 'emerald-700': 'bg-emerald-700', 'emerald-50': 'bg-emerald-50', 'amber-500': 'bg-amber-500', 'amber-700': 'bg-amber-700', 'amber-50': 'bg-amber-50', 'red-600': 'bg-red-600', 'red-50': 'bg-red-50' };

function Section({ id, title, note, children }: { id: string; title: string; note?: string; children: React.ReactNode }) {
  return (
    <section id={id} className="scroll-mt-20 space-y-4">
      <div>
        <h2 className="text-xl font-semibold text-ink-900">{title}</h2>
        {note && <p className="mt-1 text-sm text-ink-500">{note}</p>}
      </div>
      {children}
    </section>
  );
}

const NAV = [['tipografia', 'Tipografía'], ['colores', 'Colores'], ['botones', 'Botones'], ['formularios', 'Formularios'], ['fechas', 'Fechas'], ['feedback', 'Feedback'], ['overlays', 'Overlays'], ['datos', 'Datos'], ['dominio', 'Dominio'], ['composicion', 'Composición'], ['paneles', 'Paneles de datos'], ['reputacion', 'Reputación y archivos'], ['formatos', 'Formatos']];

export default function UiCatalog() {
  const theme = useResolvedTheme();
  const [reasonOpen, setReasonOpen] = useState(false);
  const [city, setCity] = useState('');
  const [time, setTime] = useState('08:00');
  const [role, setRole] = useState<AssignableRole>('OPS');
  const [pw, setPw] = useState('');
  const [multi, setMulti] = useState<string[]>(['LIMA', 'JULIACA']);
  const [cargo, setCargo] = useState('GENERAL');
  const [date, setDate] = useState('');
  const [range, setRange] = useState<DateRangeValue>({ from: '', to: '' });
  const [money, setMoney] = useState('1850');
  const [weight, setWeight] = useState('3200');
  const [page, setPage] = useState(3);
  const [dlg, setDlg] = useState(false);
  const [sheet, setSheet] = useState(false);
  const [loading, setLoading] = useState(false);
  const [plan, setPlan] = useState('a');
  const [stars, setStars] = useState(0);
  const [dropFile, setDropFile] = useState<File | null>(null);
  const [quick, setQuick] = useState('');
  const [dir, setDir] = useState<'asc' | 'desc' | null>('asc');

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 lg:px-8">
      <header className="mb-8 flex flex-col gap-3 border-b border-steel-200 pb-6 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="font-mono text-xs uppercase tracking-wide text-petrol-700">Solo desarrollo · /dev/ui</p>
          <h1 className="text-3xl font-semibold tracking-tight">Catálogo de UI de LOGYX</h1>
          <p className="mt-1 max-w-2xl text-sm text-ink-700">Referencia viva del sistema de diseño. Antes de crear UI, revisa aquí qué componente existe. Documentación: <code className="font-mono">.claude/skills/logyx-design-system/</code> y <code className="font-mono">docs/design/CONTENT.md</code>.</p>
        </div>
        <div className="flex flex-col items-start gap-3 sm:items-end">
        <ThemeToggle />
        <nav aria-label="Secciones" className="flex flex-wrap gap-1.5 text-sm">
          {NAV.map(([id, l]) => <a key={id} href={`#${id}`} className="rounded-full bg-surface px-3 py-1 text-ink-700 ring-1 ring-steel-200 hover:text-petrol-700">{l}</a>)}
        </nav>
        </div>
      </header>

      <div className="space-y-14">
        <Section id="tipografia" title="Tipografía" note="Geist Sans para UI; Geist Mono para cifras técnicas (placas, km, toneladas, IDs, montos en tabla). Números tabulares en tablas y precios.">
          <Card><CardBody className="space-y-3">
            <p className="text-3xl font-semibold tracking-tight">Título de página — 30/semibold</p>
            <p className="text-xl font-semibold">Título de sección — 20/semibold</p>
            <p className="text-[15px] font-semibold">Título de tarjeta — 15/semibold</p>
            <p className="text-sm">Texto base de interfaz — 14/regular. Lima a Juliaca en 18 h.</p>
            <p className="text-[13px] font-medium text-ink-700">Etiqueta de campo — 13/medium</p>
            <p className="text-xs text-ink-500">Ayuda y contexto — 12/regular · ink-500</p>
            <p className="text-xs font-semibold uppercase tracking-wide text-ink-500">Encabezado de tabla — 12/semibold mayúsculas</p>
            <p className="font-mono text-sm tabular-nums">ABC-123 · 1,010 km · 3.2 t · S/ 1,850.00 — Geist Mono</p>
            <p className="money text-2xl">S/ 1,850.00 <span className="font-sans text-xs font-normal text-ink-500">clase .money</span></p>
          </CardBody></Card>
        </Section>

        <Section id="colores" title="Colores" note="Úsalos solo como clases Tailwind (bg-petrol-700, text-ink-500…); los tokens cambian solos en modo oscuro (usa el selector de tema de arriba). Emerald = dinero y oportunidad; amber = por cerrar/pendiente; red = error.">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            {COLORS.map(([n, hex, use]) => (
              <div key={n} className="overflow-hidden rounded-card border border-steel-200 bg-surface">
                <div className={`h-12 ${SW[n]}`} />
                <div className="p-2"><p className="font-mono text-xs font-semibold">{n}</p><p className="font-mono text-[11px] text-ink-500">{theme === 'dark' ? DARK_HEX[n] : hex}</p><p className="text-[11px] text-ink-500">{use}</p></div>
              </div>
            ))}
          </div>
          <p className="text-sm text-ink-700">Radio: tarjetas <code className="font-mono">rounded-card</code> (10px) · controles <code className="font-mono">rounded-input</code> (8px) · chips <code className="font-mono">rounded-full</code>.</p>
        </Section>

        <Section id="botones" title="Botones" note="Texto = verbo + objeto. Un solo botón primario por vista. 'loading' bloquea y muestra spinner.">
          <Card><CardBody className="space-y-5">
            <div className="flex flex-wrap items-center gap-3">
              <Button>Publicar solicitud</Button><Button variant="money"><Gavel />Enviar oferta</Button><Button variant="outline">Ver detalle</Button><Button variant="ghost">Cancelar</Button>
              <Button variant="destructive">Cancelar solicitud</Button><Button variant="destructive-solid">Eliminar vehículo</Button><Button variant="link">Ver términos</Button>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <Button size="sm">Pequeño</Button><Button>Normal</Button><Button size="lg">Grande</Button>
              <Button size="icon" variant="outline" aria-label="Agregar"><Plus /></Button>
              <Button disabled>Deshabilitado</Button>
              <Button loading={loading} onClick={() => { setLoading(true); setTimeout(() => setLoading(false), 1600); }}>{loading ? 'Publicando…' : 'Probar loading'}</Button>
            </div>
          </CardBody></Card>
        </Section>

        <Section id="formularios" title="Formularios" note="Siempre dentro de <Field> (label + ayuda + error con role='alert'). Estados: normal, foco, deshabilitado, inválido.">
          <Card><CardBody>
            <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
              <Field label="Dirección de recojo" htmlFor="d-in" hint="Calle, número y distrito"><Input id="d-in" placeholder="Av. Argentina 1234, Callao" /></Field>
              <Field label="Campo con error" htmlFor="d-err" error="Ingresa la dirección de recojo."><Input id="d-err" aria-invalid defaultValue="Av" /></Field>
              <Field label="Contraseña (PasswordInput)" htmlFor="d-pw" hint="Con botón mostrar/ocultar"><PasswordInput id="d-pw" defaultValue="Logyx2026!" autoComplete="off" /></Field>
              <Field label="Deshabilitado" htmlFor="d-dis"><Input id="d-dis" disabled value="No editable" readOnly /></Field>
              <Field label="Tipo de carga (Select)" htmlFor="d-sel"><Select id="d-sel" value={cargo} onValueChange={setCargo} options={cargos} /></Field>
              <Field label="Select inválido" htmlFor="d-sel2" error="Selecciona un vehículo."><Select id="d-sel2" value="" onValueChange={() => {}} options={cargos} invalid /></Field>
              <Field label="Ciudad (Combobox con búsqueda)" htmlFor="d-cb"><Combobox id="d-cb" value={city} onValueChange={setCity} options={cities} placeholder="Todas las ciudades" allowClear /></Field>
              <Field label="Ciudades del corredor (MultiSelect)" htmlFor="d-ms"><MultiSelect id="d-ms" value={multi} onValueChange={setMulti} options={cities} /></Field>
              <Field label="Monto (MoneyInput)" htmlFor="d-money" hint="Mínimo S/ 1,200.00"><MoneyInput id="d-money" value={money} onValueChange={setMoney} /></Field>
              <Field label="Peso (NumberInput)" htmlFor="d-w"><NumberInput id="d-w" value={weight} onValueChange={setWeight} suffix="kg" /></Field>
              <Field label="Descripción (Textarea)" htmlFor="d-ta" className="md:col-span-2 lg:col-span-3"><Textarea id="d-ta" placeholder="Ej.: 120 rollos de tela, paletizados" /></Field>
            </div>
            <div className="mt-6 grid gap-6 border-t border-steel-200 pt-5 md:grid-cols-3">
              <div className="space-y-2"><p className="text-[13px] font-medium text-ink-700">Checkbox</p>
                <div className="flex items-center gap-2"><Checkbox id="c1" defaultChecked /><InlineLabel htmlFor="c1">Acepto la política de privacidad</InlineLabel></div>
                <div className="flex items-center gap-2"><Checkbox id="c2" /><InlineLabel htmlFor="c2">Necesito carga y descarga</InlineLabel></div></div>
              <div className="space-y-2"><p className="text-[13px] font-medium text-ink-700">RadioGroup</p>
                <RadioGroup value={plan} onValueChange={setPlan}>
                  <div className="flex items-center gap-2"><RadioGroupItem id="r1" value="a" /><InlineLabel htmlFor="r1">Menor precio</InlineLabel></div>
                  <div className="flex items-center gap-2"><RadioGroupItem id="r2" value="b" /><InlineLabel htmlFor="r2">Mejor valorado</InlineLabel></div>
                </RadioGroup></div>
              <div className="space-y-2"><p className="text-[13px] font-medium text-ink-700">Switch</p>
                <div className="flex items-center gap-2"><Switch id="s1" defaultChecked /><InlineLabel htmlFor="s1">Avisarme de nuevas cargas</InlineLabel></div></div>
            </div>
          </CardBody></Card>
        </Section>

        <Section id="fechas" title="Fechas" note="Valores como 'YYYY-MM-DD'. Calendario en español, semana desde lunes. El rango trae atajos.">
          <Card><CardBody className="grid gap-5 md:grid-cols-2">
            <Field label="Fecha de recojo (DatePicker)" htmlFor="d-date"><DatePicker id="d-date" value={date} onValueChange={setDate} min={new Date().toISOString().slice(0, 10)} /></Field>
            <Field label="Recojo entre (DateRangePicker)" htmlFor="d-range"><DateRangePicker id="d-range" value={range} onValueChange={setRange} /></Field>
            <p className="font-mono text-xs text-ink-500 md:col-span-2">date = {date || '""'} · range = {range.from || '""'} → {range.to || '""'}</p>
          </CardBody></Card>
        </Section>

        <Section id="feedback" title="Feedback" note="Toast = efímero tras una acción. Alert = persistente en la página. Skeleton = carga.">
          <Card><CardBody className="space-y-5">
            <div className="flex flex-wrap gap-3">
              <Button variant="outline" onClick={() => toast.success('Oferta enviada', 'La PYME verá tu oferta de inmediato.')}>Toast éxito</Button>
              <Button variant="outline" onClick={() => toast.error('No pudimos publicar la solicitud', 'Revisa tu conexión e inténtalo de nuevo.')}>Toast error</Button>
              <Button variant="outline" onClick={() => toast.info('La subasta cierra en 5 minutos')}>Toast info</Button>
              <Button variant="outline" onClick={() => toast.warning('Tu licencia vence en 7 días')}>Toast aviso</Button>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <Alert tone="info" title="Subasta abierta">Los transportistas pueden ofertar hasta las 18:00.</Alert>
              <Alert tone="success">Tu solicitud fue publicada.</Alert>
              <Alert tone="warning" title="Falta un conductor">Asigna un conductor para que el envío pueda iniciar.</Alert>
              <Alert tone="error" title="No pudimos cargar tus envíos">Actualiza la página o inténtalo en unos minutos.</Alert>
            </div>
            <div className="grid gap-3 md:grid-cols-2"><SkeletonRows rows={3} /><div className="space-y-2"><Skeleton className="h-6 w-1/2" /><Skeleton className="h-4 w-full" /><Skeleton className="h-4 w-2/3" /></div></div>
          </CardBody></Card>
        </Section>

        <Section id="overlays" title="Overlays y menús" note="confirm() devuelve Promise<boolean>. Dialog para formularios cortos; Sheet para paneles laterales (ofertar, detalle).">
          <Card><CardBody className="space-y-5">
            <div className="flex flex-wrap items-center gap-3">
              <Button variant="destructive" onClick={async () => { const ok = await confirm({ title: '¿Cancelar esta solicitud?', description: 'Se cerrará la subasta y los transportistas dejarán de ver tu carga. No se puede deshacer.', confirmLabel: 'Cancelar solicitud', cancelLabel: 'Volver', tone: 'destructive' }); toast[ok ? 'success' : 'info'](ok ? 'Solicitud cancelada' : 'No se hicieron cambios'); }}>confirm() destructivo</Button>
              <Button variant="outline" onClick={async () => { const ok = await confirm({ title: '¿Aceptar la oferta de S/ 1,850.00?', description: 'Transportes Andinos hará el envío Lima → Juliaca. Las demás ofertas se rechazarán.', confirmLabel: 'Aceptar oferta' }); if (ok) toast.success('Oferta aceptada'); }}>confirm() normal</Button>
              <Button variant="outline" onClick={() => setDlg(true)}>Dialog</Button>
              <Button variant="outline" onClick={() => setSheet(true)}>Sheet</Button>
              <Tooltip content="Descargar comprobante"><Button variant="outline" size="icon" aria-label="Descargar"><Download /></Button></Tooltip>
              <DropdownMenu>
                <DropdownMenuTrigger asChild><Button variant="outline" size="icon" aria-label="Más acciones"><MoreHorizontal /></Button></DropdownMenuTrigger>
                <DropdownMenuContent>
                  <DropdownMenuLabel>Vehículo ABC-123</DropdownMenuLabel>
                  <DropdownMenuItem><Pencil />Editar</DropdownMenuItem>
                  <DropdownMenuItem><PackageCheck />Marcar disponible</DropdownMenuItem>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem destructive><Trash2 />Eliminar</DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
              <span className="text-xs text-ink-500">Atajo ejemplo: <Kbd>Esc</Kbd> cierra · <Kbd>↑</Kbd><Kbd>↓</Kbd> navega</span>
            </div>
            <Tabs defaultValue="ofertas">
              <TabsList><TabsTrigger value="ofertas">Ofertas</TabsTrigger><TabsTrigger value="detalle">Detalle</TabsTrigger><TabsTrigger value="historial">Historial</TabsTrigger></TabsList>
              <TabsContent value="ofertas"><p className="text-sm text-ink-700">Contenido de la pestaña Ofertas.</p></TabsContent>
              <TabsContent value="detalle"><p className="text-sm text-ink-700">Contenido de Detalle.</p></TabsContent>
              <TabsContent value="historial"><p className="text-sm text-ink-700">Contenido de Historial.</p></TabsContent>
            </Tabs>
          </CardBody></Card>
          <Dialog open={dlg} onOpenChange={setDlg} title="Agregar conductor" description="Se creará una cuenta para que pueda ingresar a la app." footer={<><Button variant="ghost" onClick={() => setDlg(false)}>Cancelar</Button><Button onClick={() => { setDlg(false); toast.success('Conductor agregado'); }}>Agregar conductor</Button></>}>
            <Field label="Nombre completo" htmlFor="dlg-n"><Input id="dlg-n" placeholder="Carlos Quispe" /></Field>
          </Dialog>
          <Sheet open={sheet} onOpenChange={setSheet} title="Enviar oferta" description="Lima → Juliaca · 3.2 t"><div className="p-5 text-sm text-ink-700">Contenido del panel lateral.</div></Sheet>
        </Section>

        <Section id="datos" title="Datos" note="Tabla, paginación (lista para meta.page/size/total), avatar, badge, estado vacío.">
          <Card>
            <Table>
              <thead><tr><Th>Ruta</Th><Th>Peso</Th><Th>Estado</Th><Th className="text-right">Precio</Th></tr></thead>
              <tbody>
                <Tr><Td className="font-medium">Lima → Juliaca</Td><Td className="font-mono">3.2 t</Td><Td><StatusPill status="PUBLISHED" /></Td><Td className="text-right font-mono tabular-nums">S/ 1,850.00</Td></Tr>
                <Tr><Td className="font-medium">Arequipa → Cusco</Td><Td className="font-mono">1.8 t</Td><Td><StatusPill status="MATCHED" /></Td><Td className="text-right font-mono tabular-nums">S/ 1,240.00</Td></Tr>
              </tbody>
            </Table>
            <div className="border-t border-steel-200 p-4"><Pagination page={page} size={10} total={86} onPageChange={setPage} /></div>
          </Card>
          <div className="flex flex-wrap items-center gap-3">
            <Avatar name="Transportes Andinos" size="sm" /><Avatar name="Transportes Andinos" /><Avatar name="Carlos Quispe" size="lg" />
            <Badge>Neutral</Badge><Badge tone="petrol">Petrol</Badge><Badge tone="emerald">Compatible</Badge><Badge tone="amber">Por cerrar</Badge><Badge tone="red">Incidencia</Badge>
          </div>
          <Card><EmptyState title="Aún no tienes solicitudes" description="Publica tu primera carga y recibe ofertas de transportistas en minutos." action={<Button><Plus />Publicar solicitud</Button>} /></Card>
        </Section>

        <Section id="dominio" title="Componentes de dominio" note="components/domain: úsalos en lugar de reconstruir estados, rutas o KPIs.">
          <div className="grid gap-4 md:grid-cols-3">
            <KpiTile label="Solicitudes activas" value="4" context="2 con ofertas por revisar" icon={Truck} />
            <KpiTile label="Envíos en tránsito" value="2" context="1 llega hoy" />
            <KpiTile label="Entregados del mes" value="11" />
          </div>
          <Card><CardBody className="space-y-4">
            <RouteLine origin="LIMA" destination="JULIACA" km={1010} originSub="28 nov · 08:00" destinationSub="30 nov" />
            <div className="flex flex-wrap items-center gap-2"><StatusPill status="PUBLISHED" /><StatusPill status="MATCHED" /><StatusPill status="CANCELLED" /><StatusPill status="PENDING" /><StatusPill status="IN_TRANSIT" /><StatusPill status="DELIVERED" /><StatusPill status="REJECTED" /><StatusPill status="MAINTENANCE" /></div>
            <div className="flex items-center gap-2 text-sm">Cierra en <Countdown closesAt={new Date(Date.now() + 3 * 60_000).toISOString()} /> · <Countdown closesAt={new Date(Date.now() + 5 * 3600_000).toISOString()} /></div>
          </CardBody></Card>
        </Section>

        <Section id="cuentas" title="Verificación y acciones con motivo" note="RN01: VerificationBanner (AppShell lo monta según /auth/me), VerificationBlock (ORG-003/004 dentro de un formulario), StatusPill kind=verification y VerifiedBadge (= Verificado por OnCargo; rucValid no da sello). ReasonDialog: cancelar, rechazar, suspender, reasignar. UnfitList: vehículos no aptos con su motivo (RN02).">
          <div className="space-y-3">
            <VerificationBanner status="PENDING" orgType="PYME" />
            <VerificationBanner status="REJECTED" note="La razón social no coincide con el RUC." orgType="CARRIER" />
            <VerificationBanner status="SUSPENDED" note="Reportes reiterados de incumplimiento." orgType="PYME" />
            <VerificationBlock failure={{ code: 'LOGYX-ORG-004', message: 'Tu organización está suspendida por LOGYX (Reportes reiterados). Escríbenos para revisarlo' }} />
          </div>
          <Card className="space-y-4 p-5">
            <div className="flex flex-wrap items-center gap-2"><StatusPill kind="verification" status="PENDING" /><StatusPill kind="verification" status="VERIFIED" /><StatusPill kind="verification" status="REJECTED" /><StatusPill kind="verification" status="SUSPENDED" /><StatusPill kind="bid" status="WITHDRAWN" /><StatusPill kind="counter" status="EXPIRED" /><VerifiedBadge /></div>
            <div className="flex flex-wrap gap-2"><Button variant="destructive" onClick={() => setReasonOpen(true)}>Abrir ReasonDialog</Button></div>
            <UnfitList items={partitionFleet([{ id: '1', plate: 'ABC-123', type: 'TRUCK_SMALL', maxWeightKg: 5000, status: 'AVAILABLE' }, { id: '2', plate: 'XYZ-987', type: 'VAN', maxWeightKg: 1200, status: 'MAINTENANCE' }], { weightKg: 3000, cargoType: 'HAZARDOUS', compatibleVehicleTypes: ['TRUCK_MEDIUM', 'TRUCK_LARGE', 'TANKER'] }).unfit} />
          </Card>
          <ReasonDialog open={reasonOpen} onOpenChange={setReasonOpen} title="¿Cancelar este envío?" description="Se avisa a la otra parte con el motivo. No se puede deshacer." required maxLength={500} confirmLabel="Cancelar envío" tone="destructive" hint="Lo verá la otra parte." placeholder="Ej. El cliente pidió postergar la entrega." onSubmit={async (r) => { toast.info('Motivo recibido', r); return null; }} />
        </Section>

        <Section id="roles" title="Cuentas, roles y contraseñas" note="V14: ReadOnlyNotice (solo aparece sin permiso OPERATE; `useCan('OPERATE')` oculta las acciones de escritura, el backend responde AUTH-007/008), RoleChoice (rol con descripción de qué puede cada uno), PasswordRequirements (reglas visibles de la contraseña nueva) y StatusPill kind=account (activo, invitación pendiente, desactivado) y LOST/BID_CANCELLED de Mis ofertas.">
          <Card className="space-y-4 p-5">
            <ReadOnlyNotice>No puedes publicar solicitudes.</ReadOnlyNotice>
            <div className="flex flex-wrap items-center gap-2"><StatusPill kind="account" status="ACTIVE" /><StatusPill kind="account" status="INVITED" /><StatusPill kind="account" status="INACTIVE" /><StatusPill status="LOST" /><StatusPill status="BID_CANCELLED" /></div>
            <div className="grid gap-5 sm:grid-cols-2">
              <RoleChoice value={role} onValueChange={setRole} idPrefix="d-role" />
              <div className="space-y-3">
                <Field label="Contraseña nueva" htmlFor="d-newpw"><PasswordInput id="d-newpw" value={pw} onChange={(e) => setPw(e.currentTarget.value)} autoComplete="new-password" /></Field>
                <PasswordRequirements id="d-rules" password={pw} confirm={pw ? pw : ''} />
              </div>
            </div>
          </Card>
        </Section>

        <Section id="fase4" title="Viajes" note="TimeSelect (hora cada 30 min junto a DatePicker) y CapacityBar (ocupación en kg, role meter).">
          <Card className="grid gap-5 p-5 sm:grid-cols-2">
            <Field label="Hora de salida" htmlFor="d-time"><TimeSelect id="d-time" value={time} onValueChange={setTime} /></Field>
            <div className="space-y-3"><CapacityBar used={4500} total={12000} label="Ocupación de ejemplo" /><CapacityBar used={11000} total={12000} label="Ocupación casi llena" /></div>
          </Card>
        </Section>

        <Section id="composicion" title="Composición" note="Piezas compartidas para pantallas completas: FilterBar (listados), Timeline (paradas/hitos), VerifiedBadge (RUC verificado), AuthCard (login/registro).">
          <Card>
            <FilterBar count={12} noun={['carga', 'cargas']} active onClear={() => toast.info('Filtros limpiados')} sort={<Select id="d-fb-s" aria-label="Ordenar por" value="pickup" onValueChange={() => {}} options={[{ value: 'pickup', label: 'Recojo más próximo' }]} className="h-9 w-44" />}>
              <Field label="Buscar (SearchInput)" htmlFor="d-fb-q" hint="Enter o lupa para buscar; la X limpia"><SearchInput id="d-fb-q" value="" onSearch={(t) => toast.info(t ? `Buscar «${t}»` : 'Búsqueda limpia')} placeholder="Razón social, RUC, placa…" /></Field>
              <Field label="Origen" htmlFor="d-fb-o"><Combobox id="d-fb-o" value="" onValueChange={() => {}} options={[{ value: 'LIMA', label: 'Lima' }]} placeholder="Todas las ciudades" allowClear /></Field>
              <Field label="Destino" htmlFor="d-fb-d"><Combobox id="d-fb-d" value="" onValueChange={() => {}} options={[{ value: 'JULIACA', label: 'Juliaca' }]} placeholder="Todas las ciudades" allowClear /></Field>
            </FilterBar>
            <CardBody className="space-y-6">
              <Timeline label="Paradas de ejemplo" items={[
                { id: '1', title: 'Recojo · Lima', detail: 'Av. Argentina 2450, Callao', meta: 'Completada · 28 nov · 08:10', status: 'done' },
                { id: '2', title: 'Entrega · Juliaca', detail: 'Jr. San Román 456', meta: 'El conductor llegó', status: 'current' },
                { id: '3', title: 'Cierre', detail: 'Confirmación de entrega', meta: 'Pendiente', status: 'pending' },
              ]} />
              <div className="flex flex-wrap items-center gap-4"><VerifiedBadge /><VerifiedBadge variant="icon" /></div>
              <AuthCard title="Ingresa a tu cuenta" subtitle="Tarjeta de acceso (AuthCard)" footer="Pie con enlaces">
                <p className="text-sm text-ink-700">Contenido del formulario.</p>
              </AuthCard>
            </CardBody>
          </Card>
        </Section>

        <Section id="paneles" title="Paneles de datos" note="Piezas de los dashboards: KpiTile (tone, delta, unit, loading) en KpiGrid; Table con stack (tarjetas por container query), sticky, dense, Th ordenable y label; DescriptionList; QuickFilters; ExportButton (exportar CSV con aviso de error); EmptyState icon/compact; SkeletonList; CardFooter/CardDescription.">
          <KpiGrid>
            <KpiTile label="Solicitudes activas" value={10} context="3 con subasta abierta" icon={Truck} href="#paneles" tone="petrol" />
            <KpiTile label="Ahorro vs sugerido" value="S/ 1,240" context="en 4 envíos" icon={Gavel} tone="money" delta={{ value: '+8%', direction: 'up', good: true }} />
            <KpiTile label="Subastas por cerrar" value={2} context="menos de 5 min" icon={PackageCheck} tone="warn" />
            <KpiTile label="Incidencias abiertas" value={1} context="requiere atención" tone="alert" loading />
          </KpiGrid>
          <Card>
            <CardHeader><div><CardTitle>Solicitudes recientes</CardTitle><CardDescription>Redimensiona la ventana: bajo 720 px del contenedor las filas pasan a tarjetas.</CardDescription></div><div className="flex flex-wrap items-center gap-2"><ExportButton href="/exportar/solicitudes" /><QuickFilters label="Estado" value={quick} onValueChange={setQuick} options={[{ value: '', label: 'Todas', count: 13 }, { value: 'PUBLISHED', label: 'Publicadas', count: 10 }, { value: 'MATCHED', label: 'Adjudicadas', count: 3 }]} /></div></CardHeader>
            <Table label="Solicitudes recientes" stack sticky>
              <thead><tr><Th>Ruta</Th><Th sort={dir} onSort={() => setDir(dir === 'asc' ? 'desc' : 'asc')}>Recojo</Th><Th>Estado</Th><Th className="text-right">Sugerido</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>
              <tbody>
                {[['LIMA', 'JULIACA', 1133, '28 nov', 'PUBLISHED', 1850], ['AREQUIPA', 'PUNO', 235, '29 nov', 'MATCHED', 913.11]].map(([o, d, km, f, st, pr]) => (
                  <Tr key={String(o)}>
                    <Td primary><RouteLine size="sm" origin={String(o)} destination={String(d)} km={Number(km)} /></Td>
                    <Td label="Recojo">{f}</Td>
                    <Td label="Estado"><StatusPill status={String(st)} /></Td>
                    <Td label="Sugerido" className="text-right"><span className="money">{formatMoney(Number(pr))}</span></Td>
                    <Td actions className="text-right"><Button size="sm" variant="outline">Ver detalle</Button></Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
            <CardFooter><span className="text-ink-500">2 de 13 solicitudes</span><Button size="sm" variant="ghost">Ver todas</Button></CardFooter>
          </Card>
          <div className="grid gap-4 md:grid-cols-2">
            <Card><CardHeader><CardTitle>Detalle de carga</CardTitle></CardHeader><CardBody>
              <DescriptionList items={[{ label: 'Peso', value: '2.5 t', mono: true }, { label: 'Distancia', value: '1,133 km', mono: true }, { label: 'Precio sugerido', value: 'S/ 1,850.00', money: true }, { label: 'Recojo', value: '28 nov · 08:00', hint: 'Av. Argentina 1234, Callao' }, { label: 'Notas', value: 'Carga frágil, no apilar', span: true }]} />
            </CardBody></Card>
            <Card><CardHeader><CardTitle>Estados de carga</CardTitle></CardHeader>
              <EmptyState compact icon={SearchX} title="No hay cargas con estos filtros" description="Prueba con otra ciudad o amplía el rango de fechas." action={<Button size="sm" variant="outline">Limpiar filtros</Button>} />
              <SkeletonList rows={2} />
            </Card>
          </div>
        </Section>

        <Section id="reputacion" title="Reputación y archivos" note="StarRating: entrada accesible (radiogroup, flechas) o solo lectura con decimales. TrustScore: chip en listas, meter en detalle. FileDropzone: arrastrar y soltar con validación de tipo y tamaño.">
          <Card><CardBody className="grid gap-6 md:grid-cols-2">
            <div className="space-y-3">
              <p className="text-[13px] font-medium text-ink-700">StarRating (entrada)</p>
              <StarRating label="Puntualidad" value={stars} onValueChange={setStars} size="lg" />
              <StarRating label="Con error" value={0} onValueChange={() => {}} invalid />
              <StarRating label="Deshabilitado" value={3} onValueChange={() => {}} disabled />
              <p className="text-[13px] font-medium text-ink-700">StarRating (solo lectura)</p>
              <div className="flex items-center gap-4"><StarRating readOnly value={4.67} label="Promedio" /><span className="font-mono text-sm tabular-nums">4.7</span><StarRating readOnly value={2.3} size="sm" /></div>
            </div>
            <div className="space-y-3">
              <p className="text-[13px] font-medium text-ink-700">TrustScore</p>
              <div className="flex flex-wrap items-center gap-2"><TrustScore score={92.4} /><TrustScore score={77.8} /><TrustScore score={61} /><TrustScore score={38.5} /></div>
              <TrustScore variant="meter" score={77.8} reviews={3} className="max-w-xs" />
              <p className="pt-2 text-[13px] font-medium text-ink-700">FileDropzone (máx. 10 MB)</p>
              <FileDropzone file={dropFile} onFileChange={setDropFile} />
            </div>
          </CardBody></Card>
        </Section>

        <Section id="formatos" title="Formatos" note="Siempre vía lib/format.ts; nunca formatees a mano.">
          <Card><CardBody>
            <dl className="grid gap-x-8 gap-y-2 text-sm sm:grid-cols-2">
              {[['formatMoney(1850)', formatMoney(1850)], ['formatDateTime(iso)', formatDateTime('2026-11-28T13:00:00Z')], ['formatDate("2026-11-28")', formatDate('2026-11-28')], ['formatTons(2500)', formatTons(2500)], ['formatKm(1010)', formatKm(1010)]].map(([k, v]) => (
                <div key={k} className="flex justify-between border-b border-steel-200 py-1.5"><dt className="font-mono text-xs text-ink-500">{k}</dt><dd className="font-mono tabular-nums">{v}</dd></div>
              ))}
            </dl>
          </CardBody></Card>
        </Section>
      </div>
    </div>
  );
}
