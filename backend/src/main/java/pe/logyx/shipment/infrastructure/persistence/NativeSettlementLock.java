package pe.logyx.shipment.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shipment.application.port.out.SettlementLock;

import java.util.List;
import java.util.UUID;

/**
 * Lee el estado del cobro (V24 {@code shipment_settlements}). SystemTx: el conductor (que también propone ajustes) no
 * ve cobros por RLS; solo se lee el estado de ese envío, que quien llama ya vio. Como el candado del envío lo tomó la
 * transacción del usuario antes de leer, un cobro facturado en paralelo ya está confirmado cuando se consulta.
 */
@ApplicationScoped
public class NativeSettlementLock implements SettlementLock {

    @Inject EntityManager em;
    @Inject SystemTx system;

    @Override
    @SuppressWarnings("unchecked")
    public boolean locksAdjustments(UUID shipmentId) {
        List<Object> rows = system.call(() -> em.createNativeQuery(
                        "SELECT status FROM shipment_settlements WHERE shipment_id = ?1")
                .setParameter(1, shipmentId).getResultList());
        return !rows.isEmpty() && !"PENDING_INVOICE".equals(rows.get(0));
    }
}
