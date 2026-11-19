package pe.logyx.settlement.infrastructure.web;

import jakarta.validation.constraints.*;
import pe.logyx.pricing.domain.model.AwardedPriceBreakdown;
import pe.logyx.settlement.application.SettlementService;
import pe.logyx.settlement.application.port.out.CommissionCollectionRepository;
import pe.logyx.settlement.application.port.out.SettlementRepository;
import pe.logyx.settlement.domain.model.*;
import pe.logyx.shared.tenancy.OrgType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTOs del cobro (2.12). Regla de visibilidad (ADR-0010): la PYME nunca recibe la comisión (campos nulos); el
 * transportista y el operador sí. Los datos de cobro del transportista van completos a la PYME del envío (para
 * transferir) y al operador; al transportista sin ser ADMIN, enmascarados.
 */
public final class SettlementDtos {

    private SettlementDtos() {}

    public record SettlementInvoiceView(String number, LocalDate issuedOn, UUID documentId, BigDecimal amount,
                                        OffsetDateTime registeredAt) {}

    public record SettlementPaymentView(PaymentMethod method, String methodLabel, String operationNumber,
                                        LocalDate paidOn, BigDecimal amount, UUID documentId, OffsetDateTime registeredAt) {}

    /** {@code openedBy}: {@code REQUESTER} (la PYME) o {@code CARRIER}. */
    public record SettlementDisputeView(OffsetDateTime openedAt, String openedBy, String reason,
                                        SettlementStatus previousStatus) {}

    /** {@code actorSide}: {@code REQUESTER}, {@code CARRIER} u {@code OPERATOR}. */
    public record SettlementHistoryItem(SettlementStatus from, SettlementStatus to, String toLabel, String actorName,
                                        String actorSide, String note, OffsetDateTime at) {}

    /** Dato de cobro. {@code number} nulo cuando quien consulta solo ve los 4 últimos dígitos. */
    public record PayoutAccountView(UUID id, PayoutKind kind, String bankName, String holderName, String number,
                                    String last4, boolean primary, OffsetDateTime createdAt) {
        static PayoutAccountView of(PayoutAccount a, boolean full) {
            return new PayoutAccountView(a.id(), a.kind(), a.bankName(), a.holderName(), full ? a.number() : null,
                    a.last4(), a.primary(), a.createdAt());
        }
    }

