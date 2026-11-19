package pe.logyx.pricing.application.port.out;

import pe.logyx.pricing.domain.model.Tariff;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Versiones del tarifario (V24 {@code tariff_versions}). */
public interface TariffRepository {

    /** Una versión con su metadato. {@code createdByName} puede ser nulo. */
    record TariffRecord(Tariff tariff, String reason, UUID createdBy, String createdByName, OffsetDateTime createdAt,
                        OffsetDateTime cancelledAt) {
        public boolean cancelled() { return cancelledAt != null; }
    }

    /** Todas, la más nueva primero. */
    List<TariffRecord> all();

    /** Mayor número de versión (0 si no hay). Bloquea la tabla para numerar sin carreras. */
    int lockAndMaxVersion();

    void insert(Tariff tariff, String reason, UUID createdBy);

    /** Anula una versión programada que aún no rige; false si no existía, ya regía o ya estaba anulada. */
    boolean cancelScheduled(int version, UUID by, OffsetDateTime now);
}
