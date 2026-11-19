package pe.logyx.marketplace.application;

/** Totales de "Mis solicitudes" por estado (para las pestañas de filtro rápido), sin filtros aplicados. */
public record RequestCounts(long all, long published, long matched, long cancelled, long withBids) {}
