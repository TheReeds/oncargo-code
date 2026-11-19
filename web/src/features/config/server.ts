import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

/** Tarifario versionado y calendario de feriados (roadmap 2.14, V24). Solo servidor. */
export type TariffVersion = Schemas['TariffVersionView'];
export type TariffValues = Schemas['TariffValues'];
export type CurrentTariff = Schemas['CurrentTariff'];
export type CalendarDay = Schemas['CalendarDayView'];

export const currentTariff = (token: string) => unwrap(apiFor(token).GET('/api/v1/pricing/tariffs/current'));
export const tariffVersions = (token: string) => unwrap(apiFor(token).GET('/api/v1/pricing/tariffs'));
export const createTariff = (token: string, body: Schemas['CreateTariffVersion']) =>
  unwrap(apiFor(token).POST('/api/v1/pricing/tariffs', { body }));
export const cancelTariff = (token: string, version: number, reason: string) =>
  unwrap(apiFor(token).POST('/api/v1/pricing/tariffs/{version}/cancel', { params: { path: { version } }, body: { reason } }));

export const calendarDays = (token: string, query: { from?: string; to?: string; city?: string } = {}) =>
  unwrap(apiFor(token).GET('/api/v1/catalog/calendar', { params: { query } }));
export const addCalendarDay = (token: string, body: Schemas['CreateCalendarDay']) =>
  unwrap(apiFor(token).POST('/api/v1/catalog/calendar', { body }));
export const removeCalendarDay = (token: string, id: string, reason: string) =>
  unwrap(apiFor(token).DELETE('/api/v1/catalog/calendar/{id}', { params: { path: { id }, query: { reason } } }));
