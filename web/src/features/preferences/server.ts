import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type CarrierPreferenceView = Schemas['CarrierPreferenceView'];
export type BackupOptionsView = Schemas['BackupOptionsView'];
export type BackupItemView = Schemas['BackupItemView'];
export type BackupRequestView = Schemas['BackupRequestView'];
export type PreferenceKind = 'PREFERRED' | 'BLOCKED';

export const listPreferences = (token: string, kind?: PreferenceKind) =>
  unwrap(apiFor(token).GET('/api/v1/carrier-preferences', { params: { query: { kind } } }));
export const markCarrier = (token: string, carrierOrgId: string, kind: PreferenceKind) =>
  unwrap(apiFor(token).PUT('/api/v1/carrier-preferences/{carrierOrgId}', { params: { path: { carrierOrgId } }, body: { kind } }));
export const unmarkCarrier = (token: string, carrierOrgId: string) =>
  unwrap(apiFor(token).DELETE('/api/v1/carrier-preferences/{carrierOrgId}', { params: { path: { carrierOrgId } } }));
export const invitePreferred = (token: string, id: string) =>
  unwrap(apiFor(token).POST('/api/v1/shipment-requests/{id}/invite-preferred', { params: { path: { id } } }));

export const backupOptions = (token: string, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/shipment-requests/{id}/backup-bids', { params: { path: { id } } }));
export const requestBackup = (token: string, bidId: string) =>
  unwrap(apiFor(token).POST('/api/v1/bids/{id}/backup/request', { params: { path: { id: bidId } } }));
export const awardBackup = (token: string, bidId: string) =>
  unwrap(apiFor(token).POST('/api/v1/bids/{id}/backup/award', { params: { path: { id: bidId } } }));
export const backupRequests = (token: string) => unwrap(apiFor(token).GET('/api/v1/bids/backup-requests'));
export const confirmBackup = (token: string, bidId: string) =>
  unwrap(apiFor(token).POST('/api/v1/bids/{id}/backup/confirm', { params: { path: { id: bidId } } }));
export const declineBackup = (token: string, bidId: string) =>
  unwrap(apiFor(token).POST('/api/v1/bids/{id}/backup/decline', { params: { path: { id: bidId } } }));
