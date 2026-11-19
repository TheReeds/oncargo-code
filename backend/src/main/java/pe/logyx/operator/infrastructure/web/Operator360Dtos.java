package pe.logyx.operator.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.logyx.catalog.domain.City;
import pe.logyx.identity.application.port.out.OrganizationSuccessionStore.LegalChange;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.incident.application.IncidentSummary;
import pe.logyx.incident.domain.model.IncidentCategory;
import pe.logyx.incident.domain.model.IncidentSeverity;
import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.operator.application.AuditRecord;
import pe.logyx.operator.application.Organization360Service;
import pe.logyx.operator.domain.FraudSignalKind;
import pe.logyx.operator.domain.FraudSignalPolicy;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.shipment.domain.model.AdjustmentStatus;
import pe.logyx.shipment.domain.model.AdjustmentType;
import pe.logyx.shipment.domain.model.Shipment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Operador 360, señales y acciones sobre cuentas (roadmap 2.15). Nombres de schema únicos y estables. */
public final class Operator360Dtos {
    private Operator360Dtos() {}

    /** Motivo obligatorio (3–300). */
    public record AccountActionReason(@NotBlank(message = "Indica el motivo") @Size(max = 300) String reason) {}

    /** Motivo opcional al reactivar. */
    public record AccountReactivation(@Size(max = 300) String reason) {}

    /** Persona de la organización tal como la ve el operador. */
    public record OrgMemberView(UUID id, String fullName, String email, Profile.Role role, boolean active,
                                boolean invitationPending, OffsetDateTime lastLoginAt, OffsetDateTime createdAt,
                                OffsetDateTime operatorSuspendedAt, String operatorSuspensionReason) {
        static OrgMemberView of(Profile p) {
            return new OrgMemberView(p.id(), p.fullName(), p.email(), p.role(), p.active(), p.invitationPending(),
                    p.lastLoginAt(), p.createdAt(), p.operatorSuspendedAt(), p.operatorSuspensionReason());
        }
    }

    public record OrgVehicleView(UUID id, String plate, String type, String status, boolean archived) {}

    public record OrgShipmentRow(UUID id, String code, City originCity, City destinationCity, Shipment.Status status,
                                 OffsetDateTime createdAt, BigDecimal agreedPrice, BigDecimal finalPrice,
                                 UUID requesterOrgId, String requesterName, UUID carrierOrgId, String carrierName) {
        static OrgShipmentRow of(ShipmentSummary s) {
            var r = s.request();
            var sh = s.shipment();
            return new OrgShipmentRow(sh.id(), r == null ? null : r.code(), r == null ? null : r.originCity(),
                    r == null ? null : r.destinationCity(), sh.status(), sh.createdAt(), sh.agreedPrice(),
                    s.finalPrice(), sh.requesterOrgId(), s.requesterName(), sh.carrierOrgId(), s.carrierName());
        }
    }

    public record OrgIncidentRow(UUID id, UUID shipmentId, IncidentCategory category, IncidentSeverity severity,
                                 IncidentStatus status, boolean blocking, OffsetDateTime createdAt, OffsetDateTime dueAt,
                                 boolean slaBreached, UUID reportedByOrgId, String assigneeName) {
        static OrgIncidentRow of(IncidentSummary s) {
            var i = s.incident();
            return new OrgIncidentRow(i.id(), i.shipmentId(), i.category(), i.severity(), i.status(), i.blocking(),
                    i.createdAt(), i.dueAt(), i.slaBreached(OffsetDateTime.now()), i.reportedByOrgId(),
                    s.assigneeName());
        }
    }

    public record OrgAuditRow(long id, OffsetDateTime occurredAt, String actorName, String organizationName,
                              String action, String entity, UUID entityId, String detail) {
        static OrgAuditRow of(AuditRecord r) {
            return new OrgAuditRow(r.id(), r.occurredAt(), r.actorName(), r.organizationName(), r.action(), r.entity(),
                    r.entityId(), r.detail());
        }
    }

