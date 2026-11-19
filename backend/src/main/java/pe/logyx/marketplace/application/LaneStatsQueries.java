package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.marketplace.application.port.out.LanePriceStatsReader;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.shared.domain.BusinessException;

import java.util.Optional;
import java.util.UUID;

/** Mediana adjudicada de la ruta y el tipo de una solicitud (2.8): solo agregado y solo con k >= 5 (si no, vacío). */
@ApplicationScoped
public class LaneStatsQueries {

    /** Ventana del historial, en días (la fija la vista). */
    public static final int WINDOW_DAYS = 90;

    @Inject ShipmentRequestRepository requests;
    @Inject LanePriceStatsReader reader;

    @Transactional
    public Optional<LanePriceStatsReader.LaneStats> forRequest(UUID requestId) {
        var r = requests.findById(requestId).orElseThrow(() -> BusinessException.notFound("Solicitud"));
        var c = r.cargo();
        return reader.find(c.originCity(), c.destinationCity(), c.cargoType());
    }
}
