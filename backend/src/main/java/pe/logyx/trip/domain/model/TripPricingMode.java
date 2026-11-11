package pe.logyx.trip.domain.model;

/** Cómo cobra el transportista un viaje publicado (ver {@code TripPricingPolicy}). */
public enum TripPricingMode {
    /** {@code rate} = soles por kg. */
    PER_KG,
    /** {@code rate} = tarifa por todo el tramo (capacidad completa); cada reserva paga la parte proporcional. */
    PER_TRIP
}
