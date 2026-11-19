package pe.logyx.notification.domain.model;

import pe.logyx.shared.tenancy.OrgType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Agrupa los tipos de aviso para las preferencias de cada persona (V16): la matriz categoría × canal de
 * {@code /cuenta/avisos}. Un tipo pertenece a exactamente una categoría ({@link #of}). {@code audience} = tipos de
 * organización a los que les llega algún aviso de la categoría (la pantalla solo muestra las suyas).
 */
public enum NotificationCategory {
    BIDS("Ofertas", "Ofertas nuevas, retiradas y ofertas consolidadas por tus cargas.", EnumSet.of(OrgType.PYME)),
    AWARDS("Adjudicaciones", "Cuando una oferta tuya gana o no es elegida.", EnumSet.of(OrgType.CARRIER)),
    REQUESTS("Solicitudes y subastas", "Cierre de subastas y solicitudes canceladas.",
            EnumSet.of(OrgType.PYME, OrgType.CARRIER, OrgType.OPERATOR)),
    NEGOTIATION("Mensajes y contraofertas", "Mensajes del chat y contraofertas.", EnumSet.of(OrgType.PYME, OrgType.CARRIER)),
    SHIPMENTS("Envíos", "Recojo, entrega, cancelaciones y cambios de vehículo o conductor.",
            EnumSet.of(OrgType.PYME, OrgType.CARRIER)),
    INCIDENTS("Incidencias", "Incidencias reportadas y resueltas.", EnumSet.of(OrgType.PYME, OrgType.CARRIER, OrgType.OPERATOR)),
    REVIEWS("Calificaciones", "Cuando te califican después de una entrega.", EnumSet.of(OrgType.PYME, OrgType.CARRIER)),
    TRIPS("Viajes y reservas", "Reservas en viajes publicados: pedidas, confirmadas, rechazadas o canceladas.",
            EnumSet.of(OrgType.PYME, OrgType.CARRIER)),
    SMART_LOAD("Cargas combinadas", "Sugerencias de Smart Load para tu flota.", EnumSet.of(OrgType.CARRIER)),
    ACCOUNT("Cuenta y accesos", "Verificación de tu empresa y pedidos de acceso de tu equipo.",
            EnumSet.of(OrgType.PYME, OrgType.CARRIER, OrgType.OPERATOR));

    private final String label;
    private final String description;
    private final Set<OrgType> audience;

    NotificationCategory(String label, String description, Set<OrgType> audience) {
        this.label = label;
        this.description = description;
        this.audience = audience;
    }

    public String label() { return label; }
    public String description() { return description; }
    public boolean appliesTo(OrgType type) { return type != null && audience.contains(type); }

    public static NotificationCategory of(NotificationType t) {
        return switch (t) {
            case NEW_BID, BID_WITHDRAWN, SMART_LOAD_ACCEPTED -> BIDS;
            case BID_ACCEPTED, BID_REJECTED -> AWARDS;
            case AUCTION_CLOSED, REQUEST_CANCELLED -> REQUESTS;
            case NEW_MESSAGE, COUNTER_OFFER -> NEGOTIATION;
            case SHIPMENT_STATUS_CHANGE, SHIPMENT_CANCELLED -> SHIPMENTS;
            case INCIDENT_REPORTED, INCIDENT_RESOLVED -> INCIDENTS;
            case NEW_REVIEW -> REVIEWS;
            case TRIP_BOOKING_REQUESTED, TRIP_BOOKING_CONFIRMED, TRIP_BOOKING_REJECTED, TRIP_BOOKING_CANCELLED,
                 TRIP_CANCELLED -> TRIPS;
            case SMART_LOAD_SUGGESTED -> SMART_LOAD;
            case ORGANIZATION_VERIFICATION, ACCESS_REQUESTED -> ACCOUNT;
        };
    }
}
