package pe.logyx.shared.tenancy;

import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.TransactionScoped;

/**
 * Enlaza cada transacción JTA con la organización del usuario autenticado.
 *
 * <p>Al iniciar la transacción ejecuta, sobre la misma conexión que usará
 * Hibernate:
 * <pre>
 *   set_config('app.org_id', ..., true)   -- LOCAL: muere con la transacción
 *   SET LOCAL ROLE logyx_app              -- rol sin BYPASSRLS
 * </pre>
 * A partir de ahí PostgreSQL aplica las políticas RLS de V2. Si no hay usuario
 * (login, registro, scheduler) o la operación es de sistema, no se hace nada y
 * la transacción corre con el rol dueño del esquema.
 *
 * <p>Por eso todo acceso a datos de usuario debe ir dentro de {@code @Transactional}.
 */
@ApplicationScoped
public class TenantSessionBinder {

    @Inject
    EntityManager em;

    void onTransactionBegin(@Observes @Initialized(TransactionScoped.class) Object event) {
        if (!Arc.container().requestContext().isActive()) return;
        TenantContext ctx = Arc.container().instance(TenantContext.class).get();
        if (ctx.isSystem()) return;
        var org = ctx.currentOrgId();
        if (org.isEmpty()) return;

        em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true)")
                .setParameter(1, org.get().toString())
                .setParameter(2, ctx.orgType().name())
                .getSingleResult();
        em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
    }
}
