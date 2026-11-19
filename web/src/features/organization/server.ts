// Perfil de la organización y usuarios con rol (V14, ADR-0009). Solo servidor.
import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type OrganizationProfile = Schemas['OrganizationProfile'];
export type OrganizationUser = Schemas['OrganizationUser'];
export type InviteRole = Schemas['InviteUserRequest']['role'];

export const getOrganization = (token: string) => unwrap(apiFor(token).GET('/api/v1/organizations/me'));
export const updateOrganization = (token: string, body: Schemas['UpdateOrganizationRequest']) =>
  unwrap(apiFor(token).PATCH('/api/v1/organizations/me', { body }));

export const listUsers = (token: string) => unwrap(apiFor(token).GET('/api/v1/organizations/me/users'));
export const inviteUser = (token: string, body: Schemas['InviteUserRequest']) => unwrap(apiFor(token).POST('/api/v1/organizations/me/users', { body }));
export const resendInvitation = (token: string, id: string) =>
  unwrap(apiFor(token).POST('/api/v1/organizations/me/users/{id}/resend-invitation', { params: { path: { id } } }));
export const changeUserRole = (token: string, id: string, role: InviteRole) =>
  unwrap(apiFor(token).PATCH('/api/v1/organizations/me/users/{id}', { params: { path: { id } }, body: { role } }));
export const deactivateUser = (token: string, id: string) =>
  unwrap(apiFor(token).POST('/api/v1/organizations/me/users/{id}/deactivate', { params: { path: { id } } }));
export const reactivateUser = (token: string, id: string) =>
  unwrap(apiFor(token).POST('/api/v1/organizations/me/users/{id}/reactivate', { params: { path: { id } } }));

// ----- V24 (Ola 2 zona F, 2.2): preferencias de la organización (el ADMIN escribe; todos leen)
export type OrganizationPreferencesView = Schemas['OrganizationPreferencesView'];
export type UpdateOrganizationPreferences = Schemas['UpdateOrganizationPreferences'];
export const getOrganizationPreferences = (token: string) => unwrap(apiFor(token).GET('/api/v1/organizations/me/preferences'));
export const updateOrganizationPreferences = (token: string, body: UpdateOrganizationPreferences) =>
  unwrap(apiFor(token).PUT('/api/v1/organizations/me/preferences', { body }));
