package pe.logyx.shared.tenancy;

import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import java.util.function.Supplier;

/**
 * Ejecuta una operación de plataforma en una transacción nueva sobre el datasource <b>privilegiado</b>
 * (dueño del esquema, sin RLS). Ver ADR-0001 y ADR-0006.
 *
 * <p>Es la <b>única</b> puerta a ese datasource: toda transacción que no pase por aquí usa el usuario de la
 * aplicación ({@code logyx_api}, miembro solo de {@code logyx_app}), autenticada o no. Por eso login, registro,
 * jobs programados y el seed dev también entran por aquí. Cada uso debe estar comentado con su justificación.
 *
 * <p>Si ya se está dentro de una transacción de sistema activa, la operación se une a ella (no abre otra):
 * así el despachador del outbox crea las notificaciones en su misma transacción. Desde una transacción de
 * usuario siempre se abre una nueva, que <b>no</b> ve lo no confirmado de la externa.
 *
 * <p>Si no hay request context (jobs, arranque) lo activa durante la operación: Hibernate resuelve el
 * datasource por request context.
 */
@ApplicationScoped
public class SystemTx {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** true mientras el hilo actual ejecuta una operación de sistema. */
    public static boolean isActive() {
        return ACTIVE.get();
    }

    public <T> T call(Supplier<T> work) {
        if (isActive() && transactionActive()) return work.get();

        var rc = Arc.container().requestContext();
        boolean activated = !rc.isActive();
        if (activated) rc.activate();
        boolean previous = ACTIVE.get();
        ACTIVE.set(Boolean.TRUE);
        try {
            return QuarkusTransaction.requiringNew().call(work::get);
        } finally {
            ACTIVE.set(previous);
            if (activated) rc.terminate();
        }
    }

    public void run(Runnable work) {
        call(() -> { work.run(); return null; });
    }

    private static boolean transactionActive() {
        try {
            TransactionManager tm = Arc.container().instance(TransactionManager.class).get();
            return tm.getStatus() == Status.STATUS_ACTIVE;
        } catch (SystemException e) {
            return false;
        }
    }
}
