package pe.logyx.shipment.domain;

import pe.logyx.shared.domain.BusinessException;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Hora de un evento de parada (roadmap 1.11, conductor con mala señal): «Llegué» y «Entregado» aceptan la hora del
 * dispositivo ({@code occurredAt}), porque una acción encolada sin señal puede llegar al servidor horas después y la
 * puntualidad y la estadía deben medirse con la hora real. Se confía en ella dentro de límites:
 * <ul>
 *   <li>no más de {@link #MAX_FUTURE} en el futuro (reloj adelantado);</li>
 *   <li>no más de {@link #MAX_AGE} en el pasado (cola olvidada o reloj atrasado);</li>
 *   <li>monótona: no antes del paso anterior ({@code notBefore}: el cierre de la parada previa o la llegada a esta).</li>
 * </ul>
 * Si no se cumple: 400 {@code LOGYX-TRK-008} con {@code field = occurredAt} (la app reintenta sin la hora y vale la
 * del servidor). Sin {@code occurredAt} vale la hora del servidor, nunca anterior al paso previo. El servidor guarda
 * además su propia hora ({@code *RecordedAt}).
 */
public final class StopEventTimePolicy {

    public static final Duration MAX_FUTURE = Duration.ofMinutes(2);
    public static final Duration MAX_AGE = Duration.ofHours(48);

    private StopEventTimePolicy() {}

    public static OffsetDateTime resolve(OffsetDateTime occurredAt, OffsetDateTime now, OffsetDateTime notBefore) {
        if (occurredAt == null) return notBefore != null && notBefore.isAfter(now) ? notBefore : now;
        if (occurredAt.isAfter(now.plus(MAX_FUTURE))) {
            throw invalid("La hora del dispositivo está adelantada: revisa la fecha y hora del teléfono");
        }
        if (occurredAt.isBefore(now.minus(MAX_AGE))) {
            throw invalid("La acción tiene más de 48 horas: no se puede registrar con esa hora");
        }
        if (notBefore != null && occurredAt.isBefore(notBefore)) {
            throw invalid("La hora indicada es anterior al paso previo del viaje");
        }
        return occurredAt;
    }

    private static BusinessException invalid(String message) {
        return BusinessException.badRequest("LOGYX-TRK-008", message, "occurredAt");
    }
}
