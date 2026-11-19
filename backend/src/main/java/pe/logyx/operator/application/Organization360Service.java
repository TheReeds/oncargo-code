package pe.logyx.operator.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.documents.application.OrganizationFileService;
import pe.logyx.documents.application.UploadDocumentCommand;
import pe.logyx.identity.application.OperatorAccountService;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.application.OrganizationSuccessionService;
import pe.logyx.identity.application.port.out.OrganizationSuccessionStore.LegalChange;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.incident.application.IncidentQueries;
import pe.logyx.incident.application.IncidentSummary;
import pe.logyx.incident.application.OperatorIncidentFilter;
import pe.logyx.operator.application.port.out.FraudSignalReader;
import pe.logyx.operator.application.port.out.Organization360Reader;
import pe.logyx.operator.domain.FraudSignalPolicy;
import pe.logyx.reputation.application.ReputationProfile;
import pe.logyx.reputation.application.ReputationQueries;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.AdjustmentService;
import pe.logyx.shipment.application.ShipmentQueries;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.shipment.domain.model.ShipmentAdjustment;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Operador 360, señales de fraude y acciones sobre cuentas (roadmap 2.15). Todo bajo la RLS del operador (lecturas
 * {@code *_operator}); lee otros módulos solo por sus casos de uso y consultas. Solo el cambio de RUC usa SystemTx
 * (en {@link OrganizationSuccessionService}).
 */
@ApplicationScoped
public class Organization360Service {

    @Inject OrganizationDirectory organizations;
    @Inject OperatorAccountService accounts;
    @Inject OrganizationSuccessionService succession;
    @Inject OrganizationFileService files;
    @Inject Organization360Reader reader;
    @Inject FraudSignalReader signalsReader;
    @Inject ShipmentQueries shipments;
    @Inject IncidentQueries incidents;
    @Inject ReputationQueries reputation;
    @Inject AuditQueries audit;
    @Inject AdjustmentService adjustments;
    @Inject TenantContext tenant;
    @Inject pe.logyx.identity.application.port.out.OrganizationSuccessionStore lineage;

    @ConfigProperty(name = "logyx.fraud.withdrawal-rate", defaultValue = "0.30") BigDecimal withdrawalThreshold;
    @ConfigProperty(name = "logyx.fraud.cancellation-rate", defaultValue = "0.30") BigDecimal cancellationThreshold;
    @ConfigProperty(name = "logyx.fraud.min-sample", defaultValue = "5") long minSample;
    @ConfigProperty(name = "logyx.fraud.new-account-days", defaultValue = "30") long newAccountDays;
    @ConfigProperty(name = "logyx.fraud.low-bid-ratio", defaultValue = "0.85") BigDecimal lowBidRatio;
    @ConfigProperty(name = "logyx.fraud.low-bid-count", defaultValue = "5") long lowBidCount;

    /** Tasas: cancelación (envíos que canceló / envíos en que es parte) y retiro (ofertas retiradas / ofertas). */
    public record Rates(BigDecimal cancellation, BigDecimal withdrawal, BigDecimal requestCancellation) {}

    public record Overview(Organization organization, UUID predecessorId, List<UUID> successorIds,
                           Organization360Reader.Counts counts, Rates rates, List<Profile> users,
                           List<Organization360Reader.VehicleRow> vehicles, List<ShipmentSummary> recentShipments,
                           List<IncidentSummary> recentIncidents, ReputationProfile reputation,
                           List<AuditRecord> verificationHistory, List<AuditRecord> activity,
                           List<LegalChange> legalChanges, List<FraudSignalPolicy.Signal> signals) {}

    private static final List<String> VERIFICATION_ACTIONS = List.of("ORGANIZATION_VERIFIED", "ORGANIZATION_REJECTED",
            "ORGANIZATION_SUSPENDED", "ORGANIZATION_LEGAL_NAME_CHANGED", "ORGANIZATION_SUCCEEDED",
            "ORGANIZATION_LEGAL_DATA_CHANGED");

    @Transactional
    public Overview overview(UUID orgId) {
        requireOperator();
        Organization o = organizations.byIds(List.of(orgId)).get(orgId);
        if (o == null) throw BusinessException.notFound("Organización");
        var counts = reader.counts(orgId);
        long shipmentsTotal = counts.shipmentsPending() + counts.shipmentsInTransit() + counts.shipmentsDelivered()
                + counts.shipmentsCancelled();
        Rates rates = new Rates(rate(counts.cancelledByThisOrg(), shipmentsTotal),
                rate(counts.bidsWithdrawn(), counts.bids()), rate(counts.requestsCancelled(), counts.requests()));
        List<ShipmentSummary> recent = shipments.byIds(reader.recentShipments(orgId, 10));
        Map<UUID, ShipmentSummary> byId = recent.stream()
                .collect(Collectors.toMap(s -> s.shipment().id(), Function.identity(), (a, b) -> a));
        List<ShipmentSummary> ordered = reader.recentShipments(orgId, 10).stream().map(byId::get)
                .filter(Objects::nonNull).toList();
        var recentIncidents = incidents.operatorInbox(new OperatorIncidentFilter(List.of(), List.of(), null, false, null,
                orgId, false), new PageRequest(0, 10)).items();
        var verification = audit.search(new AuditFilter(null, null, VERIFICATION_ACTIONS, null, orgId, null, null),
                new PageRequest(0, 20)).items();
        var activity = audit.search(new AuditFilter(null, orgId, List.of(), null, null, null, null),
                new PageRequest(0, 20)).items();
        List<FraudSignalPolicy.Signal> mine = signals().stream()
                .filter(s -> s.organizationIds().contains(orgId)).toList();
        return new Overview(o, lineage.predecessorOf(orgId), lineage.successorsOf(orgId), counts, rates,
                accounts.usersOf(orgId), reader.vehicles(orgId), ordered, recentIncidents, reputation.profile(orgId),
                verification, activity, succession.history(orgId), mine);
    }

