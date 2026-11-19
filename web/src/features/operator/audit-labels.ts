// Etiquetas en español de la auditoría. Lo que no esté aquí se muestra con su código (la lista de la API es la fuente de verdad).
export const AUDIT_ACTION_LABELS: Record<string, string> = {
  REGISTER: 'Registro de empresa',
  // Ola 2 zona X (V23)
  STOP_REGISTERED_BY_OFFICE: 'Parada registrada por la oficina',
  PICKUP_REQUIREMENTS_CHANGED: 'Requisitos del recojo cambiados',
  ADJUSTMENT_PROPOSED: 'Ajuste propuesto',
  ADJUSTMENT_ACCEPTED: 'Ajuste aceptado',
  ADJUSTMENT_REJECTED: 'Ajuste rechazado',
  ADJUSTMENT_WITHDRAWN: 'Ajuste retirado',
  ADJUSTMENT_ESCALATED: 'Ajuste escalado a OnCargo',
  INCIDENT_WITHDRAWN: 'Incidencia retirada',
  INCIDENT_REOPENED: 'Incidencia reabierta',
  INCIDENT_ASSIGNED: 'Incidencia asignada',
  INCIDENT_UNASSIGNED: 'Incidencia sin responsable',
  USER_SUSPENDED_BY_OPERATOR: 'Usuario suspendido por OnCargo',
  USER_REACTIVATED_BY_OPERATOR: 'Usuario reactivado por OnCargo',
  ORGANIZATION_LEGAL_NAME_CHANGED: 'Razón social corregida por OnCargo',
  ORGANIZATION_SUCCEEDED: 'Organización sucesora por cambio de RUC',
  ORG_ACCESS_REQUESTED: 'Pedido de acceso desde el registro',
  USER_PROVISIONED: 'Usuario creado',
  USER_INVITED: 'Usuario invitado',
  USER_INVITATION_RESENT: 'Invitación reenviada',
  USER_INVITATION_ACCEPTED: 'Invitación aceptada',
  USER_ROLE_CHANGED: 'Rol de usuario cambiado',
  USER_DEACTIVATED: 'Usuario desactivado',
  USER_REACTIVATED: 'Usuario reactivado',
  ORGANIZATION_UPDATED: 'Datos de la empresa actualizados',
  LOGOUT_ALL: 'Sesiones cerradas en todos los dispositivos',
  ORGANIZATION_VERIFICATION_RESUBMITTED: 'Empresa corrigió sus datos y volvió a revisión',
  PASSWORD_CHANGED: 'Contraseña cambiada',
  PASSWORD_RESET_REQUESTED: 'Restablecimiento de contraseña pedido',
  PASSWORD_RESET_COMPLETED: 'Contraseña restablecida',
  DRIVER_REACTIVATED: 'Conductor reactivado',
  DRIVER_PASSWORD_RESET: 'Contraseña de conductor restablecida',
  OPERATOR_PROVISIONED: 'Operador creado',
  REQUEST_PUBLISHED: 'Solicitud publicada',
  REQUEST_CANCELLED: 'Solicitud cancelada',
  REQUEST_REPUBLISHED: 'Solicitud republicada',
  BID_ACCEPTED: 'Oferta aceptada',
  BID_WITHDRAWN: 'Oferta retirada',
  COUNTER_OFFER_PROPOSED: 'Contra-oferta propuesta',
  COUNTER_OFFER_ACCEPTED: 'Contra-oferta aceptada',
  SHIPMENT_CANCELLED: 'Envío cancelado',
  SHIPMENT_DELIVERED: 'Envío entregado',
  SHARE_LINK_CREATED: 'Enlace de seguimiento creado',
  SHARE_LINK_REVOKED: 'Enlace de seguimiento revocado',
  SHIPMENT_VEHICLE_CHANGED: 'Vehículo del envío cambiado',
  DRIVER_ASSIGNED: 'Conductor asignado',
  DRIVER_CREATED: 'Conductor registrado',
  DRIVER_DEACTIVATED: 'Conductor desactivado',
  DOCUMENT_UPLOADED: 'Documento subido',
  REVIEW_SUBMITTED: 'Calificación enviada',
  INCIDENT_REPORTED: 'Incidencia reportada',
  INCIDENT_IN_REVIEW: 'Incidencia en revisión',
  INCIDENT_RESOLVED: 'Incidencia resuelta',
  INCIDENT_CLOSED: 'Incidencia cerrada',
  ORGANIZATION_VERIFIED: 'Empresa verificada',
  ORGANIZATION_REJECTED: 'Empresa rechazada',
  ORGANIZATION_SUSPENDED: 'Empresa suspendida',
  TRIP_PUBLISHED: 'Viaje publicado',
  TRIP_CLOSED: 'Viaje cerrado',
  TRIP_CANCELLED: 'Viaje cancelado',
  TRIP_BOOKING_REQUESTED: 'Reserva solicitada',
  TRIP_BOOKING_CONFIRMED: 'Reserva confirmada',
  TRIP_BOOKING_REJECTED: 'Reserva rechazada',
  TRIP_BOOKING_CANCELLED: 'Reserva cancelada',
  SMART_LOAD_ACCEPTED: 'Plan Smart Load aceptado',
  SMART_LOAD_REJECTED: 'Plan Smart Load rechazado',
};

/** Tipos de entidad de `AuditRow.entity` (filtro `entity` de la API). */
export const AUDIT_ENTITY_LABELS: Record<string, string> = {
  shipment_request: 'Solicitud',
  bid: 'Oferta',
  counter_offer: 'Contra-oferta',
  shipment: 'Envío',
  organization: 'Organización',
  profile: 'Usuario',
  review: 'Calificación',
  incident: 'Incidencia',
  document: 'Documento',
  trip: 'Viaje',
  trip_booking: 'Reserva',
  smart_load: 'Smart Load',
};

export const auditActionLabel = (a?: string) => (a ? (AUDIT_ACTION_LABELS[a] ?? a) : '—');
export const auditEntityLabel = (e?: string) => (e ? (AUDIT_ENTITY_LABELS[e] ?? e) : '—');

/** Pantalla del operador a la que se puede saltar desde una fila (solo las que existen). */
export function auditEntityHref(entity?: string, entityId?: string): string | null {
  if (!entity || !entityId) return null;
  if (entity === 'shipment_request') return `/operador/solicitudes/${entityId}`;
  if (entity === 'shipment') return `/operador/envios/${entityId}`;
  if (entity === 'organization') return `/organizaciones/${entityId}`;
  return null;
}
