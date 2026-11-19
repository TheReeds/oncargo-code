package pe.logyx.fleet.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.event.FleetDocumentExpiring;
import pe.logyx.fleet.application.port.out.DriverRepository;
import pe.logyx.fleet.application.port.out.FleetDocumentRepository;
import pe.logyx.fleet.application.port.out.FleetFileStore;
import pe.logyx.fleet.application.port.out.VehicleRepository;
import pe.logyx.fleet.domain.FleetDocumentPolicy;
import pe.logyx.fleet.domain.model.Driver;
import pe.logyx.fleet.domain.model.FleetDocument;
import pe.logyx.fleet.domain.model.FleetDocumentType;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.UserAccounts;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Documentos de flota con vencimiento (roadmap 2.4): subida (reemplaza al vigente del mismo tipo), listado, descarga,
 * sellos que se copian a la oferta, hallazgos al ofertar y el barrido diario de vencimientos.
 * Privados del transportista (RLS {@code fleet_documents_owner}); la PYME solo ve los sellos copiados en la oferta.
 */
@ApplicationScoped
public class FleetDocumentService {

    static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Sellos de la oferta: vencimiento del SOAT y de la revisión técnica si siguen vigentes hoy. */
    public record Seals(LocalDate soatValidUntil, LocalDate inspectionValidUntil) {
        public static final Seals NONE = new Seals(null, null);
    }

    public record Upload(FleetDocumentType type, UUID ownerId, String number, LocalDate expiresAt, String fileName,
                         long sizeBytes, FleetFileStore.Content content) {}

    @Inject FleetDocumentRepository documents;
    @Inject FleetFileStore files;
    @Inject VehicleRepository vehicles;
    @Inject DriverRepository drivers;
    @Inject UserAccounts accounts;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject SystemTx system;
    @Inject Event<FleetDocumentExpiring> expiring;

    public static LocalDate today() { return LocalDate.now(LIMA); }

    // ------------------------------------------------------------ Subida y lectura (transportista, bajo RLS)

    @Transactional
    public FleetDocument upload(Upload cmd) {
        UUID org = tenant.orgId();
        FleetDocumentType type = cmd.type();
        UUID vehicleId = null, driverId = null;
        if (type.owner() == FleetDocumentType.Owner.VEHICLE) {
            Vehicle v = vehicles.findOwned(cmd.ownerId(), org).orElseThrow(() -> BusinessException.notFound("Vehículo"));
            if (v.isArchived()) {
                throw BusinessException.conflict("LOGYX-FLT-004", "El vehículo está archivado", "vehicleId");
            }
            vehicleId = v.id();
        } else {
            driverId = drivers.findOwned(cmd.ownerId(), org).orElseThrow(() -> BusinessException.notFound("Conductor")).id();
        }
        FleetDocumentPolicy.ensureUploadable(type, cmd.expiresAt(), today());
        UUID id = UUID.randomUUID();
        FleetFileStore.Stored stored = files.put(org, id, cmd.sizeBytes(), cmd.content());
        try {
            OffsetDateTime now = OffsetDateTime.now();
            documents.supersedeCurrent(type, cmd.ownerId(), now);
            FleetDocument d = new FleetDocument(id, org, type, vehicleId, driverId, clean(cmd.number()), cmd.expiresAt(),
                    cleanName(cmd.fileName(), stored.extension()), stored.contentType(), cmd.sizeBytes(), stored.sha256(),
                    stored.key(), tenant.profileId(), now, null);
            documents.insert(d);
            if (type == FleetDocumentType.DRIVER_LICENSE) syncLicenseExpiry(driverId, cmd.expiresAt());
            audit.record(tenant.profileId(), org, "FLEET_DOCUMENT_UPLOADED", "fleet_document", id,
                    type + (cmd.expiresAt() == null ? "" : " vence=" + cmd.expiresAt()));
            return d;
        } catch (RuntimeException e) {
            files.delete(stored.key());   // sin fila no debe quedar archivo huérfano
            throw e;
        }
    }