    public record LegalChangeView(UUID id, UUID organizationId, UUID successorId, String oldLegalName,
                                  String newLegalName, String oldRuc, String newRuc, String reason,
                                  String documentName, String documentContentType, long documentSizeBytes,
                                  OffsetDateTime createdAt) {
        static LegalChangeView of(LegalChange c) {
            return new LegalChangeView(c.id(), c.organizationId(), c.successorId(), c.oldLegalName(), c.newLegalName(),
                    c.oldRuc(), c.newRuc(), c.reason(), c.documentName(), c.documentContentType(),
                    c.documentSizeBytes(), c.createdAt());
        }
    }

    /** Resultado del cambio: la organización vigente (la misma o la sucesora). */
    public record LegalChangeResult(UUID organizationId, boolean successorCreated, LegalChangeView change) {}

    /**
     * Tasas 0–1 (null sin datos): {@code cancellation} = envíos que canceló / envíos en que es parte;
     * {@code withdrawal} = ofertas retiradas / ofertas; {@code requestCancellation} = solicitudes canceladas / publicadas.
     */
    public record OrgRatesView(BigDecimal cancellation, BigDecimal withdrawal, BigDecimal requestCancellation) {}

    public record OrgCountsView(long vehicles, long vehiclesArchived, long drivers, long shipmentsPending,
                                long shipmentsInTransit, long shipmentsDelivered, long shipmentsCancelled,
                                long cancelledByThisOrg, long bids, long bidsWithdrawn, long bidsAccepted,
                                long requests, long requestsCancelled, long incidents, long incidentsActive,
                                long incidentsReported) {}

    public record OrgReputationView(BigDecimal trustScore, int totalReviews, BigDecimal avgRating) {}

    /** Ficha 360 de una organización (solo el operador). */
    public record Organization360View(UUID id, OrgType type, String legalName, String ruc, String city, String phone,
                                      OffsetDateTime createdAt, Organization.VerificationStatus verificationStatus,
                                      String verificationNote, OffsetDateTime verificationUpdatedAt,
                                      UUID predecessorId, List<UUID> successorIds, OrgCountsView counts,
                                      OrgRatesView rates, OrgReputationView reputation, List<OrgMemberView> users,
                                      List<OrgVehicleView> vehicles, List<OrgShipmentRow> recentShipments,
                                      List<OrgIncidentRow> recentIncidents, List<OrgAuditRow> verificationHistory,
                                      List<OrgAuditRow> activity, List<LegalChangeView> legalChanges,
                                      List<FraudSignalView> signals) {
        static Organization360View of(Organization360Service.Overview o, Map<UUID, Organization> names) {
            var org = o.organization();
            var v = org.verification();
            var c = o.counts();
            var score = o.reputation() == null ? null : o.reputation().score();
            return new Organization360View(org.id(), org.type(), org.legalName(), org.ruc(), org.city(), org.phone(),
                    org.createdAt(), v.status(), v.note(), v.updatedAt(), o.predecessorId(), o.successorIds(),
                    new OrgCountsView(c.vehicles(), c.vehiclesArchived(), c.drivers(), c.shipmentsPending(),
                            c.shipmentsInTransit(), c.shipmentsDelivered(), c.shipmentsCancelled(),
                            c.cancelledByThisOrg(), c.bids(), c.bidsWithdrawn(), c.bidsAccepted(), c.requests(),
                            c.requestsCancelled(), c.incidents(), c.incidentsActive(), c.incidentsReported()),
                    new OrgRatesView(o.rates().cancellation(), o.rates().withdrawal(), o.rates().requestCancellation()),
                    score == null ? null : new OrgReputationView(score.trustScore(), score.totalReviews(),
                            score.avgRating()),
                    o.users().stream().map(OrgMemberView::of).toList(),
                    o.vehicles().stream().map(x -> new OrgVehicleView(x.id(), x.plate(), x.type(), x.status(),
                            x.archived())).toList(),
                    o.recentShipments().stream().map(OrgShipmentRow::of).toList(),
                    o.recentIncidents().stream().map(OrgIncidentRow::of).toList(),
                    o.verificationHistory().stream().map(OrgAuditRow::of).toList(),
                    o.activity().stream().map(OrgAuditRow::of).toList(),
                    o.legalChanges().stream().map(LegalChangeView::of).toList(),
                    o.signals().stream().map(s -> FraudSignalView.of(s, names)).toList());
        }
    }

