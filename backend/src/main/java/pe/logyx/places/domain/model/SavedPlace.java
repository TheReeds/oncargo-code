package pe.logyx.places.domain.model;

import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PeruPhone;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Almacén o dirección frecuente de una organización (libreta de direcciones). Es privada de la organización: RN07 no
 * aplica aquí (el contacto y las instrucciones solo viajan a la solicitud, y de ahí a las partes tras adjudicar).
 * Nunca se borra: se archiva.
 */
public final class SavedPlace {

    /** Los campos editables de una dirección. */
    public record Data(String label, City city, String address, String contactName, String contactPhone,
                       String openingHours, LocalTime windowFrom, LocalTime windowTo, String instructions,
                       PlaceKind kind) {

        Data validated() {
            String label = required(this.label, "label", "Ponle un nombre a la dirección");
            String address = required(this.address, "address", "Ingresa la dirección");
            if (city == null) throw BusinessException.badRequest("LOGYX-VAL-001", "Selecciona la ciudad", "city");
            if ((windowFrom == null) != (windowTo == null)) {
                throw BusinessException.badRequest("LOGYX-PLC-001",
                        "Indica el inicio y el fin del horario de atención, o ninguno", "windowTo");
            }
            if (windowFrom != null && !windowFrom.isBefore(windowTo)) {
                throw BusinessException.badRequest("LOGYX-PLC-001",
                        "El horario debe terminar después de empezar", "windowTo");
            }
            return new Data(label, city, address, blank(contactName),
                    PeruPhone.normalize(contactPhone, "contactPhone"), blank(openingHours), windowFrom, windowTo,
                    blank(instructions), kind == null ? PlaceKind.BOTH : kind);
        }

        private static String required(String v, String field, String message) {
            if (v == null || v.isBlank()) throw BusinessException.badRequest("LOGYX-VAL-001", message, field);
            return v.trim();
        }

        private static String blank(String v) {
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    private final UUID id;
    private final UUID organizationId;
    private final UUID createdBy;
    private final OffsetDateTime createdAt;
    private Data data;
    private OffsetDateTime archivedAt;
    private OffsetDateTime lastUsedAt;
    private OffsetDateTime updatedAt;

    public SavedPlace(UUID id, UUID organizationId, UUID createdBy, OffsetDateTime createdAt, Data data,
                      OffsetDateTime archivedAt, OffsetDateTime lastUsedAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.data = data;
        this.archivedAt = archivedAt;
        this.lastUsedAt = lastUsedAt;
        this.updatedAt = updatedAt;
    }

    public static SavedPlace create(UUID organizationId, UUID createdBy, Data data) {
        OffsetDateTime now = OffsetDateTime.now();
        return new SavedPlace(UUID.randomUUID(), organizationId, createdBy, now, data.validated(), null, null, now);
    }

    /** Reemplaza los campos editables. Una dirección archivada no se edita: primero se restaura. */
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

    public boolean isArchived() { return archivedAt != null; }

    private void ensureActive() {
        if (archivedAt != null) {
            throw BusinessException.conflict("LOGYX-PLC-002", "La dirección está archivada: restáurala para editarla");
        }
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID createdBy() { return createdBy; }
    public OffsetDateTime createdAt() { return createdAt; }
    public Data data() { return data; }
    public OffsetDateTime archivedAt() { return archivedAt; }
    public OffsetDateTime lastUsedAt() { return lastUsedAt; }
    public OffsetDateTime updatedAt() { return updatedAt; }
}
