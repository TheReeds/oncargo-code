// Autoría y rastro de acciones de un recurso (roadmap v2 1.8). Solo servidor; lectura bajo RLS.
import { apiFor, type Schemas } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';

export type ActivityView = Schemas['ActivityView'];
export type ActivitySubject = 'REQUEST' | 'BID' | 'SHIPMENT' | 'TRIP' | 'INCIDENT';

export const getActivity = (token: string, subject: ActivitySubject, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/activity', { params: { query: { subject, id } } }));
