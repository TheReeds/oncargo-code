package pe.logyx.marketplace.infrastructure.web;

import jakarta.validation.constraints.*;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.marketplace.application.BackupBidService;
import pe.logyx.marketplace.application.BatchRequestsUseCase;
import pe.logyx.marketplace.application.BidSummary;
import pe.logyx.marketplace.application.CarrierPreferenceService;
import pe.logyx.marketplace.application.OffersInboxService;
import pe.logyx.marketplace.domain.model.BackupState;
import pe.logyx.marketplace.domain.model.PreferenceKind;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lado PYME del marketplace de la Ola 2 (zona P): preferidos y bloqueados, bandeja de ofertas, acciones masivas, segunda oferta. */
public final class DemandDtos {
    private DemandDtos() {}

    // ----- Preferidos y bloqueados (2.9)

    public record SetPreference(@NotNull PreferenceKind kind) {}

    public record CarrierPreferenceView(UUID carrierOrgId, String carrierName, boolean carrierRucVerified,
                                        BigDecimal carrierTrustScore, PreferenceKind kind, OffsetDateTime updatedAt) {
        static CarrierPreferenceView of(CarrierPreferenceService.Entry e) {
            var p = e.preference();
            return new CarrierPreferenceView(p.carrierOrgId(), e.carrierName(), e.carrierRucVerified(),
                    e.carrierTrustScore(), p.kind(), p.updatedAt());
        }
    }

    public record InviteResult(int invited) {}

    public record Unmarked(UUID carrierOrgId) {}

    // ----- Acciones masivas (2.10)

    public record BatchCancel(@NotEmpty @Size(max = BatchRequestsUseCase.MAX_ITEMS) List<UUID> ids,
                              @Size(max = 500) String reason) {}

    public record BatchReschedule(@NotEmpty @Size(max = BatchRequestsUseCase.MAX_ITEMS) List<UUID> ids,
                                  @NotNull @FutureOrPresent(message = "La fecha de recojo no puede ser pasada") LocalDate pickupDate) {}

    public record BatchDuplicate(@NotEmpty @Size(max = BatchRequestsUseCase.MAX_ITEMS) List<UUID> ids) {}

    /** Resultado de una solicitud del lote. En error: {@code code}, {@code message} y, si aplica, {@code field}. */
    public record BatchItemView(UUID requestId, boolean ok, String code, String message, String field,
                                /** Duplicar: la solicitud nueva. */ UUID resultId, String resultCode) {}

    public record BatchResultView(int total, int succeeded, int failed, List<BatchItemView> items) {
        static BatchResultView of(List<BatchRequestsUseCase.ItemResult> results) {
            var items = results.stream().map(r -> new BatchItemView(r.requestId(), r.ok(), r.code(), r.message(),
                    r.field(), r.resultId(), r.resultCode())).toList();
            int ok = (int) items.stream().filter(BatchItemView::ok).count();
            return new BatchResultView(items.size(), ok, items.size() - ok, items);
        }
    }

    // ----- Bandeja de ofertas (2.10)

    public record OfferBidView(UUID bidId, UUID carrierOrgId, String carrierName, boolean carrierRucVerified,
                               BigDecimal carrierTrustScore, BigDecimal amount, Vehicle.Type vehicleType,
                               String vehiclePlate, String notes, boolean preferred, OffsetDateTime createdAt) {
        static OfferBidView of(BidSummary s, Set<UUID> preferred) {
            var b = s.bid();
            return new OfferBidView(b.id(), b.carrierOrgId(), s.carrierName(), s.carrierRucVerified(),
                    s.carrierTrustScore(), b.amount(), b.vehicleType(), b.vehiclePlate(), b.notes(),
                    preferred.contains(b.carrierOrgId()), b.createdAt());
        }
    }

    /** Una fila de la bandeja: la solicitud, cuántas ofertas tiene y la mejor por monto y por Trust Score. */
    public record OfferInboxRow(UUID requestId, String code, City originCity, City destinationCity,
                                CargoType cargoType, BigDecimal weightKg, LocalDate pickupDate,
                                BigDecimal suggestedPrice, BigDecimal priceFloor,
                                /** Cuándo cierra la subasta (el cliente calcula el tiempo restante). */
                                OffsetDateTime closesAt, int bidCount, OfferBidView best, OfferBidView bestTrust,
                                List<OfferBidView> bids) {
        static OfferInboxRow of(OffersInboxService.Row row, Set<UUID> preferred) {
            ShipmentRequest r = row.request().request();
            var c = r.cargo();
            var a = row.request().auction();
            return new OfferInboxRow(r.id(), r.code(), c.originCity(), c.destinationCity(), c.cargoType(),
                    c.weightKg(), c.pickupDate(), r.pricing().suggestedPrice(), r.pricing().priceFloor(),
                    a == null ? null : a.closesAt(), row.bids().size(), OfferBidView.of(row.bestByAmount(), preferred),
                    OfferBidView.of(row.bestByTrust(), preferred),
                    row.bids().stream().map(b -> OfferBidView.of(b, preferred)).toList());
        }
    }

    public record OffersInboxView(int requests, int bids, List<OfferInboxRow> rows) {}

    // ----- Segunda oferta (2.9)

    public record BackupItemView(MarketplaceDtos.BidView bid, int rank, BackupState state, OffsetDateTime requestedAt,
                                 OffsetDateTime respondedAt) {
        static BackupItemView of(BackupBidService.Item i) {
            var b = i.backup();
            return new BackupItemView(MarketplaceDtos.BidView.of(i.bid()), b.rank(), b.state(), b.requestedAt(),
                    b.respondedAt());
        }
    }

    /** {@code eligible=false}: {@code blockerCode} (LOGYX-BCK-001/002/003) y {@code blockerMessage} dicen por qué. */
    public record BackupOptionsView(UUID requestId, boolean eligible, String blockerCode, String blockerMessage,
                                    /** Hasta cuándo debió cancelarse el envío para poder elegir otra oferta. */
                                    OffsetDateTime deadline, List<BackupItemView> items) {
        static BackupOptionsView of(BackupBidService.Options o) {
            return new BackupOptionsView(o.requestId(), o.eligible(), o.blockerCode(), o.blockerMessage(),
                    o.deadline(), o.items().stream().map(BackupItemView::of).toList());
        }
    }

    /** Lo que ve el transportista cuando una PYME le pide confirmar: su oferta y la carga, nada de otras ofertas. */
    public record BackupRequestView(UUID bidId, UUID requestId, String requestCode, City originCity,
                                    City destinationCity, LocalDate pickupDate, BigDecimal weightKg,
                                    String requesterName, BigDecimal amount, String vehiclePlate, BackupState state,
                                    OffsetDateTime requestedAt, OffsetDateTime respondedAt) {
        static BackupRequestView of(BackupBidService.CarrierRequest r) {
            var c = r.request().cargo();
            return new BackupRequestView(r.bid().id(), r.request().id(), r.request().code(), c.originCity(),
                    c.destinationCity(), c.pickupDate(), c.weightKg(), r.requesterName(), r.bid().amount(),
                    r.bid().vehiclePlate(), r.backup().state(), r.backup().requestedAt(), r.backup().respondedAt());
        }
    }
}
