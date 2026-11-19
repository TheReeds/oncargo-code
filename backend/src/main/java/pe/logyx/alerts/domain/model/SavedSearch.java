package pe.logyx.alerts.domain.model;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Búsqueda guardada del marketplace (roadmap 2.3): los filtros de un transportista, privados de cada persona, con los
 * que recibe un aviso cuando se publica una carga que coincide. Todos los filtros son opcionales, pero debe haber al
 * menos uno (si no, avisaría de todo). Con {@code vehicleId} solo avisa si ese vehículo puede ofertar a la carga; sin
 * él, si algún vehículo activo de la flota puede.
 */
public final class SavedSearch {

    public static final int NAME_MAX = 80;

    private final UUID id;
    private final UUID organizationId;
    private final UUID profileId;
    private String name;
    private City originCity;
    private City destinationCity;
    private CargoType cargoType;
    private LocalDate pickupFrom;
    private LocalDate pickupTo;
    private BigDecimal maxWeightKg;
    private UUID vehicleId;
    private boolean active;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public SavedSearch(UUID id, UUID organizationId, UUID profileId, String name, City originCity, City destinationCity,
                       CargoType cargoType, LocalDate pickupFrom, LocalDate pickupTo, BigDecimal maxWeightKg,
                       UUID vehicleId, boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.profileId = profileId;
        this.name = name;
        this.originCity = originCity;
        this.destinationCity = destinationCity;
        this.cargoType = cargoType;
        this.pickupFrom = pickupFrom;
        this.pickupTo = pickupTo;
        this.maxWeightKg = maxWeightKg;
        this.vehicleId = vehicleId;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Los filtros de una búsqueda ya validados (alta y edición). */
    public record Filters(String name, City originCity, City destinationCity, CargoType cargoType, LocalDate pickupFrom,
                          LocalDate pickupTo, BigDecimal maxWeightKg, UUID vehicleId) {}

    public static SavedSearch create(UUID organizationId, UUID profileId, Filters f, OffsetDateTime now) {
        SavedSearch s = new SavedSearch(UUID.randomUUID(), organizationId, profileId, null, null, null, null, null, null,
                null, null, true, now, now);
        s.apply(f, now);
        return s;
    }

    /** Reemplaza nombre y filtros. LOGYX-ALT-001 (400) si el nombre falta o no hay ningún filtro; ALT-003 si son incoherentes. */
    public void apply(Filters f, OffsetDateTime now) {
        String n = f.name() == null ? "" : f.name().trim();
        if (n.isEmpty() || n.length() > NAME_MAX) {
            throw BusinessException.badRequest("LOGYX-ALT-001", "Ponle un nombre de hasta " + NAME_MAX + " caracteres", "name");
        }
        if (f.originCity() == null && f.destinationCity() == null && f.cargoType() == null && f.pickupFrom() == null
                && f.pickupTo() == null && f.maxWeightKg() == null && f.vehicleId() == null) {
            throw BusinessException.badRequest("LOGYX-ALT-001",
                    "Elige al menos un filtro: ruta, tipo de carga, fechas, peso o vehículo", "originCity");
        }
        if (f.originCity() != null && f.originCity() == f.destinationCity()) {
            throw BusinessException.badRequest("LOGYX-ALT-003", "El origen y el destino son la misma ciudad", "destinationCity");
        }
        if (f.pickupFrom() != null && f.pickupTo() != null && f.pickupFrom().isAfter(f.pickupTo())) {
            throw BusinessException.badRequest("LOGYX-ALT-003", "La fecha «desde» es posterior a «hasta»", "pickupTo");
        }
        if (f.maxWeightKg() != null && f.maxWeightKg().signum() <= 0) {
            throw BusinessException.badRequest("LOGYX-ALT-003", "El peso máximo debe ser mayor a cero", "maxWeightKg");
        }
        this.name = n;
        this.originCity = f.originCity();
        this.destinationCity = f.destinationCity();
        this.cargoType = f.cargoType();
        this.pickupFrom = f.pickupFrom();
        this.pickupTo = f.pickupTo();
        this.maxWeightKg = f.maxWeightKg();
        this.vehicleId = f.vehicleId();
        this.updatedAt = now;
    }

    public void setActive(boolean value, OffsetDateTime now) {
        this.active = value;
        this.updatedAt = now;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID profileId() { return profileId; }
    public String name() { return name; }
    public City originCity() { return originCity; }
    public City destinationCity() { return destinationCity; }
    public CargoType cargoType() { return cargoType; }
    public LocalDate pickupFrom() { return pickupFrom; }
    public LocalDate pickupTo() { return pickupTo; }
    public BigDecimal maxWeightKg() { return maxWeightKg; }
    public UUID vehicleId() { return vehicleId; }
    public boolean active() { return active; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime updatedAt() { return updatedAt; }
}
