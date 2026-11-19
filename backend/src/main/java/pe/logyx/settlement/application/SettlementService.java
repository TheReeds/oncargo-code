package pe.logyx.settlement.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.documents.application.DocumentQueries;
import pe.logyx.documents.domain.model.Document;
import pe.logyx.documents.domain.model.DocumentType;
import pe.logyx.pricing.application.CommissionService;
import pe.logyx.pricing.domain.model.AwardedPriceBreakdown;
import pe.logyx.settlement.application.event.CommissionCollected;
import pe.logyx.settlement.application.event.SettlementUpdated;
import pe.logyx.settlement.application.port.out.*;
import pe.logyx.settlement.application.port.out.SettlementRepository.HistoryEntry;
import pe.logyx.settlement.domain.SettlementPolicy;
import pe.logyx.settlement.domain.SettlementPolicy.Side;
import pe.logyx.settlement.domain.model.*;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.config.SystemParameters;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.shipment.domain.model.Shipment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Cobros sin pasarela (roadmap 2.12, ADR-0014). Todo bajo la RLS de quien llama (la PYME y el transportista solo ven sus
 * envíos; el operador todos), salvo abrir la fila del cobro ({@link SettlementBootstrap}), que corre por
 * {@code SystemTx}: justificación, el vencimiento usa el plazo de pago de la PYME, que el transportista no puede leer, y
 * la fila es de la plataforma (la abre también el sistema al entregarse el envío).
 *
 * <p>Las transiciones toman el mismo candado consultivo que los ajustes del envío ({@code shipment-adjust:}): facturar y
 * proponer un ajuste no se cruzan. Cada paso queda en {@code settlement_history} (lo ven las partes), en la auditoría y
 * avisa por el outbox ({@link SettlementUpdated}).
 */
@ApplicationScoped
public class SettlementService {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final String LOCK = "shipment-adjust:";
    public static final int MAX_COLLECTION_SHIPMENTS = 200;

    /** Cobro de un envío con su precio final, desglose y quién puede ver qué (lo decide el DTO por rol). */
    public record Detail(ShipmentSummary shipment, Settlement settlement, BigDecimal finalPrice,
                         AwardedPriceBreakdown breakdown, int daysOverdue, boolean adjustmentsLocked,
                         List<HistoryEntry> history, List<PayoutAccount> payoutAccounts) {}

    /** Totales de la bandeja «Por pagar» / «Por cobrar» / comisiones del operador. */
    public record Summary(long awaiting, BigDecimal awaitingAmount, long overdue, BigDecimal overdueAmount,
                          long disputed, long toConfirm, BigDecimal toConfirmAmount, long commissionPending,
                          BigDecimal commissionPendingAmount, boolean truncated) {}

    /** Fila de listado con montos calculados. */
    public record Line(SettlementReader.Row row, BigDecimal finalPrice, AwardedPriceBreakdown breakdown, int daysOverdue) {}

    @Inject SettlementRepository settlements;
    @Inject SettlementReader reader;
    @Inject SettlementBootstrap bootstrap;
    @Inject PayoutAccountRepository payouts;
    @Inject CommissionCollectionRepository collections;
    @Inject ShipmentQueries shipments;
    @Inject DocumentQueries documents;
    @Inject CommissionService commission;
    @Inject SystemParameters parameters;
    @Inject TenantContext tenant;
    @Inject AuditService audit;
    @Inject SystemTx system;
    @Inject Event<SettlementUpdated> updated;
    @Inject Event<CommissionCollected> collected;

    public static LocalDate today() { return LocalDate.now(LIMA); }

    // ---------- Lectura

    @Transactional
    public Detail detail(UUID shipmentId) {
        ShipmentSummary sum = deliveredVisible(shipmentId);
        Settlement s = ensure(sum);
        return detailOf(sum, s);
    }

    @Transactional
    public Paged<Line> list(Set<SettlementStatus> statuses, Boolean overdue, UUID carrierOrgId, Boolean commissionPending,
                            LocalDate from, LocalDate to, PageRequest page) {
        rejectDriver();
        Set<String> st = statuses == null ? Set.of() : statuses.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
        boolean seesCommission = tenant.orgType() != OrgType.PYME;
        var f = new SettlementReader.Filter(st, overdue, carrierOrgId, seesCommission ? commissionPending : null, from, to,
                today(), parameters.paymentTermsDays());
        return reader.list(f, page).map(this::line);
    }

