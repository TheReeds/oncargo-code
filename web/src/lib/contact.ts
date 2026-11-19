/**
 * Canales de contacto públicos. Único punto de cambio (decisión del dueño, 2026-11-17): soporte y privacidad viven en
 * el dominio oncargo.tech.
 */
/** Soporte general: verificación, suspensión, cambios de razón social, dudas de uso. */
export const SUPPORT_EMAIL = 'soporte@oncargo.tech';
/** Derechos ARCO y consultas de datos personales (Ley 29733). */
export const PRIVACY_EMAIL = 'privacidad@oncargo.tech';
/** Horario de atención al cliente (hora de Lima). Por confirmar con el dueño del producto. */
export const SUPPORT_HOURS = 'Lunes a viernes, de 8:00 a 18:00 (hora de Lima)';

/**
 * Número de WhatsApp Business solo con dígitos y código de país (p. ej. 51987654321), de la variable de entorno
 * `PUBLIC_WHATSAPP_NUMBER`. Sin número configurado no se muestra el botón (nunca un número inventado).
 */
const rawWhatsapp = String(import.meta.env.PUBLIC_WHATSAPP_NUMBER ?? '').replace(/\D/g, '');
export const WHATSAPP_NUMBER: string | null = rawWhatsapp.length >= 9 ? rawWhatsapp : null;

/** Enlace `wa.me` con un texto inicial; `null` si no hay número configurado. */
export function whatsappUrl(message = 'Hola, quiero información sobre OnCargo.'): string | null {
  return WHATSAPP_NUMBER ? `https://wa.me/${WHATSAPP_NUMBER}?text=${encodeURIComponent(message)}` : null;
}

/** Correo para pedir aviso cuando OnCargo llegue a una ciudad nueva (primera versión: un `mailto`). */
export const notifyCityHref = (city = '') =>
  `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent('Avísenme cuando lleguen a mi ciudad')}&body=${encodeURIComponent(`Hola, quiero que me avisen cuando OnCargo llegue a: ${city}`)}`;
