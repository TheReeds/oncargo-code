package pe.logyx.templates.domain.model;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Plantilla de solicitud de una organización: los mismos datos que se piden al publicar, sin la fecha de recojo.
 * RN07 se aplica al guardar y otra vez en cada publicación (las reglas pueden cambiar). Nunca se borra: se archiva.
 */
public final class RequestTemplate {

    public static final int NAME_MAX = 80;

    /** Los campos editables. {@code details}: referencia, valor declarado y contactos de recojo y entrega. */
    public record Data(String name, City originCity, String originAddress, City destinationCity,
                       String destinationAddress, CargoType cargoType, String cargoDescription, BigDecimal weightKg,
                       BigDecimal volumeM3, ShipmentRequest.Details details, UUID originPlaceId,
                       UUID destinationPlaceId, boolean invitePreferred) {

        Data validated() {
            if (name == null || name.isBlank()) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "Ponle un nombre a la plantilla", "name");
            }
            if (name.trim().length() > NAME_MAX) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "El nombre admite hasta " + NAME_MAX + " caracteres",
                        "name");
            }
            if (originCity == null || destinationCity == null) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "Elige el origen y el destino", "originCity");
            }
            if (originCity == destinationCity) {
                throw BusinessException.badRequest("LOGYX-PRC-002", "El origen y el destino no pueden ser el mismo",
                        "destinationCity");
            }
            if (cargoType == null) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "Elige el tipo de carga", "cargoType");
            }
            if (weightKg == null || weightKg.signum() <= 0 || weightKg.compareTo(new BigDecimal("40000")) > 0) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "El peso debe ir de 1 a 40,000 kg", "weightKg");
            }
            if (volumeM3 != null && volumeM3.signum() <= 0) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "El volumen debe ser mayor a 0", "volumeM3");
            }
            return new Data(name.trim(), originCity, required(ContactInfoPolicy.clean(originAddress, "originAddress"),
                    "originAddress"), destinationCity, required(ContactInfoPolicy.clean(destinationAddress,
                    "destinationAddress"), "destinationAddress"), cargoType, required(ContactInfoPolicy.clean(
                    cargoDescription, "cargoDescription"), "cargoDescription"), weightKg, volumeM3,
                    ShipmentRequest.Details.clean(details), originPlaceId, destinationPlaceId, invitePreferred);
        }

        private static String required(String v, String field) {
            if (v == null || v.isBlank()) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "Este dato es obligatorio", field);
            }
            return v.trim();
        }
    }

    private final UUID id;
    private final UUID organizationId;
    private final UUID createdBy;
    private final OffsetDateTime createdAt;
    private Data data;
    private OffsetDateTime archivedAt;
    private OffsetDateTime lastPublishedAt;
    private OffsetDateTime updatedAt;

    public RequestTemplate(UUID id, UUID organizationId, UUID createdBy, OffsetDateTime createdAt, Data data,
                           OffsetDateTime archivedAt, OffsetDateTime lastPublishedAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.data = data;
        this.archivedAt = archivedAt;
        this.lastPublishedAt = lastPublishedAt;
        this.updatedAt = updatedAt;
    }

    public static RequestTemplate create(UUID organizationId, UUID createdBy, Data data) {
        OffsetDateTime now = OffsetDateTime.now();
        return new RequestTemplate(UUID.randomUUID(), organizationId, createdBy, now, data.validated(), null, null, now);
    }

    /** Reemplaza los campos editables; una archivada se restaura antes (LOGYX-TPL-002). */
    public void update(Data data) {
        ensureActive();
        this.data = data.validated();
        this.updatedAt = OffsetDateTime.now();
    }

    public void archive() {
        if (archivedAt == null) {
            archivedAt = OffsetDateTime.now();
            updatedAt = archivedAt;
        }
    }

    public void restore() {
        if (archivedAt != null) {
            archivedAt = null;
            updatedAt = OffsetDateTime.now();
        }
    }

    public void markPublished(OffsetDateTime at) {
        this.lastPublishedAt = at;
    }

    public void ensureActive() {
        if (archivedAt != null) {
            throw BusinessException.conflict("LOGYX-TPL-002", "La plantilla está archivada: restáurala primero");
        }
    }

    public boolean isArchived() { return archivedAt != null; }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID createdBy() { return createdBy; }
    public OffsetDateTime createdAt() { return createdAt; }
    public Data data() { return data; }
    public OffsetDateTime archivedAt() { return archivedAt; }
    public OffsetDateTime lastPublishedAt() { return lastPublishedAt; }
    public OffsetDateTime updatedAt() { return updatedAt; }
}