    /** Totales (sobre los primeros 2 000 envíos de cada grupo; {@code truncated} si hubo más). */
    @Transactional
    public Summary summary() {
        rejectDriver();
        LocalDate today = today();
        int terms = parameters.paymentTermsDays();
        var open = reader.top(new SettlementReader.Filter(Set.of("PENDING_INVOICE", "INVOICED", "PAID", "DISPUTED"),
                null, null, null, null, null, today, terms), 0, 2000);
        long awaiting = 0, overdue = 0, disputed = 0, toConfirm = 0;
        BigDecimal awaitingAmt = BigDecimal.ZERO, overdueAmt = BigDecimal.ZERO, toConfirmAmt = BigDecimal.ZERO;
        for (var r : open.rows()) {
            Line l = line(r);
            switch (r.status()) {
                case "PENDING_INVOICE", "INVOICED" -> {
                    awaiting++;
                    awaitingAmt = awaitingAmt.add(l.finalPrice());
                    if (l.daysOverdue() > 0) {
                        overdue++;
                        overdueAmt = overdueAmt.add(l.finalPrice());
                    }
                }
                case "PAID" -> {
                    toConfirm++;
                    toConfirmAmt = toConfirmAmt.add(l.finalPrice());
                }
                case "DISPUTED" -> disputed++;
                default -> { }
            }
        }
        long commissionPending = 0;
        BigDecimal commissionAmt = BigDecimal.ZERO;
        boolean truncated = open.total() > open.rows().size();
        if (tenant.orgType() != OrgType.PYME) {
            var pend = reader.top(new SettlementReader.Filter(Set.of(), null, null, true, null, null, today, terms), 0, 2000);
            for (var r : pend.rows()) {
                Line l = line(r);
                commissionPending++;
                commissionAmt = commissionAmt.add(l.breakdown().commission()).add(l.breakdown().commissionVat());
            }
            truncated |= pend.total() > pend.rows().size();
        }
        return new Summary(awaiting, awaitingAmt, overdue, overdueAmt, disputed, toConfirm, toConfirmAmt,
                commissionPending, commissionAmt, truncated);
    }

    // ---------- Transiciones

    @Transactional
    public Detail invoice(UUID shipmentId, String number, LocalDate issuedOn, UUID documentId) {
        return transition(shipmentId, "SETTLEMENT_INVOICED", (sum, s, side) -> {
            UUID doc = document(shipmentId, documentId, DocumentType.CARRIER_INVOICE);
            SettlementPolicy.invoice(s, side, tenant.isDriver(), number, issuedOn, deliveredOn(sum), doc,
                    sum.finalPrice(), sum.pendingAdjustments(), today(), OffsetDateTime.now(), tenant.profileId());
            return new Step(s.invoice().number(), sum.finalPrice());
        });
    }

    @Transactional
    public Detail pay(UUID shipmentId, PaymentMethod method, String operation, LocalDate paidOn, BigDecimal amount,
                      UUID documentId) {
        return transition(shipmentId, "SETTLEMENT_PAID", (sum, s, side) -> {
            UUID doc = document(shipmentId, documentId, DocumentType.PAYMENT_PROOF);
            SettlementPolicy.pay(s, side, method, operation, paidOn, amount, deliveredOn(sum), doc,
                    sum.pendingAdjustments(), today(), OffsetDateTime.now(), tenant.profileId());
            String note = method.label + (s.payment().operationNumber() == null ? "" : " · op. " + s.payment().operationNumber());
            return new Step(note, amount);
        });
    }

    @Transactional
    public Detail confirm(UUID shipmentId) {
        return transition(shipmentId, "SETTLEMENT_CONFIRMED", (sum, s, side) -> {
            SettlementPolicy.confirm(s, side, tenant.isDriver(), OffsetDateTime.now(), tenant.profileId());
            return new Step(null, s.payment().amount());
        });
    }

    @Transactional
    public Detail dispute(UUID shipmentId, String reason) {
        return transition(shipmentId, "SETTLEMENT_DISPUTED", (sum, s, side) -> {
            SettlementPolicy.dispute(s, side, tenant.isDriver(), reason, tenant.orgId(), OffsetDateTime.now());
            return new Step(s.dispute().reason(), sum.finalPrice());
        });
    }

