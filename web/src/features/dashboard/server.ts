import { listMine } from '@/features/requests/server';
import { listShipments } from '@/features/shipments/server';

export async function pymeDashboard(token: string) {
  const [requests, shipments] = await Promise.all([listMine(token), listShipments(token)]);
  const published = requests.filter((r) => r.status === 'PUBLISHED');
  const now = Date.now();
  const closed = (r: (typeof requests)[number]) => r.auction?.status !== 'OPEN' || (r.auction?.closesAt ? new Date(r.auction.closesAt).getTime() < now : false);
  const attention = published.filter((r) => (r.auction?.bidCount ?? 0) > 0 && closed(r));
  const withBids = published.filter((r) => (r.auction?.bidCount ?? 0) > 0);
  const inTransit = shipments.filter((s) => s.status === 'IN_TRANSIT');
  const pending = shipments.filter((s) => s.status === 'PENDING');
  const month = new Date().toISOString().slice(0, 7);
  const delivered = shipments.filter((s) => s.status === 'DELIVERED' && (s.deliveredAt ?? '').startsWith(month));
  return {
    kpis: {
      active: published.length,
      closingSoon: published.filter((r) => !closed(r)).length,
      offersToReview: withBids.reduce((n, r) => n + (r.auction?.bidCount ?? 0), 0),
      requestsWithBids: withBids.length,
      inTransit: inTransit.length,
      toPickup: pending.length,
      deliveredMonth: delivered.length,
    },
    attention,
    ongoing: shipments.filter((s) => s.status === 'IN_TRANSIT' || s.status === 'PENDING'),
  };
}