    public record SignalOrganization(UUID id, String legalName, OrgType type, String ruc,
                                     Organization.VerificationStatus verificationStatus) {}

    /**
     * Señal de fraude (indicio, no sanción). {@code value}: el dato repetido (teléfono, placa o licencia; nunca la IP);
     * {@code rate}: tasa 0–1 en las de tasa; {@code sample}: organizaciones u ofertas/envíos considerados.
     */
    public record FraudSignalView(FraudSignalKind kind, pe.logyx.operator.domain.FraudSignalSeverity severity, String value,
                                  BigDecimal rate, long sample, List<SignalOrganization> organizations) {
        static FraudSignalView of(FraudSignalPolicy.Signal s, Map<UUID, Organization> names) {
            return new FraudSignalView(s.kind(), s.severity(), s.value(), s.rate(), s.sample(),
                    s.organizationIds().stream().map(id -> {
                        Organization o = names.get(id);
                        return o == null ? new SignalOrganization(id, null, null, null, null)
                                : new SignalOrganization(id, o.legalName(), o.type(), o.ruc(), o.verificationStatus());
                    }).toList());
        }
    }

    /** Ajuste escalado al operador con su envío. */
    public record EscalatedAdjustmentRow(UUID id, UUID shipmentId, String shipmentCode, City originCity,
                                         City destinationCity, String requesterName, String carrierName,
                                         AdjustmentType type, BigDecimal amount, String note, AdjustmentStatus status,
                                         OffsetDateTime createdAt, OffsetDateTime respondBy,
                                         OffsetDateTime escalatedAt, BigDecimal agreedPrice) {
        static EscalatedAdjustmentRow of(Organization360Service.AdjustmentRow row) {
            var a = row.adjustment();
            var s = row.shipment();
            var r = s == null ? null : s.request();
            return new EscalatedAdjustmentRow(a.id(), a.shipmentId(), r == null ? null : r.code(),
                    r == null ? null : r.originCity(), r == null ? null : r.destinationCity(),
                    s == null ? null : s.requesterName(), s == null ? null : s.carrierName(), a.type(), a.amount(),
                    a.note(), a.status(), a.createdAt(), a.respondBy(), a.escalatedAt(),
                    s == null ? null : s.shipment().agreedPrice());
        }
    }

    /** Fila de la bandeja de incidencias del operador (2.7). */
    public record OperatorIncidentRow(UUID id, UUID shipmentId, IncidentCategory category, IncidentSeverity severity,
                                      IncidentStatus status, boolean blocking, String description,
                                      UUID requesterOrgId, String requesterName, UUID carrierOrgId, String carrierName,
                                      UUID reportedByOrgId, OffsetDateTime createdAt, OffsetDateTime dueAt,
                                      boolean slaBreached, UUID assigneeProfileId, String assigneeName,
                                      BigDecimal claimedAmount) {
        static OperatorIncidentRow of(IncidentSummary s) {
            var i = s.incident();
            return new OperatorIncidentRow(i.id(), i.shipmentId(), i.category(), i.severity(), i.status(), i.blocking(),
                    i.description(), i.requesterOrgId(), s.name(i.requesterOrgId()), i.carrierOrgId(),
                    s.name(i.carrierOrgId()), i.reportedByOrgId(), i.createdAt(), i.dueAt(),
                    i.slaBreached(OffsetDateTime.now()), i.assigneeProfileId(), s.assigneeName(), i.claimedAmount());
        }
    }
}
