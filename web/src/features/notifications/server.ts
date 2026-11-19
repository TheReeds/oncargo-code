import { apiFor, type Schemas } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import { unwrapPage } from '@/lib/api/page';

export type NotificationView = Schemas['NotificationView'];

export type NotificationPreferenceView = Schemas['NotificationPreferenceView'];
export type NotificationPreferenceChange = Schemas['NotificationPreferenceChange'];

/** V16: avisos de la persona (`mine` = solo los de algo que creó). */
export const listNotifications = (token: string, q: { unread?: boolean; mine?: boolean; page?: number; size?: number } = {}) =>
  unwrapPage<NotificationView>(apiFor(token).GET('/api/v1/notifications', { params: { query: { unread: q.unread || undefined, mine: q.mine || undefined, page: q.page ?? 0, size: q.size ?? 20 } } }) as never);
export const unreadCount = async (token: string) => (await unwrap(apiFor(token).GET('/api/v1/notifications/unread-count')))?.unread ?? 0;
export const markRead = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/notifications/{id}/read', { params: { path: { id } } }));
export const markAllRead = (token: string) => unwrap(apiFor(token).POST('/api/v1/notifications/read-all'));
export const getPreferences = (token: string) => unwrap(apiFor(token).GET('/api/v1/notifications/preferences'));
export const updatePreferences = (token: string, preferences: NotificationPreferenceChange[]) =>
  unwrap(apiFor(token).PUT('/api/v1/notifications/preferences', { body: { preferences } }));
