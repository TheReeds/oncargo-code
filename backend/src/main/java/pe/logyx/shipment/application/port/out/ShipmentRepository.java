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
    /** Entregados o cancelados del conductor, más recientes primero (F22). */
    List<Shipment> listHistoryByDriver(UUID driverId);
    /** Pendientes o en tránsito con ese vehículo. */
    List<Shipment> listActiveByVehicle(UUID vehicleId);
    /**
     * Bloqueo consultivo de transacción ({@code pg_advisory_xact_lock}) sobre un recurso (vehículo o conductor):
     * serializa las asignaciones concurrentes del mismo recurso hasta el commit (F11).
     */
    void lockResource(String key);
    /** Envíos de la organización (como transportista o como solicitante) en alguno de los estados. */
    long countByStatus(UUID orgId, boolean asCarrier, Collection<Shipment.Status> statuses);
    /** Envíos entregados desde {@code since} (inclusive). */
    long countDeliveredSince(UUID orgId, boolean asCarrier, OffsetDateTime since);
    /** Todos los visibles (RLS) en ese estado. */
    long countAllByStatus(Shipment.Status status);
    /** Suma del precio acordado de los envíos en esos estados (operador, vía RLS); 0 si no hay. */
    java.math.BigDecimal sumAgreedPrice(Collection<Shipment.Status> statuses);
    /** Listado global (operador, vía RLS), más recientes primero. */
    pe.logyx.shared.domain.Paged<Shipment> searchAll(pe.logyx.shipment.application.OperatorShipmentFilter filter,
                                                      pe.logyx.shared.domain.PageRequest page);
    List<Shipment> listByIds(Collection<UUID> ids);
    /**
     * «Mis envíos» filtrados en el servidor (roadmap 1.5), como transportista o como solicitante, más recientes primero.
     * Corre bajo la RLS de quien llama: las mismas filas que la vista JSON y el CSV.
     */
    pe.logyx.shared.domain.Paged<Shipment> searchMine(UUID orgId, boolean asCarrier,
                                                       pe.logyx.shipment.application.ShipmentFilter filter,
                                                       pe.logyx.shared.domain.PageRequest page);
    /** Inserta o actualiza. */
    void save(Shipment shipment);
}
