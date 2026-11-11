package pe.logyx.trip.application;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.trip.application.port.out.CarrierTripRepository;
import pe.logyx.trip.application.port.out.SmartLoadRepository;
import pe.logyx.trip.domain.model.CarrierTrip;
import pe.logyx.trip.domain.model.TripStatus;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ciclo de vida automático (lo invoca el scheduler sin usuario: rol dueño, cruza organizaciones). Viajes OPEN/FILLING
 * cuya salida ya pasó → CLOSED (las reservas pendientes se rechazan); CLOSED con salida hace más de 24 h → COMPLETED.
 * Sugerencias Smart Load PENDING vencidas → EXPIRED.
 */
@ApplicationScoped
public class TripMaintenanceUseCase {

    static final String DEPARTED_NOTE = "El viaje ya salió sin confirmar esta reserva";
    static final Duration COMPLETE_AFTER = Duration.ofHours(24);

    @Inject CarrierTripRepository trips;
    @Inject SmartLoadRepository smartLoads;
    @Inject BookingSweeper sweeper;

    @Transactional
    public int run() {
        OffsetDateTime now = OffsetDateTime.now();
        int changed = 0;
        for (CarrierTrip t : trips.listByStatusDepartedBefore(List.of(TripStatus.OPEN, TripStatus.FILLING), now)) {
            t.close();
            sweeper.rejectPending(t, DEPARTED_NOTE);
            trips.save(t);
            changed++;
        }
        for (CarrierTrip t : trips.listByStatusDepartedBefore(List.of(TripStatus.CLOSED), now.minus(COMPLETE_AFTER))) {
            t.complete();
            trips.save(t);
            changed++;
        }
        for (var s : smartLoads.listPendingExpiredBefore(now)) {
            s.expire(now);
            smartLoads.save(s);
            changed++;
        }
        if (changed > 0) Log.infof("Mantenimiento de viajes: %d cambios", changed);
        return changed;
    }
}
