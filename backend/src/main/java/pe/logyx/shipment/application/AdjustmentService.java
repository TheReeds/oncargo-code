package pe.logyx.shipment.application;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.config.SystemParameters;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.event.AdjustmentDecided;
import pe.logyx.shipment.application.event.AdjustmentEscalated;
import pe.logyx.shipment.application.event.AdjustmentProposed;
import pe.logyx.shipment.application.port.out.AdjustmentRepository;
import pe.logyx.shipment.application.port.out.DeliveryEvidenceProvider;
import pe.logyx.shipment.domain.AdjustmentPolicy;
import pe.logyx.shipment.domain.model.AdjustmentStatus;
import pe.logyx.shipment.domain.model.AdjustmentType;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentAdjustment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ajustes del envío (roadmap 2.6, ADR-0012): el transportista (ADMIN/OPS o el conductor asignado) propone; la PYME
 * (ADMIN/OPS) acepta o rechaza dentro del plazo ({@code adjustment_response_hours}); si no responde, el job lo escala
 * y decide el operador. Todo bajo RLS (V23: solo las partes y el operador ven y deciden) salvo el escalado, que corre
 * sin usuario por {@link SystemTx}. Cada paso se audita ({@code ADJUSTMENT_*}).
 */
@ApplicationScoped
public class AdjustmentService {

    @Inject AdjustmentRepository adjustments;
    @Inject ShipmentQueries queries;
    @Inject DeliveryEvidenceProvider documents;
    @Inject SystemParameters parameters;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject SystemTx system;
    @Inject Event<AdjustmentProposed> proposed;
    @Inject Event<AdjustmentDecided> decided;
    @Inject Event<AdjustmentEscalated> escalated;
    @Inject pe.logyx.shared.idempotency.IdempotencyKeys idempotency;

    static final String IDEMPOTENCY_SCOPE = "shipment-adjustment";

    /** Ajustes del envío, precio final y (para el transportista) sugerencias de estadía y peso. */
    @Transactional
    public AdjustmentsOverview overview(UUID shipmentId) {
        ShipmentSummary sum = queries.detail(shipmentId);              // 404 si no lo ve (RLS + partes/operador)
        Shipment s = sum.shipment();
        List<ShipmentAdjustment> list = adjustments.listByShipment(s.id());
        BigDecimal accepted = AdjustmentPolicy.acceptedTotal(list);
        boolean carrierSide = tenant.orgType() == OrgType.CARRIER && s.carrierOrgId().equals(tenant.orgId());
        List<AdjustmentsOverview.Suggestion> suggestions = carrierSide && s.status() != Shipment.Status.CANCELLED
                ? suggestions(sum, list) : List.of();
        return new AdjustmentsOverview(s.agreedPrice(), accepted, AdjustmentPolicy.finalPrice(s.agreedPrice(), accepted),
                s.commissionRate(), list, suggestions);
    }

