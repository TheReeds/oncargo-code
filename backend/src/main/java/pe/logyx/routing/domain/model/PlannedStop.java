package pe.logyx.routing.domain.model;

import pe.logyx.catalog.domain.City;

/**
 * Parada de un plan con recojos y entregas. {@code load} es el índice de la carga en la lista de entrada
 * ({@code -1} para el punto de partida o de llegada del vehículo).
 */
public record PlannedStop(City city, int load, Kind kind) {

    public enum Kind { START, PICKUP, DELIVERY, END }
}
