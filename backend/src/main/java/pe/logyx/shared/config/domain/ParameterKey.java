package pe.logyx.shared.config.domain;

import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Parámetros de sistema que el operador puede cambiar sin desplegar (roadmap v2, ítem 2.14, primera tanda).
 * Cada uno define su tipo, sus límites y el nombre de su clave en {@code system_parameters}. El valor por omisión
 * (cuando el operador nunca lo cambió) sale de la configuración ({@code application.properties}) y lo resuelve
 * {@code SystemParameters}; este enum es dominio puro.
 *
 * <p>Los valores se guardan como texto canónico: decimales sin ceros sobrantes ({@code 0.08}) y horas enteras.
 */
public enum ParameterKey {

    /** Porcentaje que OnCargo retiene al transportista sobre el precio adjudicado (decisión del dueño, 2026-11-17). */
    COMMISSION_RATE("commission_rate", Kind.RATE, "0", "0.30", "Comisión al transportista",
            "Fracción del precio adjudicado que OnCargo retiene al transportista (0.08 = 8 %)."),
    /** RN10: horas publicada sin ofertas tras las cuales el operador puede asignar manualmente. */
    ASSIGNMENT_SLA_HOURS("assignment_sla_hours", Kind.HOURS, "0", "720", "SLA de asignación manual",
            "Horas sin ofertas tras las cuales el operador puede asignar la solicitud (RN10)."),
    /** RN11: una incidencia abierta desde hace este tiempo pasa a "en riesgo" (ámbar). */
    INCIDENT_WARN_HOURS("incident_warn_hours", Kind.HOURS, "1", "720", "Incidencias: aviso (ámbar)",
            "Horas abierta tras las cuales una incidencia pasa a ámbar."),
    /** RN11: una incidencia abierta desde hace este tiempo incumple el SLA (rojo). */
    INCIDENT_BREACH_HOURS("incident_breach_hours", Kind.HOURS, "1", "720", "Incidencias: incumplimiento (rojo)",
            "Horas abierta tras las cuales una incidencia incumple el SLA (rojo)."),
    /** 2.4: 1 = un transportista no puede ofertar con un vehículo que tiene un documento vencido (0 = solo advertencia). */
    FLEET_DOCS_BLOCK("fleet_docs_block", Kind.FLAG, "0", "1", "Documentos de flota vencidos: bloquear la oferta",
            "1 = no se puede ofertar con un vehículo cuyo SOAT, revisión técnica, póliza o MTC esté vencido. "
                    + "0 = solo se muestra una advertencia."),
    /** 2.6: horas que tiene la PYME para aceptar o rechazar un ajuste del envío; luego decide el operador. */
    ADJUSTMENT_RESPONSE_HOURS("adjustment_response_hours", Kind.HOURS, "1", "720", "Ajustes: plazo de respuesta",
            "Horas que tiene la PYME para aceptar o rechazar un ajuste; luego pasa al operador."),
    /** 2.6: espera sin cargo en una parada antes de sugerir estadía. */
    DETENTION_FREE_HOURS("detention_free_hours", Kind.HOURS, "0", "48", "Estadía: horas sin cargo",
            "Espera en una parada (llegada a cierre) que no se cobra."),
    /** 2.6: tarifa por hora de estadía que se sugiere (soles con IGV). */
    DETENTION_HOURLY_RATE("detention_hourly_rate", Kind.AMOUNT, "0", "1000", "Estadía: tarifa por hora",
            "Soles con IGV por cada hora de espera sobre la franquicia (se sugiere, no se cobra sola)."),
    /** 2.7: SLA por severidad de una incidencia (horas desde el reporte). */
    INCIDENT_SLA_CRITICAL_HOURS("incident_sla_critical_hours", Kind.HOURS, "1", "720", "Incidencias críticas: SLA",
            "Horas para resolver una incidencia crítica (accidente, robo)."),
    INCIDENT_SLA_HIGH_HOURS("incident_sla_high_hours", Kind.HOURS, "1", "720", "Incidencias altas: SLA",
            "Horas para resolver una incidencia de severidad alta."),
    INCIDENT_SLA_MEDIUM_HOURS("incident_sla_medium_hours", Kind.HOURS, "1", "720", "Incidencias medias: SLA",
            "Horas para resolver una incidencia de severidad media."),
    INCIDENT_SLA_LOW_HOURS("incident_sla_low_hours", Kind.HOURS, "1", "720", "Incidencias bajas: SLA",
            "Horas para resolver una incidencia de severidad baja.");

