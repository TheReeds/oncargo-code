import { apiClient, unwrap } from '@/lib/api/client';
import type { Schemas, UserInfo } from '@/lib/api/server';

export const login = (email: string, password: string, clientIp?: string) =>
  unwrap(apiClient(undefined, clientIp).POST('/api/v1/auth/login', { body: { email, password } }));

export const register = (body: Schemas['RegisterRequest'], clientIp?: string) =>
  unwrap(apiClient(undefined, clientIp).POST('/api/v1/auth/register', { body }));

export async function me(token: string): Promise<UserInfo | null> {
  try {
    return await unwrap(apiClient(token).GET('/api/v1/auth/me'));
  } catch {
    return null;
  }
}
