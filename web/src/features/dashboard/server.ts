import { listMine } from '@/features/requests/server';
import { listShipments } from '@/features/shipments/server';
import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';

export async function pymeDashboard(token: string) {
  const [requests, shipments, summary] = await Promise.all([listMine(token), listShipments(token), unwrap(apiFor(token).GET('/api/v1/dashboard/summary'))]);
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
      active: summary.activeRequests ?? published.length,
      closingSoon: published.filter((r) => !closed(r)).length,
      offersToReview: summary.bidsToReview ?? withBids.reduce((n, r) => n + (r.auction?.bidCount ?? 0), 0),
      requestsWithBids: withBids.length,
      inTransit: summary.shipmentsInTransit ?? inTransit.length,
      toPickup: pending.length,
      deliveredMonth: summary.deliveredThisMonth ?? delivered.length,
    },
    attention,
    ongoing: shipments.filter((s) => s.status === 'IN_TRANSIT' || s.status === 'PENDING'),
  };
}