    @Transactional
    public Detail resolve(UUID shipmentId, SettlementStatus target, String note) {
        return transition(shipmentId, "SETTLEMENT_DISPUTE_RESOLVED", (sum, s, side) -> {
            String n = note == null || note.isBlank() ? null : note.trim();
            if (side == Side.OPERATOR && (n == null || n.length() < 3)) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "Indica cómo se resolvió", "note");
            }
            if (n != null && n.length() > 500) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "La nota admite hasta 500 caracteres", "note");
            }
            SettlementPolicy.resolve(s, side, tenant.orgId(), target);
            return new Step(n, sum.finalPrice());
        });
    }

    private record Step(String note, BigDecimal amount) {}

    @FunctionalInterface
    private interface Mutation {
        Step apply(ShipmentSummary sum, Settlement s, Side side);
    }

    private Detail transition(UUID shipmentId, String action, Mutation m) {
        shipments.lockShipment(shipmentId, LOCK);                          // mismo candado que los ajustes (2.6)
        ShipmentSummary sum = deliveredVisible(shipmentId);
        Settlement s = ensure(sum);
        Side side = sideOf(s);
        SettlementStatus from = s.status();
        Step step = m.apply(sum, s, side);
        settlements.update(s);
        settlements.addHistory(s, from, tenant.profileId(), tenant.orgId(), step.note());
        String code = sum.request() == null ? null : sum.request().code();
        audit.record(tenant.profileId(), tenant.orgId(), action, "shipment", shipmentId,
                from + " → " + s.status() + (step.note() == null ? "" : " · " + clip(step.note(), 300)));
        updated.fire(new SettlementUpdated(shipmentId, s.requesterOrgId(), s.carrierOrgId(), code, sum.routeLabel(),
                from.name(), s.status().name(), tenant.orgId(), side == Side.OPERATOR, step.amount(), step.note()));
        return detailOf(sum, s);
    }

    // ---------- Comisión (operador)

    /** Cobros de comisión (operador: todos o de un transportista; transportista: los suyos). */
    @Transactional
    public Paged<CommissionCollectionRepository.Collection> commissionCollections(UUID carrierOrgId, PageRequest page) {
        rejectDriver();
        if (tenant.orgType() == OrgType.PYME) throw BusinessException.forbidden("La comisión es entre OnCargo y el transportista");
        UUID filter = tenant.orgType() == OrgType.CARRIER ? tenant.orgId() : carrierOrgId;
        return collections.list(filter, page);
    }

    /**
     * El operador registra que cobró la comisión (con su IGV) de esos envíos del transportista. {@code LOGYX-STL-006}
     * comisión ya cobrada, sin comisión o en disputa; {@code STL-010} envío de otro transportista o no entregado.
     */
    @Transactional
    public CommissionCollectionRepository.Collection collectCommission(UUID carrierOrgId, List<UUID> shipmentIds,
                                                                       PaymentMethod method, String operation,
                                                                       LocalDate paidOn, String note) {
        if (tenant.orgType() != OrgType.OPERATOR) throw BusinessException.forbidden("Solo el operador registra la comisión");
        if (carrierOrgId == null) throw BusinessException.badRequest("LOGYX-VAL-001", "Elige el transportista", "carrierOrgId");
        Set<UUID> ids = new LinkedHashSet<>(shipmentIds == null ? List.of() : shipmentIds);
        if (ids.isEmpty() || ids.size() > MAX_COLLECTION_SHIPMENTS) {
            throw BusinessException.badRequest("LOGYX-VAL-001",
                    "Elige de 1 a " + MAX_COLLECTION_SHIPMENTS + " envíos", "shipmentIds");
        }
        if (method == null) throw BusinessException.badRequest("LOGYX-VAL-001", "Elige el medio de pago", "method");
        String op = operation == null ? "" : operation.trim();
        if (op.isEmpty() || op.length() > 40) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el número de operación", "operationNumber");
        }
        if (paidOn == null || paidOn.isAfter(today())) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "La fecha del cobro no puede ser futura", "paidOn");
        }
        String n = note == null || note.isBlank() ? null : note.trim();
        if (n != null && n.length() > 500) throw BusinessException.badRequest("LOGYX-VAL-001", "Nota muy larga", "note");
        ids.forEach(id -> shipments.lockShipment(id, LOCK));
        // SystemTx: abre los cobros que falten (vencimiento con el plazo de la PYME); idempotente.
        system.run(() -> ids.forEach(id -> bootstrap.open(id, parameters.paymentTermsDays())));
        Map<UUID, SettlementReader.Row> rows = new HashMap<>();
        reader.rows(ids, parameters.paymentTermsDays()).forEach(r -> rows.put(r.shipmentId(), r));
        Map<UUID, Settlement> found = new HashMap<>();
        settlements.findAll(ids).forEach(s -> found.put(s.shipmentId(), s));
        BigDecimal total = BigDecimal.ZERO;
        for (UUID id : ids) {
            SettlementReader.Row r = rows.get(id);
            Settlement s = found.get(id);
            if (r == null || s == null || !carrierOrgId.equals(r.carrierOrgId())) {
                throw BusinessException.badRequest("LOGYX-STL-010",
                        "Un envío elegido no está entregado o es de otro transportista", "shipmentIds");
            }
            AwardedPriceBreakdown b = line(r).breakdown();
            BigDecimal owed = b.commission().add(b.commissionVat());
            SettlementPolicy.ensureCommissionCollectable(s, owed);
            total = total.add(owed);
        }
        var c = new CommissionCollectionRepository.Collection(UUID.randomUUID(), carrierOrgId, null, total, method, op,
                paidOn, n, ids.size(), tenant.profileId(), null, OffsetDateTime.now());
        collections.insert(c, tenant.orgId());
        for (UUID id : ids) {
            Settlement s = found.get(id);
            s.collectCommission(c.id());
            settlements.update(s);
        }
        audit.record(tenant.profileId(), tenant.orgId(), "COMMISSION_COLLECTED", "organization", carrierOrgId,
                total.toPlainString() + " · " + ids.size() + " envíos · " + method + " op. " + op);
        collected.fire(new CommissionCollected(c.id(), carrierOrgId, total, ids.size(), op));
        return c;
    }

    // ---------- Datos de cobro del transportista

    /** Los de mi empresa (transportista). */
    @Transactional
    public List<PayoutAccount> myPayoutAccounts() {
        requireCarrierOffice();
        return payouts.activeOf(tenant.orgId());
    }

    @Transactional
    public PayoutAccount addPayoutAccount(PayoutKind kind, String bankName, String holderName, String number,
                                          boolean primary) {
        requireCarrierOffice();
        pe.logyx.shared.security.AccessDenied.requireOrgAdmin(tenant);
        if (kind == null) throw BusinessException.badRequest("LOGYX-VAL-001", "Elige el tipo", "kind");
        String n = pe.logyx.settlement.domain.PayoutAccountPolicy.normalizeNumber(kind, number);
        String holder = pe.logyx.settlement.domain.PayoutAccountPolicy.holder(holderName);
        String bank = pe.logyx.settlement.domain.PayoutAccountPolicy.bank(kind, bankName);
        shipments.lockShipment(tenant.orgId(), "payout:");
        List<PayoutAccount> active = payouts.activeOf(tenant.orgId());
        pe.logyx.settlement.domain.PayoutAccountPolicy.ensureRoom(active.size());
        boolean makePrimary = primary || active.isEmpty();
        var a = new PayoutAccount(UUID.randomUUID(), tenant.orgId(), kind, bank, holder, n,
                n.substring(n.length() - 4), false, null, OffsetDateTime.now());
        payouts.insert(a, tenant.profileId());
        if (makePrimary) payouts.makePrimary(tenant.orgId(), a.id());
        audit.record(tenant.profileId(), tenant.orgId(), "PAYOUT_ACCOUNT_ADDED", "organization", tenant.orgId(),
                kind + " " + a.masked());
        return payouts.find(a.id()).orElseThrow();
    }

    @Transactional
    public void archivePayoutAccount(UUID id) {
        PayoutAccount a = ownPayout(id);
        payouts.archive(id);
        if (a.primary()) payouts.activeOf(tenant.orgId()).stream().findFirst()
                .ifPresent(next -> payouts.makePrimary(tenant.orgId(), next.id()));
        audit.record(tenant.profileId(), tenant.orgId(), "PAYOUT_ACCOUNT_ARCHIVED", "organization", tenant.orgId(),
                a.kind() + " " + a.masked());
    }

    @Transactional
    public PayoutAccount makePrimary(UUID id) {
        ownPayout(id);
        payouts.makePrimary(tenant.orgId(), id);
        return payouts.find(id).orElseThrow();
    }

    private PayoutAccount ownPayout(UUID id) {
        requireCarrierOffice();
        pe.logyx.shared.security.AccessDenied.requireOrgAdmin(tenant);
        PayoutAccount a = payouts.find(id).orElseThrow(() -> BusinessException.notFound("Dato de cobro"));
        if (!a.carrierOrgId().equals(tenant.orgId()) || a.archived()) throw BusinessException.notFound("Dato de cobro");
        return a;
    }

    private void requireCarrierOffice() {
        if (tenant.orgType() != OrgType.CARRIER || tenant.isDriver()) {
            throw BusinessException.forbidden("Los datos de cobro son de la empresa transportista");
        }
    }

    // ---------- Ayudas

    private ShipmentSummary deliveredVisible(UUID shipmentId) {
        rejectDriver();
        ShipmentSummary sum = shipments.detail(shipmentId);                  // 404 si no lo ve (RLS)
        if (sum.shipment().status() != Shipment.Status.DELIVERED) {
            throw BusinessException.conflict("LOGYX-STL-001", "El cobro se registra cuando el envío está entregado");
        }
        return sum;
    }

    private Settlement ensure(ShipmentSummary sum) {
        UUID id = sum.shipment().id();
        Optional<Settlement> s = settlements.find(id);
        if (s.isPresent()) return s.get();
        // SystemTx: el vencimiento usa el plazo de pago de la PYME (el transportista no lo lee) y la fila es de la
        // plataforma; idempotente (ON CONFLICT DO NOTHING).
        system.run(() -> bootstrap.open(id, parameters.paymentTermsDays()));
        return settlements.find(id).orElseThrow(() -> BusinessException.notFound("Cobro"));
    }

    private Side sideOf(Settlement s) {
        if (tenant.orgType() == OrgType.OPERATOR) return Side.OPERATOR;
        if (s.requesterOrgId().equals(tenant.orgId())) return Side.REQUESTER;
        if (s.carrierOrgId().equals(tenant.orgId())) return Side.CARRIER;
        return Side.OTHER;
    }

    private Detail detailOf(ShipmentSummary sum, Settlement s) {
        BigDecimal finalPrice = sum.finalPrice();
        BigDecimal rate = sum.shipment().commissionRate() == null ? BigDecimal.ZERO : sum.shipment().commissionRate();
        AwardedPriceBreakdown b = commission.breakdown(finalPrice, rate);
        List<PayoutAccount> payout = tenant.orgType() == OrgType.PYME || tenant.orgType() == OrgType.OPERATOR
                ? payouts.activeOf(s.carrierOrgId()) : List.of();
        return new Detail(sum, s, finalPrice, b, SettlementPolicy.daysOverdue(s, today()),
                SettlementPolicy.locksAdjustments(s.status()), settlements.history(s.shipmentId()), payout);
    }

    Line line(SettlementReader.Row r) {
        BigDecimal agreed = r.agreedPrice();
        BigDecimal finalPrice = pe.logyx.shipment.domain.AdjustmentPolicy.finalPrice(agreed,
                r.acceptedAdjustments() == null ? BigDecimal.ZERO : r.acceptedAdjustments());
        BigDecimal rate = r.commissionRate() == null ? BigDecimal.ZERO : r.commissionRate();
        AwardedPriceBreakdown b = commission.breakdown(finalPrice, rate);
        int overdue = 0;
        if (("PENDING_INVOICE".equals(r.status()) || "INVOICED".equals(r.status())) && today().isAfter(r.dueOn())) {
            overdue = (int) (today().toEpochDay() - r.dueOn().toEpochDay());
        }
        return new Line(r, finalPrice, b, overdue);
    }

    private UUID document(UUID shipmentId, UUID documentId, DocumentType expected) {
        if (documentId == null) return null;
        Document d = documents.ofShipment(shipmentId, documentId);
        if (d.type() != expected) {
            throw BusinessException.badRequest("LOGYX-STL-009", expected == DocumentType.PAYMENT_PROOF
                    ? "Adjunta una constancia de pago (PAYMENT_PROOF) de este envío"
                    : "Adjunta el comprobante del flete (CARRIER_INVOICE) de este envío", "documentId");
        }
        return d.id();
    }

    private static LocalDate deliveredOn(ShipmentSummary sum) {
        OffsetDateTime at = sum.shipment().deliveredAt();
        return at == null ? today() : at.atZoneSameInstant(LIMA).toLocalDate();
    }

    private void rejectDriver() {
        if (tenant.isDriver()) throw BusinessException.notFound("Cobro");
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
