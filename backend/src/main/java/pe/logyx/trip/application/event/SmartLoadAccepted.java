package pe.logyx.trip.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.List;
import java.util.UUID;

/** El transportista aceptó un Smart Load Plan: avisa a las PYMEs cuya carga fue agrupada (RF33). */
public record SmartLoadAccepted(UUID suggestionId, UUID carrierOrgId, String carrierName, List<Item> items) implements OutboxEvent {
    public record Item(UUID requestId, UUID requesterOrgId, String route) {}
}
