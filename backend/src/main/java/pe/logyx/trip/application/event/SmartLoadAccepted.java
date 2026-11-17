package pe.logyx.trip.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.List;
import java.util.UUID;

/**
 * El transportista aceptó un Smart Load Plan y ofertó en lote por todas sus cargas (F12): avisa a las PYMEs que su
 * carga recibió una oferta consolidada con otras (RF33).
 */
public record SmartLoadAccepted(UUID suggestionId, UUID carrierOrgId, String carrierName, List<Item> items) implements OutboxEvent {
    public record Item(UUID requestId, UUID requesterOrgId, String route) {}
}