    @Transactional
    public ShipmentAdjustment propose(UUID shipmentId, ProposeAdjustmentCommand cmd) {
        ShipmentSummary sum = queries.detail(shipmentId);
        Shipment s = sum.shipment();
        if (tenant.orgType() != OrgType.CARRIER || !s.carrierOrgId().equals(tenant.orgId())) {
            throw new BusinessException(403, "LOGYX-ADJ-006", "Los ajustes los propone el transportista del envío");
        }
        if (cmd.type() == null) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el tipo de ajuste", "type");
        }
        // Serializa propuestas simultáneas del mismo envío (tope LOGYX-ADJ-005).
        queries.lockShipment(s.id(), "shipment-adjust:");
        String key = cmd.idempotencyKey();
        if (key != null) {
            idempotency.lock(IDEMPOTENCY_SCOPE, key);
            var previous = idempotency.find(IDEMPOTENCY_SCOPE, key).flatMap(adjustments::findById);
            if (previous.isPresent() && previous.get().shipmentId().equals(s.id())) return previous.get();
        }
        OffsetDateTime now = OffsetDateTime.now();
        String note = AdjustmentPolicy.validateProposal(s, cmd.type(), cmd.amount(), cmd.note(),
                adjustments.countByShipment(s.id()), now);
        if (cmd.stopId() != null) sum.stop(cmd.stopId());                                    // 404 si no es del envío
        if (cmd.evidenceDocumentId() != null && !documents.belongsToShipment(s.id(), cmd.evidenceDocumentId())) {
            throw BusinessException.badRequest("LOGYX-ADJ-008", "La evidencia debe ser un documento de este envío",
                    "evidenceDocumentId");
        }
        ShipmentAdjustment a = ShipmentAdjustment.propose(s, cmd.stopId(), cmd.type(), cmd.amount(), note,
                cmd.evidenceDocumentId(), tenant.profileId(), now, parameters.adjustmentResponseWindow());
        adjustments.insert(a);
        if (key != null) idempotency.remember(IDEMPOTENCY_SCOPE, key, a.id());
        audit.record(tenant.profileId(), tenant.orgId(), "ADJUSTMENT_PROPOSED", "shipment", s.id(),
                a.type() + " " + a.amount().toPlainString() + " ajuste=" + a.id());
        proposed.fire(new AdjustmentProposed(a.id(), s.id(), s.requesterOrgId(), s.carrierOrgId(), a.type(),
                a.amount(), sum.routeLabel(), code(sum)));
        return a;
    }

    /** La PYME (antes de escalar) o el operador (escalado) aceptan o rechazan. Rechazar exige nota (LOGYX-ADJ-004). */
    @Transactional
    public ShipmentAdjustment decide(UUID adjustmentId, boolean accept, String note) {
        ShipmentAdjustment a = load(adjustmentId);
        ShipmentSummary sum = queries.detail(a.shipmentId());
        AdjustmentPolicy.Decider who = tenant.orgType() == OrgType.OPERATOR ? AdjustmentPolicy.Decider.OPERATOR
                : tenant.orgType() == OrgType.PYME && a.requesterOrgId().equals(tenant.orgId())
                ? AdjustmentPolicy.Decider.REQUESTER : AdjustmentPolicy.Decider.OTHER;
        if (!a.isPending()) throw BusinessException.conflict("LOGYX-ADJ-007", "Este ajuste ya se resolvió");
        AdjustmentPolicy.ensureCanDecide(a, who);
        a.decide(accept, tenant.orgId(), tenant.profileId(), note, OffsetDateTime.now());
        adjustments.update(a);
        boolean byOperator = who == AdjustmentPolicy.Decider.OPERATOR;
        audit.record(tenant.profileId(), tenant.orgId(), accept ? "ADJUSTMENT_ACCEPTED" : "ADJUSTMENT_REJECTED",
                "shipment", a.shipmentId(), a.type() + " " + a.amount().toPlainString() + " ajuste=" + a.id()
                        + (byOperator ? " (operador)" : ""));
        decided.fire(new AdjustmentDecided(a.id(), a.shipmentId(), a.requesterOrgId(), a.carrierOrgId(), a.type(),
                a.amount(), a.status(), byOperator, a.decision().note(), sum.routeLabel(), code(sum)));
        return a;
    }

    /** El transportista retira su propuesta mientras está pendiente. */
    @Transactional
    public ShipmentAdjustment withdraw(UUID adjustmentId) {
        ShipmentAdjustment a = load(adjustmentId);
        ShipmentSummary sum = queries.detail(a.shipmentId());
        if (tenant.orgType() != OrgType.CARRIER || !a.carrierOrgId().equals(tenant.orgId())) {
            throw new BusinessException(403, "LOGYX-ADJ-006", "Solo el transportista retira su propuesta");
        }
        a.withdraw(tenant.orgId(), tenant.profileId(), OffsetDateTime.now());
        adjustments.update(a);
        audit.record(tenant.profileId(), tenant.orgId(), "ADJUSTMENT_WITHDRAWN", "shipment", a.shipmentId(),
                "ajuste=" + a.id());
        decided.fire(new AdjustmentDecided(a.id(), a.shipmentId(), a.requesterOrgId(), a.carrierOrgId(), a.type(),
                a.amount(), AdjustmentStatus.WITHDRAWN, false, null, sum.routeLabel(), code(sum)));
        return a;
    }

    /** Operador: ajustes escalados que esperan su decisión, los más antiguos primero. */
    @Transactional
    public List<ShipmentAdjustment> escalatedForOperator() {
        if (tenant.orgType() != OrgType.OPERATOR) throw BusinessException.forbidden("Solo el operador");
        return adjustments.escalatedPending(200);
    }

    /**
     * Job (sin usuario): escala los ajustes cuyo plazo venció. SystemTx: recorre envíos de todas las organizaciones;
     * solo cambia {@code escalated_at} y avisa (outbox). Idempotente: un ajuste ya escalado no se vuelve a escalar.
     */
    public int escalateDue() {
        return system.call(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            int n = 0;
            for (ShipmentAdjustment a : adjustments.dueForEscalation(now, 200)) {
                if (!AdjustmentPolicy.dueForEscalation(a, now) || !a.escalate(now)) continue;
                adjustments.update(a);
                ShipmentSummary sum = queries.byIds(List.of(a.shipmentId())).stream().findFirst().orElse(null);
                audit.record(null, a.requesterOrgId(), "ADJUSTMENT_ESCALATED", "shipment", a.shipmentId(),
                        "ajuste=" + a.id());
                escalated.fire(new AdjustmentEscalated(a.id(), a.shipmentId(), a.requesterOrgId(), a.carrierOrgId(),
                        a.type(), a.amount(), sum == null ? "" : sum.routeLabel(), sum == null ? null : code(sum)));
                n++;
            }
            if (n > 0) Log.infof("Ajustes escalados al operador: %d", n);
            return n;
        });
    }

    private ShipmentAdjustment load(UUID id) {
        return adjustments.findById(id).orElseThrow(() -> BusinessException.notFound("Ajuste"));
    }

    /** Estadía por parada (sin una de estadía viva para esa parada) y diferencia de peso medida en el recojo. */
    private List<AdjustmentsOverview.Suggestion> suggestions(ShipmentSummary sum, List<ShipmentAdjustment> list) {
        List<AdjustmentsOverview.Suggestion> out = new ArrayList<>();
        Duration free = parameters.detentionFree();
        BigDecimal rate = parameters.detentionHourlyRate();
        for (ShipmentStop st : sum.stops()) {
            boolean taken = list.stream().anyMatch(a -> a.type() == AdjustmentType.DETENTION
                    && Objects.equals(a.stopId(), st.id()) && alive(a));
            if (taken) continue;
            var d = AdjustmentPolicy.detention(st.arrivedAt(), st.completedAt(), free, rate);
            if (d != null) {
                String where = st.type() == ShipmentStop.Type.PICKUP ? "el recojo" : "la entrega";
                out.add(new AdjustmentsOverview.Suggestion(AdjustmentType.DETENTION, st.id(), d.amount(),
                        "Esperó " + hours(d.waitedMinutes()) + " en " + where + " (" + free.toHours()
                                + " h sin cargo)"));
            }
        }
        boolean weightTaken = list.stream().anyMatch(a -> a.type() == AdjustmentType.WEIGHT_DIFF && alive(a));
        var pickup = sum.stops().stream().filter(x -> x.type() == ShipmentStop.Type.PICKUP).findFirst().orElse(null);
        if (!weightTaken && pickup != null && sum.request() != null) {
            BigDecimal amount = AdjustmentPolicy.weightDifference(sum.request().weightKg(), pickup.loadedWeightKg(),
                    sum.shipment().agreedPrice());
            if (amount != null) {
                out.add(new AdjustmentsOverview.Suggestion(AdjustmentType.WEIGHT_DIFF, pickup.id(), amount,
                        "Pesó " + pickup.loadedWeightKg().stripTrailingZeros().toPlainString() + " kg; declarado "
                                + sum.request().weightKg().stripTrailingZeros().toPlainString() + " kg"));
            }
        }
        return out;
    }

    private static boolean alive(ShipmentAdjustment a) {
        return a.status() == AdjustmentStatus.PENDING || a.status() == AdjustmentStatus.ACCEPTED;
    }

    private static String hours(long minutes) {
        long h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    private static String code(ShipmentSummary sum) {
        return sum.request() == null ? null : sum.request().code();
    }
}
