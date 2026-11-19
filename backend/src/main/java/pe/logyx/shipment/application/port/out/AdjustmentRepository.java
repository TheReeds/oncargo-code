package pe.logyx.shipment.application.port.out;

import pe.logyx.shipment.domain.model.ShipmentAdjustment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Ajustes del envío (V23). Bajo la RLS de quien llama: partes, conductor asignado y operador. */
public interface AdjustmentRepository {
    Optional<ShipmentAdjustment> findById(UUID id);
    /** Más antiguos primero. */
    List<ShipmentAdjustment> listByShipment(UUID shipmentId);
    int countByShipment(UUID shipmentId);
    /** Suma de los aceptados por envío (los envíos sin ajustes aceptados no aparecen). */
    Map<UUID, BigDecimal> acceptedTotals(Collection<UUID> shipmentIds);
    /** Pendientes por envío (los envíos sin pendientes no aparecen). */
    Map<UUID, Long> pendingCounts(Collection<UUID> shipmentIds);
    /** Pendientes con el plazo vencido y sin escalar (job, SystemTx). */
    List<ShipmentAdjustment> dueForEscalation(OffsetDateTime now, int limit);
    /** Escalados aún pendientes (operador), los más antiguos primero. */
    List<ShipmentAdjustment> escalatedPending(int limit);
    void insert(ShipmentAdjustment a);
    /** Solo estado y decisión (las demás columnas no se actualizan: V23 lo impide). */
    void update(ShipmentAdjustment a);
}