    /** Panel «Señales»: indicios para revisar (no sanciones). */
    @Transactional
    public List<FraudSignalPolicy.Signal> signals() {
        requireOperator();
        List<FraudSignalPolicy.Rate> rates = new ArrayList<>();
        rates.addAll(signalsReader.withdrawalRates(minSample));
        rates.addAll(signalsReader.cancellationRates(minSample));
        // Un umbral por tasa: el de retiro para las ofertas y el de cancelación para los envíos.
        List<FraudSignalPolicy.Signal> out = new ArrayList<>(FraudSignalPolicy.evaluate(signalsReader.sharedData(),
                rates.stream().filter(r -> r.kind() == pe.logyx.operator.domain.FraudSignalKind.HIGH_WITHDRAWAL_RATE)
                        .toList(), List.of(), withdrawalThreshold, minSample, lowBidCount));
        out.addAll(FraudSignalPolicy.evaluate(List.of(),
                rates.stream().filter(r -> r.kind() == pe.logyx.operator.domain.FraudSignalKind.HIGH_CANCELLATION_RATE)
                        .toList(),
                signalsReader.lowBidsFromNewAccounts(OffsetDateTime.now().minusDays(newAccountDays), lowBidRatio),
                cancellationThreshold, minSample, lowBidCount));
        out.sort(Comparator.comparing(FraudSignalPolicy.Signal::severity)
                .thenComparing(FraudSignalPolicy.Signal::kind));
        return out;
    }

    /** Nombres de las organizaciones de las señales (para la vista). */
    @Transactional
    public Map<UUID, Organization> organizationsOf(Collection<UUID> ids) {
        return organizations.byIds(ids);
    }

    public Profile suspendUser(UUID profileId, String reason) { return accounts.suspend(profileId, reason); }

    public Profile reactivateUser(UUID profileId, String reason) { return accounts.reactivate(profileId, reason); }

    /**
     * Cambio asistido de razón social o RUC con documento y motivo. Guarda el archivo y, si el cambio falla, lo borra.
     */
    public OrganizationSuccessionService.Outcome legalChange(UUID orgId, String legalName, String ruc, String reason,
                                                             String fileName, long size,
                                                             UploadDocumentCommand.Content content) {
        requireOperator();
        if (content == null) throw BusinessException.badRequest("LOGYX-VAL-001", "Adjunta el documento", "file");
        var stored = files.storeLegal(orgId, fileName, size, content);
        try {
            return succession.change(orgId, legalName, ruc, reason, new OrganizationSuccessionService.Evidence(
                    stored.key(), stored.fileName(), stored.contentType(), stored.sizeBytes(), stored.sha256()));
        } catch (RuntimeException e) {
            files.discard(stored.key());
            throw e;
        }
    }

    public record LegalFile(LegalChange change, InputStream content) {}

    public LegalFile legalChangeFile(UUID changeId) {
        LegalChange c = succession.get(changeId);
        try {
            return new LegalFile(c, files.open(c.documentKey()));
        } catch (IOException e) {
            throw BusinessException.notFound("Archivo");
        }
    }

    /** Ajustes escalados (la PYME no respondió a tiempo) con su envío. */
    @Transactional
    public List<AdjustmentRow> escalatedAdjustments() {
        List<ShipmentAdjustment> list = adjustments.escalatedForOperator();
        Map<UUID, ShipmentSummary> byShipment = shipments.byIds(list.stream().map(ShipmentAdjustment::shipmentId)
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(s -> s.shipment().id(), Function.identity()));
        return list.stream().map(a -> new AdjustmentRow(a, byShipment.get(a.shipmentId()))).toList();
    }

    public record AdjustmentRow(ShipmentAdjustment adjustment, ShipmentSummary shipment) {}

    @Transactional
    public Paged<IncidentSummary> incidentInbox(OperatorIncidentFilter filter, PageRequest page) {
        requireOperator();
        return incidents.operatorInbox(filter, page);
    }

    private void requireOperator() {
        if (tenant.orgType() != OrgType.OPERATOR) throw BusinessException.forbidden("Solo el operador");
    }

    private static BigDecimal rate(long hits, long total) {
        if (total == 0) return null;
        return BigDecimal.valueOf(hits).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }
}
