package pe.logyx.settlement.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.math.BigDecimal;
import java.util.UUID;

/** El operador registró el cobro de la comisión de OnCargo (con su IGV) de {@code shipments} envíos. Aviso al transportista. */
public record CommissionCollected(UUID collectionId, UUID carrierOrgId, BigDecimal amount, int shipments,
                                  String operationNumber) implements OutboxEvent {}
