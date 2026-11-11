package pe.logyx.realtime.domain;

/**
 * Tipos de evento del stream {@code GET /api/v1/events/stream} (contrato: {@code docs/api/CHANGELOG.md}).
 * Son estables: agrega tipos nuevos, no renombres. Los clientes ignoran los que no conocen.
 */
public final class RealtimeEventTypes {

    private RealtimeEventTypes() {}

    // Subasta y ofertas
    public static final String BID_PLACED = "bid.placed";
    public static final String AUCTION_UPDATED = "auction.updated";
    public static final String BID_ACCEPTED = "bid.accepted";
    public static final String BID_REJECTED = "bid.rejected";
    public static final String REQUEST_MATCHED = "request.matched";
    public static final String COUNTER_OFFER_UPDATED = "counter_offer.updated";
    // Chat
    public static final String MESSAGE_POSTED = "message.posted";
    // Envíos
    public static final String SHIPMENT_STATUS_CHANGED = "shipment.status_changed";
    public static final String SHIPMENT_STOP_UPDATED = "shipment.stop_updated";
    public static final String SHIPMENT_DRIVER_ASSIGNED = "shipment.driver_assigned";
    // Incidencias
    public static final String INCIDENT_REPORTED = "incident.reported";
    public static final String INCIDENT_UPDATED = "incident.updated";
    // Notificaciones
    public static final String NOTIFICATION_CREATED = "notification.created";
    // Reputación
    public static final String REVIEW_SUBMITTED = "review.submitted";
    // Viajes y Smart Load
    public static final String TRIP_BOOKING_UPDATED = "trip_booking.updated";
    public static final String TRIP_CANCELLED = "trip.cancelled";
    public static final String SMART_LOAD_SUGGESTED = "smart_load.suggested";
    public static final String SMART_LOAD_ACCEPTED = "smart_load.accepted";
    // Control del stream (no se persisten)
    public static final String STREAM_READY = "stream.ready";
    public static final String STREAM_RESYNC = "stream.resync";
    public static final String STREAM_EXPIRED = "stream.expired";
}
