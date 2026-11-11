package pe.logyx.trip.domain.model;

/** Estado de un viaje publicado. Las transiciones las gobierna {@code TripStatusPolicy}. */
public enum TripStatus { OPEN, FILLING, CLOSED, COMPLETED, CANCELLED }
