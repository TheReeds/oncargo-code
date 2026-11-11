package pe.logyx.trip.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.math.BigDecimal;
import java.util.UUID;

/** El planificador generó una sugerencia nueva: avisa al transportista. */
public record SmartLoadSuggested(UUID suggestionId, UUID carrierOrgId, String vehiclePlate, int itemCount,
                                 BigDecimal occupancyPct, BigDecimal combinedIncome) implements OutboxEvent {}
