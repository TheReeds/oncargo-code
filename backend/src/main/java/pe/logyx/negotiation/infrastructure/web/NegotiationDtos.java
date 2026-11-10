package pe.logyx.negotiation.infrastructure.web;

import pe.logyx.negotiation.domain.model.CounterOfferStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.negotiation.application.MessageSummary;
import pe.logyx.negotiation.application.ThreadSummary;
import pe.logyx.negotiation.domain.model.CounterOffer;
import pe.logyx.negotiation.domain.model.Message;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class NegotiationDtos {
    private NegotiationDtos() {}

    public record PostMessage(@NotBlank @Size(max = 1000) String body) {}

    public record ProposeCounterOffer(@NotNull @DecimalMin("1") BigDecimal amount, @Size(max = 500) String message) {}

    /** {@code mine}: lo envió la organización del usuario actual. {@code system}: aviso automático (contra-ofertas). */
    public record MessageView(UUID id, UUID requestId, UUID carrierOrgId, UUID senderOrgId, String senderName,
                              boolean mine, String body, BigDecimal proposedPrice, boolean system,
                              OffsetDateTime createdAt) {
        static MessageView of(MessageSummary s) {
            Message m = s.message();
            return new MessageView(m.id(), m.requestId(), m.carrierOrgId(), m.senderOrgId(), s.senderName(),
                    s.mine(), m.body(), m.proposedPrice(), m.system(), m.createdAt());
        }
    }

    /** Hilo (solicitud, transportista); {@code bid*} son null si el transportista aún no ofertó. */
    public record ChatThreadView(UUID requestId, UUID carrierOrgId, String carrierName, UUID bidId,
                                 BigDecimal bidAmount, Bid.Status bidStatus, long messageCount,
                                 String lastMessage, OffsetDateTime lastMessageAt) {
        static ChatThreadView of(ThreadSummary t) {
            Bid b = t.bid();
            Message last = t.lastMessage();
            return new ChatThreadView(t.requestId(), t.carrierOrgId(), t.carrierName(), b == null ? null : b.id(),
                    b == null ? null : b.amount(), b == null ? null : b.status(), t.messageCount(),
                    last == null ? null : last.body(), last == null ? null : last.createdAt());
        }
    }

    public record CounterOfferView(UUID id, UUID bidId, UUID requestId, UUID carrierOrgId, UUID offeredByOrgId,
                                   BigDecimal previousAmount, BigDecimal amount, String message,
                                   CounterOfferStatus status, OffsetDateTime createdAt, OffsetDateTime respondedAt) {
        static CounterOfferView of(CounterOffer c) {
            return new CounterOfferView(c.id(), c.bidId(), c.requestId(), c.carrierOrgId(), c.offeredByOrgId(),
                    c.previousAmount(), c.amount(), c.message(), c.status(), c.createdAt(), c.respondedAt());
        }
    }
}
