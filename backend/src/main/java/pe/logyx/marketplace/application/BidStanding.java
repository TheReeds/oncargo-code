package pe.logyx.marketplace.application;

import pe.logyx.marketplace.domain.BidStandingBand;

/**
 * Posición de la oferta de un transportista sin revelar montos ajenos (RN04, ofertas selladas).
 *
 * @param rank          1 = la mejor (menor monto; empate: la más antigua) entre las ofertas no retiradas.
 * @param bidCount      ofertas no retiradas de la subasta (incluye la propia).
 * @param best          {@code rank == 1}.
 * @param vsBest        banda frente a la mejor; {@code null} cuando la solicitud ya se adjudicó o canceló.
 * @param finalStanding {@code true} si la subasta ya cerró o la solicitud dejó de estar publicada: es la posición final.
 */
public record BidStanding(int rank, int bidCount, boolean best, BidStandingBand vsBest, boolean finalStanding) {}