    /** Vigentes e historial del vehículo, recientes primero (404 si no es de la organización). */
    @Transactional
    public List<FleetDocument> ofVehicle(UUID vehicleId) {
        vehicles.findOwned(vehicleId, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Vehículo"));
        return documents.listByVehicle(vehicleId);
    }

    @Transactional
    public List<FleetDocument> ofDriver(UUID driverId) {
        drivers.findOwned(driverId, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Conductor"));
        return documents.listByDriver(driverId);
    }

    /** Vigentes de todos mis vehículos y conductores (tablero de vencimientos y agenda). */
    @Transactional
    public List<FleetDocument> currentOfMine() {
        List<UUID> v = vehicles.listByOrganization(tenant.orgId()).stream().map(Vehicle::id).toList();
        List<UUID> d = drivers.listByOrganization(tenant.orgId()).stream().map(Driver::id).toList();
        List<FleetDocument> out = new ArrayList<>(documents.currentOfVehicles(v));
        out.addAll(documents.currentOfDrivers(d));
        return out;
    }

    public record Download(FleetDocument document, InputStream content) {}

    @Transactional
    public Download download(UUID id) {
        FleetDocument d = documents.findById(id).filter(x -> x.organizationId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Documento"));
        return new Download(d, files.open(d.storageKey()));
    }

    // ------------------------------------------------------------ Lo que usa ofertar (marketplace)

    /** Documentos vigentes del vehículo (bajo la RLS de quien llama; lo normal es el transportista dueño). */
    @Transactional
    public List<FleetDocument> currentOfVehicle(UUID vehicleId) {
        return documents.currentOfVehicles(List.of(vehicleId));
    }

    /** Sellos para copiar a la oferta: vencimiento del SOAT y de la revisión técnica si siguen vigentes. */
    @Transactional
    public Seals sealsOf(UUID vehicleId) {
        List<FleetDocument> current = documents.currentOfVehicles(List.of(vehicleId));
        LocalDate today = today();
        return new Seals(FleetDocumentPolicy.sealValidUntil(current, FleetDocumentType.SOAT, today),
                FleetDocumentPolicy.sealValidUntil(current, FleetDocumentType.TECHNICAL_INSPECTION, today));
    }

    /** Hallazgos al ofertar con el vehículo para un recojo en {@code pickupDate} (vencidos y por vencer antes del recojo). */
    @Transactional
    public List<FleetDocumentPolicy.Issue> issuesForBid(UUID vehicleId, LocalDate pickupDate) {
        return FleetDocumentPolicy.bidIssues(documents.currentOfVehicles(List.of(vehicleId)), today(), pickupDate);
    }

    // ------------------------------------------------------------ Barrido diario (job sin usuario)

    /**
     * Avisa a la organización dueña cuando un documento vigente cruza 30 días, 7 días o vence (una vez por umbral).
     * Corre como sistema (cruza organizaciones); el aviso sale por el outbox. Devuelve cuántos avisos generó.
     */
    public int sweepExpiring(LocalDate today) {
        return system.call(() -> {
            int sent = 0;
            for (FleetDocument d : documents.currentExpiringUntil(today.plusDays(FleetDocumentPolicy.WARN_DAYS))) {
                long daysLeft = ChronoUnit.DAYS.between(today, d.expiresAt());
                Optional<Integer> threshold = FleetDocumentPolicy.noticeThreshold(daysLeft);
                if (threshold.isEmpty() || documents.noticeSent(d.id(), threshold.get())) continue;
                String label = ownerLabel(d);
                if (label == null) continue;                                     // el dueño ya no existe
                documents.recordNotice(d.id(), threshold.get());
                expiring.fire(new FleetDocumentExpiring(d.organizationId(), d.type().owner().name(), d.ownerId(), label,
                        d.type().name(), d.type().label(), d.expiresAt(), (int) daysLeft, threshold.get()));
                sent++;
            }
            return sent;
        });
    }

    private String ownerLabel(FleetDocument d) {
        if (d.vehicleId() != null) return vehicles.findById(d.vehicleId()).filter(v -> !v.isArchived()).map(Vehicle::plate).orElse(null);
        return drivers.findById(d.driverId())
                .flatMap(dr -> accounts.byId(dr.profileId()).filter(p -> p.active()).map(p -> p.fullName())).orElse(null);
    }

    private void syncLicenseExpiry(UUID driverId, LocalDate expiresAt) {
        drivers.findById(driverId).ifPresent(dr -> {
            dr.updateCredentials(dr.licenseCategory(), expiresAt, dr.dni());
            drivers.save(dr);
        });
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().replaceAll("[\\p{Cntrl}]", "");
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    static String cleanName(String original, String extension) {
        String n = original == null ? "" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n;
        if (base.isBlank()) base = "documento";
        if (base.length() > 120) base = base.substring(0, 120);
        return base + "." + extension;
    }
}
