package pe.logyx.shipment.application.port.out;

import pe.logyx.shipment.domain.model.Shipment;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {
    Optional<Shipment> findById(UUID id);
    Optional<Shipment> findForCarrier(UUID id, UUID carrierOrgId);
    Optional<Shipment> findForDriver(UUID id, UUID driverId);
    /** Más recientes primero. */
    List<Shipment> listByCarrier(UUID carrierOrgId);
    /** Más recientes primero. */
    List<Shipment> listByRequester(UUID requesterOrgId);
    /** Pendientes o en tránsito, más antiguos primero. */
    List<Shipment> listActiveByDriver(UUID driverId);
    /** Envíos de la organización (como transportista o como solicitante) en alguno de los estados. */
    long countByStatus(UUID orgId, boolean asCarrier, Collection<Shipment.Status> statuses);
    /** Envíos entregados desde {@code since} (inclusive). */
    long countDeliveredSince(UUID orgId, boolean asCarrier, OffsetDateTime since);
    /** Todos los visibles (RLS) en ese estado. */
    long countAllByStatus(Shipment.Status status);
    /** Listado global (operador, vía RLS), más recientes primero. */
    pe.logyx.shared.domain.Paged<Shipment> searchAll(pe.logyx.shipment.application.OperatorShipmentFilter filter,
                                                      pe.logyx.shared.domain.PageRequest page);
    List<Shipment> listByIds(Collection<UUID> ids);
    /** Inserta o actualiza. */
    void save(Shipment shipment);
}
