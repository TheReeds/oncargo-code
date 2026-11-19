import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type PlaceView = Schemas['PlaceView'];
export type PlaceInput = Schemas['PlaceRequest'];

export type PlacesQuery = { city?: string; kind?: string; q?: string; includeArchived?: boolean };
export const listPlaces = (token: string, query: PlacesQuery = {}) => unwrap(apiFor(token).GET('/api/v1/places', { params: { query } }));
export const createPlace = (token: string, body: PlaceInput) => unwrap(apiFor(token).POST('/api/v1/places', { body }));
export const updatePlace = (token: string, id: string, body: PlaceInput) => unwrap(apiFor(token).PATCH('/api/v1/places/{id}', { params: { path: { id } }, body }));
export const archivePlace = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/places/{id}/archive', { params: { path: { id } } }));
export const restorePlace = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/places/{id}/restore', { params: { path: { id } } }));
