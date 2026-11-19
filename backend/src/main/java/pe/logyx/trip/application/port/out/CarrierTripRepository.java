package pe.logyx.trip.application.port.out;

import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.trip.application.TripFilter;
import pe.logyx.trip.domain.model.CarrierTrip;
import pe.logyx.trip.domain.model.TripStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarrierTripRepository {
    /** Visible para el usuario actual (RLS: dueño, PYME si está abierto o si reservó, operador). */
    Optional<CarrierTrip> findById(UUID id);
    /** Con bloqueo pesimista: serializa confirmaciones/cancelaciones sobre la capacidad. */
    Optional<CarrierTrip> findByIdForUpdate(UUID id);
    Optional<CarrierTrip> findOwned(UUID id, UUID carrierOrgId);
    /** Más próximos a salir primero. */
    List<CarrierTrip> listByCarrier(UUID carrierOrgId);
    /** OPEN/FILLING con salida futura, capacidad restante suficiente y que cumplen el filtro; salida más próxima primero. */
    Paged<CarrierTrip> searchOpen(TripFilter filter, OffsetDateTime now, PageRequest page);
    /** Viajes OPEN/FILLING del vehículo (RN12: bloquean archivarlo). */
    long countOpenByVehicle(UUID vehicleId);
    long countByCarrierAndStatus(UUID carrierOrgId, List<TripStatus> statuses);
    /** Con estado en {@code statuses} y salida anterior a {@code cutoff} (mantenimiento programado). */
    List<CarrierTrip> listByStatusDepartedBefore(List<TripStatus> statuses, OffsetDateTime cutoff);
    /** Inserta o actualiza. */
    void save(CarrierTrip trip);
}
