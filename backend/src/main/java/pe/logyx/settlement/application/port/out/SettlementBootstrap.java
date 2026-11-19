package pe.logyx.settlement.application.port.out;

import java.util.UUID;

/**
 * Abre el cobro de los envíos entregados (sistema, sin RLS): vencimiento = entrega (Lima) + plazo de pago de la PYME
 * ({@code organization_preferences.payment_terms_days}) o el parámetro {@code payment_terms_days}. Idempotente.
 */
public interface SettlementBootstrap {

    /** {@code shipmentId} nulo = todos los entregados sin cobro. Devuelve cuántos abrió. */
    int open(UUID shipmentId, int defaultTermsDays);

    /** Registra que se envió ese recordatorio ({@code settlement_notices}); false si ya se había enviado. */
    boolean recordNotice(UUID shipmentId, String kind);
}
