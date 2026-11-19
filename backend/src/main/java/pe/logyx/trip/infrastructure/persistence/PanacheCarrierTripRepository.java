package pe.logyx.trip.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.trip.application.TripFilter;
import pe.logyx.trip.application.port.out.CarrierTripRepository;
import pe.logyx.trip.domain.model.CarrierTrip;
import pe.logyx.trip.domain.model.TripStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheCarrierTripRepository implements CarrierTripRepository {

    @Override
    public Optional<CarrierTrip> findById(UUID id) {
        return CarrierTripEntity.<CarrierTripEntity>findByIdOptional(id).map(CarrierTripEntity::toDomain);
    }

    @Override
    public Optional<CarrierTrip> findByIdForUpdate(UUID id) {
        return CarrierTripEntity.<CarrierTripEntity>find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional().map(CarrierTripEntity::toDomain);
    }

    @Override
    public Optional<CarrierTrip> findOwned(UUID id, UUID carrierOrgId) {
        return CarrierTripEntity.<CarrierTripEntity>find("id = ?1 and carrierOrgId = ?2", id, carrierOrgId)
                .firstResultOptional().map(CarrierTripEntity::toDomain);
    }

    @Override
    public List<CarrierTrip> listByCarrier(UUID carrierOrgId) {
        return map(CarrierTripEntity.list("carrierOrgId = ?1 order by departureAt asc, id", carrierOrgId));
    }

    @Override
    public Paged<CarrierTrip> searchOpen(TripFilter f, OffsetDateTime now, PageRequest page) {
        StringBuilder where = new StringBuilder("status in ?1 and departureAt > ?2");
        List<Object> params = new ArrayList<>(List.of(List.of(TripStatus.OPEN, TripStatus.FILLING), now));
        if (f.originCity() != null) where.append(" and originCity = ?").append(add(params, f.originCity()));
        if (f.destinationCity() != null) where.append(" and destinationCity = ?").append(add(params, f.destinationCity()));
        if (f.departureFrom() != null) where.append(" and departureAt >= ?").append(add(params, f.departureFrom()));
        if (f.departureBefore() != null) where.append(" and departureAt < ?").append(add(params, f.departureBefore()));
        // capacidad restante = capacidad − confirmado; siempre > 0 y, si se pide, ≥ minWeightKg
        BigDecimal min = f.minWeightKg() != null ? f.minWeightKg() : new BigDecimal("0.01");
        where.append(" and (capacityKg - confirmedWeightKg) >= ?").append(add(params, min));
        String order = " order by departureAt asc, id";
        var query = CarrierTripEntity.<CarrierTripEntity>find(where + order, params.toArray())
                .page(page.page(), page.size());
        long total = CarrierTripEntity.count(where.toString(), params.toArray());
        return new Paged<>(map(query.list()), page, total);
    }

    @Override
    public long countOpenByVehicle(UUID vehicleId) {
        return CarrierTripEntity.count("vehicleId = ?1 and status in ?2", vehicleId,
                List.of(TripStatus.OPEN, TripStatus.FILLING));
    }

    @Override
    public long countByCarrierAndStatus(UUID carrierOrgId, List<TripStatus> statuses) {
        return CarrierTripEntity.count("carrierOrgId = ?1 and status in ?2", carrierOrgId, statuses);
    }

    @Override
    public List<CarrierTrip> listByStatusDepartedBefore(List<TripStatus> statuses, OffsetDateTime cutoff) {
        return map(CarrierTripEntity.list("status in ?1 and departureAt < ?2", statuses, cutoff));
    }

    @Override
    public void save(CarrierTrip trip) {
        CarrierTripEntity e = CarrierTripEntity.findById(trip.id());
        if (e == null) {
            e = new CarrierTripEntity();
            e.apply(trip);
            e.persist();
        } else {
            e.apply(trip);
        }
    }

    private static int add(List<Object> params, Object value) {
        params.add(value);
        return params.size();
    }

    private static List<CarrierTrip> map(List<CarrierTripEntity> list) {
        return list.stream().map(CarrierTripEntity::toDomain).toList();
    }
}