    /**
     * Cobro de un envío entregado. {@code finalPrice} = acordado + ajustes aceptados (con IGV). {@code commission*},
     * {@code carrierNet} y {@code commissionStatus} nulos para la PYME. {@code actions}: lo que quien consulta puede
     * hacer ahora ({@code INVOICE}, {@code PAY}, {@code CONFIRM}, {@code DISPUTE}, {@code WITHDRAW_DISPUTE},
     * {@code RESOLVE}); la UI lo usa para mostrar botones, el backend igual lo valida.
     */
    public record SettlementView(UUID shipmentId, String code, String originCity, String destinationCity,
                                 String requesterName, String carrierName, OffsetDateTime deliveredAt,
                                 SettlementStatus status, String statusLabel, LocalDate dueOn, int daysOverdue,
                                 BigDecimal agreedPrice, BigDecimal adjustmentsTotal, long pendingAdjustments,
                                 BigDecimal finalPrice, BigDecimal netOfVat, BigDecimal vat, BigDecimal commissionRate,
                                 BigDecimal commission, BigDecimal commissionVat, BigDecimal carrierNet,
                                 CommissionStatus commissionStatus, boolean adjustmentsLocked,
                                 SettlementInvoiceView invoice, SettlementPaymentView payment, OffsetDateTime confirmedAt,
                                 SettlementDisputeView dispute, List<SettlementHistoryItem> history,
                                 List<PayoutAccountView> payoutAccounts, List<String> actions) {

        static SettlementView of(SettlementService.Detail d, OrgType viewer, UUID viewerOrg, boolean driver) {
            var sum = d.shipment();
            var s = d.settlement();
            var b = d.breakdown();
            boolean seesCommission = viewer != OrgType.PYME;
            var req = sum.request();
            var inv = s.invoice() == null ? null : new SettlementInvoiceView(s.invoice().number(), s.invoice().issuedOn(),
                    s.invoice().documentId(), s.invoice().amount(), s.invoice().at());
            var pay = s.payment() == null ? null : new SettlementPaymentView(s.payment().method(),
                    s.payment().method().label, s.payment().operationNumber(), s.payment().paidOn(), s.payment().amount(),
                    s.payment().documentId(), s.payment().at());
            var disp = s.dispute() == null ? null : new SettlementDisputeView(s.dispute().at(),
                    side(s.dispute().byOrgId(), s), s.dispute().reason(), s.dispute().previous());
            List<SettlementHistoryItem> history = d.history().stream().map(h -> item(h, s)).toList();
            boolean fullPayout = viewer == OrgType.PYME || viewer == OrgType.OPERATOR;
            return new SettlementView(s.shipmentId(), req == null ? null : req.code(),
                    req == null ? null : req.originCity().name(), req == null ? null : req.destinationCity().name(),
                    sum.requesterName(), sum.carrierName(), sum.shipment().deliveredAt(), s.status(), s.status().label,
                    s.dueOn(), d.daysOverdue(), sum.shipment().agreedPrice(), sum.adjustmentsTotal(),
                    sum.pendingAdjustments(), d.finalPrice(), b.netOfVat(), b.vat(),
                    seesCommission ? b.commissionRate() : null, seesCommission ? b.commission() : null,
                    seesCommission ? b.commissionVat() : null, seesCommission ? b.carrierNet() : null,
                    seesCommission ? s.commissionStatus() : null, d.adjustmentsLocked(), inv, pay, s.confirmedAt(), disp,
                    history, d.payoutAccounts().stream().map(a -> PayoutAccountView.of(a, fullPayout)).toList(),
                    actions(s, viewer, viewerOrg, driver));
        }

        private static List<String> actions(Settlement s, OrgType viewer, UUID org, boolean driver) {
            List<String> out = new ArrayList<>();
            if (driver) return out;
            boolean carrier = viewer == OrgType.CARRIER && s.carrierOrgId().equals(org);
            boolean requester = viewer == OrgType.PYME && s.requesterOrgId().equals(org);
            SettlementStatus st = s.status();
            if (carrier && (st == SettlementStatus.PENDING_INVOICE || st == SettlementStatus.INVOICED)) out.add("INVOICE");
            if (requester && st.awaitingPayment()) out.add("PAY");
            if (carrier && st == SettlementStatus.PAID) out.add("CONFIRM");
            if ((carrier || requester) && (st == SettlementStatus.INVOICED || st == SettlementStatus.PAID)) out.add("DISPUTE");
            if (st == SettlementStatus.DISPUTED && s.dispute() != null && org.equals(s.dispute().byOrgId())
                    && (carrier || requester)) out.add("WITHDRAW_DISPUTE");
            if (st == SettlementStatus.DISPUTED && viewer == OrgType.OPERATOR) out.add("RESOLVE");
            return out;
        }
    }

    static String side(UUID org, Settlement s) {
        if (org == null) return null;
        if (org.equals(s.requesterOrgId())) return "REQUESTER";
        if (org.equals(s.carrierOrgId())) return "CARRIER";
        return "OPERATOR";
    }

    static SettlementHistoryItem item(SettlementRepository.HistoryEntry h, Settlement s) {
        return new SettlementHistoryItem(h.from(), h.to(), h.to().label, h.actorName(), side(h.actorOrgId(), s), h.note(),
                h.at());
    }

    /** Fila de las bandejas «Por pagar», «Por cobrar» y de comisiones. Comisión nula para la PYME. */
    public record SettlementRowView(UUID shipmentId, String code, String originCity, String destinationCity,
                                    UUID requesterOrgId, String requesterName, UUID carrierOrgId, String carrierName,
                                    OffsetDateTime deliveredAt, SettlementStatus status, String statusLabel,
                                    LocalDate dueOn, int daysOverdue, BigDecimal finalPrice, long pendingAdjustments,
                                    String invoiceNumber, LocalDate paidOn, BigDecimal paidAmount,
                                    BigDecimal commission, BigDecimal commissionVat, BigDecimal carrierNet,
                                    CommissionStatus commissionStatus) {
        static SettlementRowView of(SettlementService.Line l, OrgType viewer) {
            var r = l.row();
            boolean c = viewer != OrgType.PYME;
            AwardedPriceBreakdown b = l.breakdown();
            SettlementStatus st = SettlementStatus.valueOf(r.status());
            return new SettlementRowView(r.shipmentId(), r.code(), r.originCity(), r.destinationCity(), r.requesterOrgId(),
                    r.requesterName(), r.carrierOrgId(), r.carrierName(), r.deliveredAt(), st, st.label, r.dueOn(),
                    l.daysOverdue(), l.finalPrice(), r.pendingAdjustments(), r.invoiceNumber(), r.paidOn(), r.paidAmount(),
                    c ? b.commission() : null, c ? b.commissionVat() : null, c ? b.carrierNet() : null,
                    c ? CommissionStatus.valueOf(r.commissionStatus()) : null);
        }
    }

