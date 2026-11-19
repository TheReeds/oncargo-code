import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type TemplateView = Schemas['TemplateView'];
export type TemplateInput = Schemas['TemplateRequest'];
export type ScheduleInput = Schemas['TemplateScheduleRequest'];
export type DraftView = Schemas['TemplateDraftView'];
export type ScheduleRunView = Schemas['TemplateRunView'];

export const listTemplates = (token: string, includeArchived = true) =>
  unwrap(apiFor(token).GET('/api/v1/request-templates', { params: { query: { includeArchived } } }));
export const getTemplate = (token: string, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/request-templates/{id}', { params: { path: { id } } }));
export const listDrafts = (token: string) => unwrap(apiFor(token).GET('/api/v1/request-templates/drafts'));
export const createTemplate = (token: string, body: TemplateInput) => unwrap(apiFor(token).POST('/api/v1/request-templates', { body }));
export const updateTemplate = (token: string, id: string, body: TemplateInput) =>
  unwrap(apiFor(token).PUT('/api/v1/request-templates/{id}', { params: { path: { id } }, body }));
export const templateFromRequest = (token: string, requestId: string, name: string, invitePreferred?: boolean) =>
  unwrap(apiFor(token).POST('/api/v1/request-templates/from-request/{requestId}', { params: { path: { requestId } }, body: { name, invitePreferred } }));
export const archiveTemplate = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/request-templates/{id}/archive', { params: { path: { id } } }));
export const restoreTemplate = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/request-templates/{id}/restore', { params: { path: { id } } }));
export const publishTemplate = (token: string, id: string, pickupDate: string, idempotencyKey?: string) =>
  unwrap(apiFor(token).POST('/api/v1/request-templates/{id}/publish', { params: { path: { id }, header: { 'Idempotency-Key': idempotencyKey } }, body: { pickupDate } }));
export const setSchedule = (token: string, id: string, body: ScheduleInput) =>
  unwrap(apiFor(token).PUT('/api/v1/request-templates/{id}/schedule', { params: { path: { id } }, body }));
export const pauseSchedule = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/request-templates/{id}/schedule/pause', { params: { path: { id } } }));
export const resumeSchedule = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/request-templates/{id}/schedule/resume', { params: { path: { id } } }));
export const removeSchedule = (token: string, id: string) => unwrap(apiFor(token).DELETE('/api/v1/request-templates/{id}/schedule', { params: { path: { id } } }));
export const publishDraft = (token: string, runId: string) => unwrap(apiFor(token).POST('/api/v1/request-templates/runs/{runId}/publish', { params: { path: { runId } } }));
export const discardDraft = (token: string, runId: string) => unwrap(apiFor(token).POST('/api/v1/request-templates/runs/{runId}/discard', { params: { path: { runId } } }));
