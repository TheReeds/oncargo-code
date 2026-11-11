package pe.logyx.realtime.domain;

import pe.logyx.realtime.domain.model.Recipient;
import pe.logyx.realtime.domain.model.Subscriber;

/**
 * Aislamiento del stream (ADR-0001, ADR-0007): decide si un evento llega a una conexión. Es la misma regla que la
 * política RLS {@code realtime_events_read} (V12) más el filtro de usuario que aplica la reanudación:
 * <ul>
 *   <li>destinatario de usuario → solo ese usuario y solo si es de la misma organización;</li>
 *   <li>destinatario de organización → los usuarios de esa organización que no son conductores (el conductor solo
 *       recibe lo de sus envíos, dirigido a él);</li>
 *   <li>difusión por tipo → las organizaciones de ese tipo, sin conductores.</li>
 * </ul>
 * Ante cualquier dato faltante, no entrega (fail-closed).
 */
public final class RealtimeAudiencePolicy {

    private RealtimeAudiencePolicy() {}

    public static boolean matches(Recipient r, Subscriber s) {
        if (r == null || s == null) return false;
        if (r.orgId() != null) {
            if (!r.orgId().equals(s.orgId())) return false;
            if (r.userId() != null) return r.userId().equals(s.userId());
            return !s.driver();
        }
        return r.orgType() != null && r.orgType() == s.orgType() && !s.driver();
    }
}
