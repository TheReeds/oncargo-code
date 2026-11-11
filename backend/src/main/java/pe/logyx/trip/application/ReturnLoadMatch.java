package pe.logyx.trip.application;

import pe.logyx.marketplace.application.RequestSummary;
import pe.logyx.trip.domain.ReturnLoadPolicy;

/** Carga de retorno sugerida: el tramo del transportista, la solicitud y si ya ofertó en ella. */
public record ReturnLoadMatch(ReturnLoadPolicy.Match match, RequestSummary request) {
    public boolean alreadyBid() { return request.myBid() != null; }
}