    /** Totales de la bandeja. Los de comisión van en 0 para la PYME. */
    public record SettlementSummaryView(long awaiting, BigDecimal awaitingAmount, long overdue, BigDecimal overdueAmount,
                                        long disputed, long toConfirm, BigDecimal toConfirmAmount, long commissionPending,
                                        BigDecimal commissionPendingAmount, boolean truncated) {
        static SettlementSummaryView of(SettlementService.Summary s) {
            return new SettlementSummaryView(s.awaiting(), s.awaitingAmount(), s.overdue(), s.overdueAmount(),
                    s.disputed(), s.toConfirm(), s.toConfirmAmount(), s.commissionPending(), s.commissionPendingAmount(),
                    s.truncated());
        }
    }

    public record CommissionCollectionView(UUID id, UUID carrierOrgId, String carrierName, BigDecimal amount,
                                           PaymentMethod method, String methodLabel, String operationNumber,
                                           LocalDate paidOn, String note, int shipmentsCount, String recordedByName,
                                           OffsetDateTime createdAt) {
        static CommissionCollectionView of(CommissionCollectionRepository.Collection c) {
            return new CommissionCollectionView(c.id(), c.carrierOrgId(), c.carrierName(), c.amount(), c.method(),
                    c.method().label, c.operationNumber(), c.paidOn(), c.note(), c.shipmentsCount(), c.recordedByName(),
                    c.createdAt());
        }
    }

    // ---------- Peticiones

    /** El transportista registra su comprobante (factura o boleta). {@code documentId}: un CARRIER_INVOICE del envío. */
    public record RegisterInvoice(@NotBlank(message = "Indica el número del comprobante") @Size(max = 40) String invoiceNumber,
                                  @NotNull(message = "Indica la fecha de emisión") LocalDate issuedOn, UUID documentId) {}

    /** La PYME registra el pago. {@code operationNumber} obligatorio salvo efectivo; {@code documentId}: un PAYMENT_PROOF. */
    public record RegisterPayment(@NotNull(message = "Elige el medio de pago") PaymentMethod method,
                                  @Size(max = 40) String operationNumber,
                                  @NotNull(message = "Indica la fecha del pago") LocalDate paidOn,
                                  @NotNull(message = "Indica el monto pagado") @DecimalMin(value = "0.01") BigDecimal amount,
                                  UUID documentId) {}

    public record OpenSettlementDispute(@NotBlank(message = "Explica el motivo") @Size(min = 5, max = 500) String reason) {}

    /** {@code status}: a qué estado vuelve (solo el operador; nulo = el anterior). {@code note}: obligatoria para el operador. */
    public record ResolveSettlementDispute(SettlementStatus status, @Size(max = 500) String note) {}

    public record CollectCommission(@NotNull(message = "Elige el transportista") UUID carrierOrgId,
                                    @NotEmpty(message = "Elige los envíos") @Size(max = 200) List<UUID> shipmentIds,
                                    @NotNull(message = "Elige el medio") PaymentMethod method,
                                    @NotBlank(message = "Indica el número de operación") @Size(max = 40) String operationNumber,
                                    @NotNull(message = "Indica la fecha") LocalDate paidOn, @Size(max = 500) String note) {}

    /** {@code number}: CCI de 20 dígitos (BANK) o celular de 9 dígitos (YAPE/PLIN). */
    public record CreatePayoutAccount(@NotNull(message = "Elige el tipo") PayoutKind kind, @Size(max = 60) String bankName,
                                      @NotBlank(message = "Indica el titular") @Size(max = 150) String holderName,
                                      @NotBlank(message = "Indica el número") @Size(max = 30) String number,
                                      Boolean primary) {}
}
