package pe.logyx.shared.idempotency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Claves de idempotencia por (organización, ámbito, clave) durante 24 h (tabla {@code idempotency_keys}, V17, con RLS).
 * Uso dentro de la transacción de la escritura: {@link #lock} serializa los reintentos simultáneos con la misma clave
 * (candado consultivo de la transacción), {@link #find} devuelve el recurso ya creado y {@link #remember} lo registra.
 * La organización sale del JWT, nunca del cliente.
 */
@ApplicationScoped
public class IdempotencyKeys {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:\\-]{8,80}");

    @Inject EntityManager em;
    @Inject TenantContext tenant;

    /** Cabecera {@code Idempotency-Key}: {@code null} si no vino; 400 si el formato no sirve. */
    public static String validate(String key) {
        if (key == null || key.isBlank()) return null;
        String k = key.trim();
        if (!VALID.matcher(k).matches()) {
            throw BusinessException.badRequest("LOGYX-VAL-001",
                    "Idempotency-Key debe tener entre 8 y 80 caracteres (letras, números, . _ : -)", "Idempotency-Key");
        }
        return k;
    }

    /** Candado hasta el fin de la transacción: dos peticiones con la misma clave se ejecutan una tras otra. */
    public void lock(String scope, String key) {
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, tenant.orgId() + "|" + scope + "|" + key).getSingleResult();
    }

    /** El recurso creado con esa clave en las últimas 24 h, si existe. Descarta una clave vencida. */
    public Optional<UUID> find(String scope, String key) {
        var rows = em.createNativeQuery("SELECT resource_id, created_at > now() - interval '24 hours' "
                        + "FROM idempotency_keys WHERE organization_id = ?1 AND scope = ?2 AND key = ?3")
                .setParameter(1, tenant.orgId()).setParameter(2, scope).setParameter(3, key).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] row = (Object[]) rows.getFirst();
        if (Boolean.TRUE.equals(row[1])) return Optional.of((UUID) row[0]);
        em.createNativeQuery("DELETE FROM idempotency_keys WHERE organization_id = ?1 AND scope = ?2 AND key = ?3")
                .setParameter(1, tenant.orgId()).setParameter(2, scope).setParameter(3, key).executeUpdate();
        return Optional.empty();
    }

    public void remember(String scope, String key, UUID resourceId) {
        em.createNativeQuery("INSERT INTO idempotency_keys (organization_id, scope, key, resource_id) "
                        + "VALUES (?1, ?2, ?3, ?4)")
                .setParameter(1, tenant.orgId()).setParameter(2, scope).setParameter(3, key)
                .setParameter(4, resourceId).executeUpdate();
    }
}
