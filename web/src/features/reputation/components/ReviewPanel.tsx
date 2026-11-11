import { useCallback, useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { Star } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Textarea } from '@/components/ui/input';
import { StarRating } from '@/components/ui/star-rating';
import { Skeleton } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toaster';
import { EmptyState } from '@/components/domain/EmptyState';
import type { ShipmentReviewsView } from '../server';
import { ReviewCard } from './ReviewCard';

type Props = { shipmentId: string; perspective: 'PYME' | 'CARRIER'; status: string; hasActiveIncident?: boolean };
type Scores = { punctuality: number; cargoCondition: number; communication: number };

function ReviewForm({ shipmentId, perspective, onDone }: { shipmentId: string; perspective: 'PYME' | 'CARRIER'; onDone: () => void }) {
  const [scores, setScores] = useState<Scores>({ punctuality: 0, cargoCondition: 0, communication: 0 });
  const [comment, setComment] = useState('');
  const [errors, setErrors] = useState<Partial<Record<keyof Scores | 'comment', string>>>({});
  const [busy, setBusy] = useState(false);
  const target = perspective === 'PYME' ? 'el transportista' : 'la PYME';
  const dims: { key: keyof Scores; label: string; hint: string; empty: string }[] = [
    { key: 'punctuality', label: 'Puntualidad', hint: perspective === 'PYME' ? 'Recojo y entrega en los horarios acordados.' : 'La carga estuvo lista a la hora del recojo.', empty: 'Califica la puntualidad.' },
    { key: 'cargoCondition', label: perspective === 'PYME' ? 'Estado de la carga' : 'Exactitud de la carga declarada', hint: perspective === 'PYME' ? 'Cómo llegó tu carga al destino.' : 'El peso, tipo y descripción coincidieron con lo declarado.', empty: perspective === 'PYME' ? 'Califica el estado de la carga.' : 'Califica la exactitud de la carga declarada.' },
    { key: 'communication', label: 'Comunicación', hint: 'Claridad y rapidez al coordinar.', empty: 'Califica la comunicación.' },
  ];

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: typeof errors = {};
    dims.forEach((d) => { if (!scores[d.key]) next[d.key] = d.empty; });
    if (comment.length > 500) next.comment = 'El comentario admite hasta 500 caracteres.';
    setErrors(next);
    if (Object.keys(next).length) return;
    setBusy(true);
    const { error } = await actions.reputation.submit({ shipmentId, ...scores, comment: comment.trim() || undefined });
    setBusy(false);
    if (error) { toast.error(error.message); return; }
    toast.success('Calificación enviada', `Tu opinión ya cuenta en el Trust Score de ${target}.`);
    onDone();
  }

  return (
    <form method="post" onSubmit={submit} noValidate className="space-y-5" aria-label="Calificar envío">
      <p className="text-sm text-ink-700">El envío fue entregado. Califica a {target} de 1 a 5 estrellas; tu calificación es definitiva y solo puedes enviarla una vez.</p>
      <div className="grid gap-5 sm:grid-cols-3">
        {dims.map((d) => (
          <Field key={d.key} label={d.label} htmlFor={`rate-${shipmentId}-${d.key}`} hint={d.hint} error={errors[d.key]} required>
            <StarRating id={`rate-${shipmentId}-${d.key}`} label={d.label} size="lg" value={scores[d.key]} invalid={!!errors[d.key]} onValueChange={(v) => { setScores((s) => ({ ...s, [d.key]: v })); setErrors((x) => ({ ...x, [d.key]: undefined })); }} />
          </Field>
        ))}
      </div>
      <Field label="Comentario" htmlFor={`rate-${shipmentId}-comment`} hint="Opcional. No incluyas teléfonos ni correos." error={errors.comment}>
        <Textarea id={`rate-${shipmentId}-comment`} value={comment} maxLength={500} onChange={(e) => setComment(e.target.value)} aria-invalid={!!errors.comment} placeholder="Cuéntanos cómo fue el servicio" />
        <p className="mt-1 text-right font-mono text-xs tabular-nums text-ink-500">{comment.length}/500</p>
      </Field>
      <div className="flex justify-end"><Button type="submit" loading={busy}>{busy ? 'Enviando calificación…' : 'Enviar calificación'}</Button></div>
    </form>
  );
}

export default function ReviewPanel({ shipmentId, perspective, status }: Props) {
  const [data, setData] = useState<ShipmentReviewsView | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    const res = await actions.reputation.forShipment({ shipmentId });
    if (res.error) setError(res.error.message);
    else setData(res.data);
  }, [shipmentId]);
  useEffect(() => { setData(null); load(); }, [load]);

  if (error)
    return <Alert tone="error" title="No pudimos cargar las calificaciones" action={<Button size="sm" variant="outline" onClick={load}>Reintentar</Button>}>{error}</Alert>;
  if (!data)
    return <div className="space-y-3" aria-busy="true" aria-label="Cargando calificaciones"><Skeleton className="h-24 w-full" /><Skeleton className="h-20 w-full" /></div>;

  const reviews = data.reviews ?? [];
  const delivered = status === 'DELIVERED';
  return (
    <div className="space-y-4">
      {data.canReview ? (
        <Card>
          <CardHeader><CardTitle className="flex items-center gap-2"><Star className="size-4 text-amber-700" aria-hidden="true" />Califica este envío</CardTitle></CardHeader>
          <CardBody><ReviewForm shipmentId={shipmentId} perspective={perspective} onDone={load} /></CardBody>
        </Card>
      ) : (
        data.blockedReason && (
          <Alert tone="info" title={delivered ? 'No puedes calificar este envío' : 'Aún no puedes calificar este envío'}>
            {data.blockedReason.replace(/\.?$/, '.')}{!delivered && ' Podrás calificar cuando el envío esté entregado y tenga foto de entrega.'}
          </Alert>
        )
      )}
      <section aria-label="Calificaciones del envío">
        <h3 className="mb-2 text-sm font-semibold text-ink-900">Calificaciones de este envío</h3>
        {reviews.length === 0 ? (
          <Card><EmptyState title="Aún no hay calificaciones" description="Cuando la PYME o el transportista califiquen este envío, las verás aquí." /></Card>
        ) : (
          <ul className="space-y-3">{reviews.map((r) => <ReviewCard key={r.id} review={r} />)}</ul>
        )}
      </section>
    </div>
  );
}
