import { useState } from 'react';
import { MessagesSquare } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import NegotiationSheet, { useCounterOffers, type MyBid } from './NegotiationSheet';

/** Mis ofertas (transportista): botón "Negociar" por fila; abre el sheet del hilo. `autoOpen` para `?solicitud=`. */
export default function NegotiateCell({ requestId, route, bid, closed, autoOpen, floor, canWithdraw }: { requestId: string; route: string; bid: MyBid; closed?: boolean; autoOpen?: boolean; floor?: number | null; canWithdraw?: boolean }) {
  const [open, setOpen] = useState(!!autoOpen);
  const { offers } = useCounterOffers(bid.id, bid.status === 'PENDING' && !closed, 30000);
  const pending = offers.find((o) => o.status === 'PENDING');
  const toAnswer = !!pending && !pending.proposedByCarrier;
  const waiting = !!pending && !!pending.proposedByCarrier;
  return (
    <div className="flex flex-col items-end gap-1">
      <Button size="sm" variant={toAnswer ? 'money' : 'outline'} onClick={() => setOpen(true)}><MessagesSquare />Negociar<span className="sr-only"> la oferta {route}</span></Button>
      {toAnswer && <Badge tone="amber">Contra-oferta por responder</Badge>}
      {waiting && <Badge tone="neutral">Esperando a la PYME</Badge>}
      <NegotiationSheet open={open} onOpenChange={setOpen} requestId={requestId} bid={bid} closed={closed} floor={floor} canWithdraw={canWithdraw} title="Negociar oferta" description={route} />
    </div>
  );
}