    /** {@code AMOUNT} (V23): soles con hasta 2 decimales. */
    public enum Kind { RATE, HOURS, FLAG, AMOUNT }

    private final String key;
    private final Kind kind;
    private final BigDecimal min;
    private final BigDecimal max;
    private final String label;
    private final String description;

    ParameterKey(String key, Kind kind, String min, String max, String label, String description) {
        this.key = key;
        this.kind = kind;
        this.min = new BigDecimal(min);
        this.max = new BigDecimal(max);
        this.label = label;
        this.description = description;
    }

    public String key() { return key; }
    public Kind kind() { return kind; }
    public BigDecimal min() { return min; }
    public BigDecimal max() { return max; }
    public String label() { return label; }
    public String description() { return description; }

    public static Optional<ParameterKey> parse(String raw) {
        if (raw == null) return Optional.empty();
        return Arrays.stream(values()).filter(k -> k.key.equalsIgnoreCase(raw.trim())).findFirst();
    }

    public static ParameterKey require(String raw) {
        return parse(raw).orElseThrow(() -> BusinessException.notFound("Parámetro"));
    }

    /**
     * Valida y devuelve el texto canónico. {@code LOGYX-PAR-001} (400, {@code field = value}) si no es un número del
     * tipo esperado o está fuera de los límites.
     */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) throw invalid("Indica un valor");
        BigDecimal v;
        try {
            v = new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw invalid("El valor debe ser un número");
        }
        if (kind == Kind.FLAG && v.compareTo(BigDecimal.ZERO) != 0 && v.compareTo(BigDecimal.ONE) != 0) {
            throw invalid("Indica 1 (bloquear) o 0 (solo advertir)");
        }
        if ((kind == Kind.HOURS || kind == Kind.FLAG) && v.stripTrailingZeros().scale() > 0) throw invalid("Indica horas enteras");
        if (kind == Kind.RATE && v.scale() > 4) throw invalid("Admite hasta 4 decimales (0.0825 = 8.25 %)");
        if (kind == Kind.AMOUNT && v.stripTrailingZeros().scale() > 2) throw invalid("Admite hasta 2 decimales");
        if (v.compareTo(min) < 0 || v.compareTo(max) > 0) {
            throw invalid(kind == Kind.RATE
                    ? "Debe estar entre " + percent(min) + " y " + percent(max)
                    : kind == Kind.AMOUNT
                    ? "Debe estar entre S/ " + min.toPlainString() + " y S/ " + max.toPlainString()
                    : "Debe estar entre " + min.toPlainString() + " y " + max.toPlainString() + " horas");
        }
        return kind == Kind.HOURS || kind == Kind.FLAG ? v.setScale(0).toPlainString() : strip(v);
    }

    public BigDecimal decimal(String canonical) { return new BigDecimal(canonical); }

    public boolean flag(String canonical) { return "1".equals(canonical); }

    public Duration hours(String canonical) { return Duration.ofHours(Long.parseLong(canonical)); }

    private BusinessException invalid(String message) {
        return BusinessException.badRequest("LOGYX-PAR-001", label + ": " + message, "value");
    }

    private static String strip(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return (s.scale() < 0 ? s.setScale(0) : s).toPlainString();
    }

    private static String percent(BigDecimal rate) {
        return strip(rate.movePointRight(2)) + " %";
    }
}
