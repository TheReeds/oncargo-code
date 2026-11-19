import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type ReportOverview = Schemas['ReportOverviewView'];
export type ReportTotals = Schemas['ReportTotalsView'];
export type ReportGroup = Schemas['ReportGroupView'];
export type Statement = Schemas['StatementView'];

/** Reporte del período (2.11). Sin fechas = últimos 6 meses; rango máximo 400 días (`LOGYX-VAL-001`). */
export const getOverview = (token: string, from?: string, to?: string) =>
  unwrap(apiFor(token).GET('/api/v1/reports/overview', { params: { query: { from: from || undefined, to: to || undefined } } }));

/** Liquidación de comisión (transportista) o resumen de costos (PYME) del mes `yyyy-MM`; el operador indica la organización. */
export const getStatement = (token: string, period: string, organizationId?: string) =>
  unwrap(apiFor(token).GET('/api/v1/reports/statements/{period}', { params: { path: { period }, query: { organizationId: organizationId || undefined } } }));
