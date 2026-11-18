import { listMine } from '@/features/requests/server';
import { listShipments } from '@/features/shipments/server';
import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';

export async function pymeDashboard(token: string) {
  const [requests, shipments, summary] = await Promise.all([listMine(token), listShipments(token), unwrap(apiFor(token).GET('/api/v1/dashboard/summary'))]);
  const published = requests.filter((r) => r.status === 'PUBLISHED');
  const now = Date.now();
  const closed = (r: (typeof requests)[number]) => r.auction?.status !== 'OPEN' || (r.auction?.closesAt ? new Date(r.auction.closesAt).getTime() < now : false);
  const withBids = published.filter((r) => (r.auction?.bidCount ?? 0) > 0);
  // Subastas con ofertas sin elegir (abiertas o ya cerradas): primero las que cierran antes; las cerradas arriba (urgen más).
  const closesAt = (r: (typeof requests)[number]) => (r.auction?.closesAt ? new Date(r.auction.closesAt).getTime() : Number.MAX_SAFE_INTEGER);
  const attentionRequests = [...withBids].sort((a, b) => Number(closed(b)) - Number(closed(a)) || closesAt(a) - closesAt(b));
  // Mejor oferta pendiente de cada una: viene en `GET /shipment-requests/mine` (`bestBid*`, solo para la PYME dueña),
  // sin pedir las ofertas de cada solicitud.
  const attention = attentionRequests.slice(0, 5).map((request) => {
    const amount = request.bestBidAmount ?? null;
    return { request, closed: closed(request), best: amount === null ? null : { amount, carrierName: request.bestBidCarrierName ?? '', carrierTrustScore: request.bestBidCarrierTrustScore ?? null } };
  });
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
      activeTripBookings: summary.activeTripBookings ?? 0,
    },
    attention,
    attentionTotal: attentionRequests.length,
    ongoing: shipments.filter((s) => s.status === 'IN_TRANSIT' || s.status === 'PENDING'),
  };
}

/** Resumen del transportista (`GET /dashboard/summary`); los campos de fase 4 pueden faltar en servidores antiguos. */
export async function carrierDashboard(token: string) {
  const s = await unwrap(apiFor(token).GET('/api/v1/dashboard/summary'));
  return {
    openTrips: s.openTrips ?? 0,
    pendingTripBookings: s.pendingTripBookings ?? 0,
    pendingSmartLoads: s.pendingSmartLoads ?? 0,
    returnLoads: s.returnLoads ?? 0,
    topReturnLoads: s.topReturnLoads ?? [],
    compatibleLoads: s.compatibleLoads ?? 0,
    pendingBids: s.pendingBids ?? 0,
    activeShipments: s.activeShipments ?? 0,
    deliveredThisMonth: s.deliveredThisMonth ?? 0,
  };
}
