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
 * A partir de ahí PostgreSQL aplica las políticas RLS de V2. La conexión ya es del
 * usuario de la aplicación ({@code logyx_api}, miembro solo de {@code logyx_app}; ADR-0006),
 * así que el {@code SET ROLE} no da privilegios: fija el rol explícitamente. Sin usuario
 * (y fuera de {@link SystemTx}) no se fija organización y las políticas no devuelven filas.
 * Las operaciones de sistema usan otra conexión (datasource privilegiado) y no pasan por aquí.
 *
 * <p>Por eso todo acceso a datos de usuario debe ir dentro de {@code @Transactional}.
 */
@ApplicationScoped
public class TenantSessionBinder {

    @Inject
    EntityManager em;

    void onTransactionBegin(@Observes @Initialized(TransactionScoped.class) Object event) {
        if (SystemTx.isActive() || !Arc.container().requestContext().isActive()) return;
        TenantContext ctx = Arc.container().instance(TenantContext.class).get();
        var org = ctx.currentOrgId();
        if (org.isEmpty()) return;

        em.createNativeQuery("SELECT set_config('app.org_id', ?1, true), set_config('app.org_type', ?2, true)")
                .setParameter(1, org.get().toString())
                .setParameter(2, ctx.orgType().name())
                .getSingleResult();
        em.createNativeQuery("SET LOCAL ROLE logyx_app").executeUpdate();
    }
}
