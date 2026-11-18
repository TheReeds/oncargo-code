// Contraseñas y sesiones (V14, ADR-0009): olvido, activación/restablecimiento por enlace, cambio y cierre global.
// Solo servidor. El token del enlace viaja siempre en el cuerpo de la petición, nunca en un path.
import { ApiError, apiClient, unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type PasswordTokenInfo = Schemas['PasswordTokenInfo'];

export const forgotPassword = (email: string, clientIp?: string) =>
  unwrap(apiClient(undefined, clientIp).POST('/api/v1/auth/forgot-password', { body: { email } }));

export const inspectPasswordToken = (token: string, clientIp?: string) =>
  unwrap(apiClient(undefined, clientIp).POST('/api/v1/auth/password-tokens/inspect', { body: { token } }));

export const resetPassword = (body: Schemas['ResetPasswordRequest'], clientIp?: string) =>
  unwrap(apiClient(undefined, clientIp).POST('/api/v1/auth/reset-password', { body }));

export const changePassword = (token: string, body: Schemas['ChangePasswordRequest'], clientIp?: string) =>
  unwrap(apiClient(token, clientIp).POST('/api/v1/auth/change-password', { body }));

export async function logoutAll(token: string): Promise<void> {
  let res;
  try {
    res = await apiClient(token).POST('/api/v1/auth/logout-all');
  } catch {
    throw new ApiError(0, 'NETWORK', 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  // 204 sin cuerpo; un 401 aquí también significa "sesión ya cerrada".
  if (!res.response.ok && res.response.status !== 401) {
    const e = (res.error ?? {}) as { error?: { code?: string; message?: string } };
    throw new ApiError(res.response.status, e.error?.code ?? `HTTP-${res.response.status}`, e.error?.message ?? 'No pudimos cerrar tus sesiones. Intenta nuevamente.');
  }
}

export { PASSWORD_MIN, PASSWORD_MAX } from './password-rules';
