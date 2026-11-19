package pe.logyx.shipment.application.port.out;

import java.util.UUID;

/**
 * 2.12 (ADR-0014): desde que el transportista factura el flete, el precio final queda fijo y el envío no admite ajustes.
 * Lo implementa {@code shipment/infrastructure} leyendo el estado del cobro (shipment no depende de {@code settlement},
 * que sí depende de shipment). Quien llama ya tomó el candado {@code shipment-adjust:} del envío.
 */
public interface SettlementLock {
    boolean locksAdjustments(UUID shipmentId);
}
