import { useState } from 'react';
import { MessagesSquare } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import NegotiationSheet, { useCounterOffers, type MyBid } from './NegotiationSheet';

/** Mis ofertas (transportista): botón "Negociar" por fila; abre el sheet del hilo. `autoOpen` para `?solicitud=`. */
export default function NegotiateCell({ requestId, route, bid, closed, autoOpen }: { requestId: string; route: string; bid: MyBid; closed?: boolean; autoOpen?: boolean }) {
  const [open, setOpen] = useState(!!autoOpen);
  const { offers } = useCounterOffers(bid.id, bid.status === 'PENDING' && !closed, 30000);
  const hasPending = offers.some((o) => o.status === 'PENDING');
  return (
    <div className="flex flex-col items-end gap-1">
      <Button size="sm" variant={hasPending ? 'money' : 'outline'} onClick={() => setOpen(true)}><MessagesSquare />Negociar<span className="sr-only"> la oferta {route}</span></Button>
      {hasPending && <Badge tone="amber">Contra-oferta por responder</Badge>}
      <NegotiationSheet open={open} onOpenChange={setOpen} requestId={requestId} bid={bid} closed={closed} title="Negociar oferta" description={route} />
    </div>
  );
}
