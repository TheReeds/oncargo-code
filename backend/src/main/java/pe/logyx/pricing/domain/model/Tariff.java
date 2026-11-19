package pe.logyx.pricing.domain.model;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Una versión del tarifario (roadmap 2.14, V24 {@code tariff_versions}). Los valores alimentan
 * {@code TariffCalculator}:
 * <pre>
 *   sugerido = base + km × combustible + km × t × tarifa_t_km × multiplicador(tipo)
 *            + ⌈km / cadaKm⌉ × peaje + manipuleo% × transporte
 * </pre>
 * La versión 1 son las constantes que el motor tenía fijas ({@link #STANDARD}). Inmutable: un cambio es otra versión.
 */
public record Tariff(int version, BigDecimal baseFee, BigDecimal fuelPerKm, BigDecimal ratePerKmTon,
                     BigDecimal tollEveryKm, BigDecimal tollPrice, BigDecimal handlingRate,
                     Map<CargoType, BigDecimal> cargoMultipliers, OffsetDateTime effectiveFrom) {

    /** Límites de cada valor (LOGYX-TRF-001 fuera de ellos). */
    public static final BigDecimal MAX_BASE_FEE = new BigDecimal("5000");
    public static final BigDecimal MAX_PER_KM = new BigDecimal("20");
    public static final BigDecimal MIN_TOLL_EVERY_KM = new BigDecimal("10");
    public static final BigDecimal MAX_TOLL_EVERY_KM = new BigDecimal("1000");
    public static final BigDecimal MAX_TOLL_PRICE = new BigDecimal("500");
    public static final BigDecimal MAX_HANDLING_RATE = new BigDecimal("0.5");
    public static final BigDecimal MIN_MULTIPLIER = new BigDecimal("0.5");
    public static final BigDecimal MAX_MULTIPLIER = new BigDecimal("3");

    /** Tarifa de la versión 1 (E3 §3.2): la que regía cuando las constantes estaban en el código. */
    public static final Tariff STANDARD = new Tariff(1, BigDecimal.ZERO, new BigDecimal("0.65"), new BigDecimal("0.80"),
            new BigDecimal("120"), new BigDecimal("12"), new BigDecimal("0.05"), standardMultipliers(), null);

    public Tariff {
        Objects.requireNonNull(baseFee, "baseFee");
        Objects.requireNonNull(fuelPerKm, "fuelPerKm");
        Objects.requireNonNull(ratePerKmTon, "ratePerKmTon");
        Objects.requireNonNull(tollEveryKm, "tollEveryKm");
        Objects.requireNonNull(tollPrice, "tollPrice");
        Objects.requireNonNull(handlingRate, "handlingRate");
        EnumMap<CargoType, BigDecimal> m = new EnumMap<>(CargoType.class);
        if (cargoMultipliers != null) m.putAll(cargoMultipliers);
        cargoMultipliers = Collections.unmodifiableMap(m);
    }

    /** Recargo por tipo de carga sobre el costo de transporte (1 si faltara). */
    public BigDecimal multiplier(CargoType cargo) {
        return cargoMultipliers.getOrDefault(cargo, BigDecimal.ONE);
    }

    /**
     * Valida los valores de una versión nueva. {@code LOGYX-TRF-001} (400) con {@code field} = el campo
     * ({@code baseFee}, {@code fuelPerKm}, …, {@code cargoMultipliers.FRAGILE}).
     */
    public void validate() {
        range(baseFee, BigDecimal.ZERO, MAX_BASE_FEE, 2, "baseFee", "La tarifa base");
        range(fuelPerKm, BigDecimal.ZERO, MAX_PER_KM, 4, "fuelPerKm", "El combustible por km");
        range(ratePerKmTon, BigDecimal.ZERO, MAX_PER_KM, 4, "ratePerKmTon", "La tarifa por t·km");
        range(tollEveryKm, MIN_TOLL_EVERY_KM, MAX_TOLL_EVERY_KM, 2, "tollEveryKm", "El tramo de peaje");
        range(tollPrice, BigDecimal.ZERO, MAX_TOLL_PRICE, 2, "tollPrice", "El precio del peaje");
        range(handlingRate, BigDecimal.ZERO, MAX_HANDLING_RATE, 4, "handlingRate", "El manipuleo");
        for (CargoType c : CargoType.values()) {
            BigDecimal v = cargoMultipliers.get(c);
            if (v == null) {
                throw BusinessException.badRequest("LOGYX-TRF-001", "Falta el multiplicador de " + c.name(),
                        "cargoMultipliers." + c.name());
            }
            range(v, MIN_MULTIPLIER, MAX_MULTIPLIER, 2, "cargoMultipliers." + c.name(), "El multiplicador de " + c.name());
        }
    }

    private static void range(BigDecimal v, BigDecimal min, BigDecimal max, int scale, String field, String what) {
        if (v == null || v.compareTo(min) < 0 || v.compareTo(max) > 0 || v.stripTrailingZeros().scale() > scale) {
            throw BusinessException.badRequest("LOGYX-TRF-001", what + " debe estar entre " + min.toPlainString()
                    + " y " + max.toPlainString() + " (hasta " + scale + " decimales)", field);
        }
    }

    private static Map<CargoType, BigDecimal> standardMultipliers() {
        Map<CargoType, BigDecimal> m = new EnumMap<>(CargoType.class);
        m.put(CargoType.GENERAL, new BigDecimal("1.00"));
        m.put(CargoType.FRAGILE, new BigDecimal("1.30"));
        m.put(CargoType.PERISHABLE, new BigDecimal("1.25"));
        m.put(CargoType.HAZARDOUS, new BigDecimal("1.50"));
        m.put(CargoType.BULK, new BigDecimal("0.95"));
        m.put(CargoType.OVERSIZED, new BigDecimal("1.40"));
        return m;
    }
}
