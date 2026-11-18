import { apiFor, type Schemas } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import { unwrapPage } from '@/lib/api/page';

export type TripView = Schemas['TripView'];
export type TripBookingView = Schemas['TripBookingView'];
export type ReturnLoadView = Schemas['ReturnLoadView'];
export type SmartLoadView = Schemas['SmartLoadView'];

// ── Transportista: viajes ─────────────────────────────────────────────
export const createTrip = (token: string, body: Schemas['CreateTripRequest']) => unwrap(apiFor(token).POST('/api/v1/trips', { body }));
export const listMyTrips = (token: string) => unwrap(apiFor(token).GET('/api/v1/trips/mine'));
export const getTrip = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/trips/{id}', { params: { path: { id } } }));
export const closeTrip = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/trips/{id}/close', { params: { path: { id } } }));
export const cancelTrip = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/trips/{id}/cancel', { params: { path: { id } } }));
export const listTripBookings = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/trips/{id}/bookings', { params: { path: { id } } }));
export const confirmBooking = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/trip-bookings/{id}/confirm', { params: { path: { id } } }));
export const rejectBooking = (token: string, id: string, reason?: string) =>
  unwrap(apiFor(token).POST('/api/v1/trip-bookings/{id}/reject', { params: { path: { id } }, body: reason ? { reason } : {} }));

// ── Transportista: retornos y Smart Load ──────────────────────────────
export const listReturnLoads = (token: string) => unwrap(apiFor(token).GET('/api/v1/return-loads'));
export const listSmartLoads = (token: string) => unwrap(apiFor(token).GET('/api/v1/smart-loads', { params: { query: {} } }));
export const generateSmartLoads = (token: string) => unwrap(apiFor(token).POST('/api/v1/smart-loads/generate'));
/** F12: acepta el plan = oferta en lote (una por carga). `items` fija el monto por carga; sin `items` se oferta el sugerido. */
export const acceptSmartLoad = (token: string, id: string, body: Schemas['AcceptSmartLoadRequest'] = {}) => unwrap(apiFor(token).POST('/api/v1/smart-loads/{id}/accept', { params: { path: { id } }, body }));
export const rejectSmartLoad = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/smart-loads/{id}/reject', { params: { path: { id } } }));

// ── PYME ──────────────────────────────────────────────────────────────
export type TripQuery = { originCity?: string; destinationCity?: string; fromDate?: string; toDate?: string; minWeightKg?: number; page?: number; size?: number };
/** Viajes abiertos con filtros; `page` base 0. */
export const searchTrips = (token: string, query: TripQuery = {}) =>
  unwrapPage<TripView>(apiFor(token).GET('/api/v1/trips', { params: { query } }) as never);
export const createBooking = (token: string, tripId: string, body: Schemas['CreateTripBookingRequest']) =>
  unwrap(apiFor(token).POST('/api/v1/trips/{id}/bookings', { params: { path: { id: tripId } }, body }));
export const listMyBookings = (token: string) => unwrap(apiFor(token).GET('/api/v1/trip-bookings/mine', { params: { query: {} } }));
export const cancelBooking = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/trip-bookings/{id}/cancel', { params: { path: { id } } }));
