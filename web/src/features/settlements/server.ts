import { apiFor } from '@/lib/api/server';
import { ApiError, unwrap } from '@/lib/api/client';
import { unwrapPage } from '@/lib/api/page';
import type { Schemas } from '@/lib/api/server';

export type SettlementRow = Schemas['SettlementRowView'];
export type SettlementSummary = Schemas['SettlementSummaryView'];
export type SettlementView = Schemas['SettlementView'];
export type SettlementStatus = Schemas['SettlementStatus'];
export type PaymentMethod = Schemas['PaymentMethod'];
export type CommissionCollection = Schemas['CommissionCollectionView'];
export type PayoutAccount = Schemas['PayoutAccountView'];

export type SettlementsQuery = {
  status?: string; overdue?: boolean; carrierOrgId?: string; commissionPending?: boolean; from?: string; to?: string; page?: number; size?: number;
};
const clean = <T extends object>(q: T) => Object.fromEntries(Object.entries(q).filter(([, v]) => v !== undefined && v !== '' && v !== false)) as T;

/** Cobros de envíos entregados (`page` 0-based): «Por pagar» (PYME), «Por cobrar» (transportista) o todos (operador). */
export const listSettlements = (token: string, query: SettlementsQuery = {}) =>
  unwrapPage<SettlementRow>(apiFor(token).GET('/api/v1/settlements', { params: { query: clean(query) } }) as never);
export const settlementSummary = (token: string) => unwrap(apiFor(token).GET('/api/v1/settlements/summary'));

/** Cobro de un envío. `null` si el envío aún no se entregó (409 `LOGYX-STL-001`): no se muestra la tarjeta. */
export async function getSettlement(token: string, id: string): Promise<SettlementView | null> {
  try {
    return await unwrap(apiFor(token).GET('/api/v1/shipments/{id}/settlement', { params: { path: { id } } }));
  } catch (e) {
    if (e instanceof ApiError && e.code === 'LOGYX-STL-001') return null;
    throw e;
  }
}

const path = (id: string) => ({ params: { path: { id } } });
export const registerInvoice = (token: string, id: string, body: Schemas['RegisterInvoice']) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/settlement/invoice', { ...path(id), body }));
export const registerPayment = (token: string, id: string, body: Schemas['RegisterPayment']) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/settlement/payment', { ...path(id), body }));
export const confirmSettlement = (token: string, id: string) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/settlement/confirm', path(id)));
export const disputeSettlement = (token: string, id: string, reason: string) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/settlement/dispute', { ...path(id), body: { reason } }));
export const resolveSettlement = (token: string, id: string, body: Schemas['ResolveSettlementDispute']) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/settlement/resolve', { ...path(id), body }));

export const collectCommission = (token: string, body: Schemas['CollectCommission']) =>
  unwrap(apiFor(token).POST('/api/v1/settlements/commission-collections', { body }));
export const listCommissionCollections = (token: string, query: { carrierOrgId?: string; page?: number; size?: number } = {}) =>
  unwrapPage<CommissionCollection>(apiFor(token).GET('/api/v1/settlements/commission-collections', { params: { query: clean(query) } }) as never);

// ----- Datos de cobro del transportista (2.12)
export const listPayoutAccounts = (token: string) => unwrap(apiFor(token).GET('/api/v1/organizations/me/payout-accounts'));
export const createPayoutAccount = (token: string, body: Schemas['CreatePayoutAccount']) =>
  unwrap(apiFor(token).POST('/api/v1/organizations/me/payout-accounts', { body }));
export const makePayoutPrimary = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/organizations/me/payout-accounts/{id}/primary', path(id)));
export const archivePayoutAccount = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/organizations/me/payout-accounts/{id}/archive', path(id)));
