package pe.logyx.pricing.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.pricing.application.port.out.TariffRepository;
import pe.logyx.pricing.domain.model.Tariff;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL nativo sobre {@code tariff_versions} (el JSONB de multiplicadores se lee como texto). */
@ApplicationScoped
public class NativeTariffRepository implements TariffRepository {

    @Inject EntityManager em;
    @Inject ObjectMapper json;

    @Override
    @SuppressWarnings("unchecked")
    public List<TariffRecord> all() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT t.version, t.base_fee, t.fuel_per_km, t.rate_per_km_ton, t.toll_every_km, t.toll_price,
                       t.handling_rate, t.cargo_multipliers::text, t.effective_from, t.reason, t.created_by,
                       p.full_name, t.created_at, t.cancelled_at
                  FROM tariff_versions t LEFT JOIN profiles p ON p.id = t.created_by
                 ORDER BY t.version DESC
                """).getResultList();
        return rows.stream().map(r -> new TariffRecord(new Tariff(((Number) r[0]).intValue(), dec(r[1]), dec(r[2]),
                dec(r[3]), dec(r[4]), dec(r[5]), dec(r[6]), multipliers((String) r[7]), odt(r[8])),
                (String) r[9], uuid(r[10]), (String) r[11], odt(r[12]), odt(r[13]))).toList();
    }

    @Override
    public int lockAndMaxVersion() {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext('tariff_versions'))").getSingleResult();
        return ((Number) em.createNativeQuery("SELECT coalesce(max(version), 0) FROM tariff_versions")
                .getSingleResult()).intValue();
    }

    @Override
    public void insert(Tariff t, String reason, UUID createdBy) {
        em.createNativeQuery("""
                INSERT INTO tariff_versions (version, base_fee, fuel_per_km, rate_per_km_ton, toll_every_km, toll_price,
                                             handling_rate, cargo_multipliers, effective_from, reason, created_by)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, CAST(?8 AS jsonb), ?9, ?10, ?11)
                """).setParameter(1, t.version()).setParameter(2, t.baseFee()).setParameter(3, t.fuelPerKm())
                .setParameter(4, t.ratePerKmTon()).setParameter(5, t.tollEveryKm()).setParameter(6, t.tollPrice())
                .setParameter(7, t.handlingRate()).setParameter(8, toJson(t.cargoMultipliers()))
                .setParameter(9, t.effectiveFrom()).setParameter(10, reason).setParameter(11, createdBy)
                .executeUpdate();
    }

    @Override
    public boolean cancelScheduled(int version, UUID by, OffsetDateTime now) {
        return em.createNativeQuery("""
                UPDATE tariff_versions SET cancelled_at = ?2, cancelled_by = ?3
                 WHERE version = ?1 AND cancelled_at IS NULL AND effective_from > ?2
                """).setParameter(1, version).setParameter(2, now).setParameter(3, by).executeUpdate() == 1;
    }

    private String toJson(Map<CargoType, BigDecimal> m) {
        Map<String, String> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k.name(), v.toPlainString()));
        try {
            return json.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<CargoType, BigDecimal> multipliers(String raw) {
        Map<CargoType, BigDecimal> out = new EnumMap<>(CargoType.class);
        try {
            Map<String, Object> m = json.readValue(raw, new TypeReference<Map<String, Object>>() {});
            m.forEach((k, v) -> {
                try {
                    out.put(CargoType.valueOf(k), new BigDecimal(String.valueOf(v)));
                } catch (IllegalArgumentException ignored) {
                    // tipo de carga que ya no existe: se ignora
                }
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("tariff_versions.cargo_multipliers inválido", e);
        }
        return out;
    }

    private static BigDecimal dec(Object o) {
        return o == null ? null : o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private static UUID uuid(Object o) {
        return o == null ? null : o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static OffsetDateTime odt(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof Timestamp t) return t.toInstant().atOffset(ZoneOffset.UTC);
        if (v instanceof Instant i) return i.atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(v.toString());
    }
}
