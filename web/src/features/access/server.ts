// «Pedir acceso» a los ADMIN de la organización (roadmap v2 1.7/1.15). Solo servidor.
import { apiFor, type Schemas } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';

export type AccessPermission = Schemas['CreateAccessRequest']['permission'];

export const requestAccess = (token: string, body: Schemas['CreateAccessRequest']) =>
  unwrap(apiFor(token).POST('/api/v1/organizations/me/access-requests', { body }));
