package pe.logyx.shared.tenancy;

import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.function.Supplier;

/**
 * Ejecuta una operación en una transacción nueva <b>sin</b> RLS (rol dueño).
 * Uso restringido a operaciones de plataforma que por definición cruzan
 * organizaciones (p. ej. el cierre automático de subastas). Cada uso debe
 * poder justificarse en revisión de código.
 */
@ApplicationScoped
public class SystemTx {

    public <T> T call(Supplier<T> work) {
        var rc = Arc.container().requestContext();
        TenantContext ctx = rc.isActive() ? Arc.container().instance(TenantContext.class).get() : null;
        boolean previous = ctx != null && ctx.isSystem();
        if (ctx != null) ctx.setSystem(true);
        try {
            return QuarkusTransaction.requiringNew().call(work::get);
        } finally {
            if (ctx != null) ctx.setSystem(previous);
        }
    }

    public void run(Runnable work) {
        call(() -> { work.run(); return null; });
    }
}
