package pe.logyx.marketplace.application.event;

import pe.logyx.shared.outbox.OutboxEvent;

import java.util.UUID;

/**
 * Se publicó una solicitud (también al republicarla): abre la subasta. La consumen los avisos de cargas nuevas
 * ({@code alerts}, 2.3). Lleva solo ids y la ruta; el matching vuelve a leer la solicitud.
 */
public record RequestPublished(UUID requestId, UUID requesterOrgId, String route, String originCity,
                               String destinationCity) implements OutboxEvent {}
