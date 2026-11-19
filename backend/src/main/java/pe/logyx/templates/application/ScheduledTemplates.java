package pe.logyx.templates.application;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SyntheticTenant;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.templates.application.port.out.DueSchedules;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job de plantillas recurrentes (roadmap 2.1, ADR-0013). Busca las programaciones que ya tocan (SystemTx: sin usuario,
 * entre organizaciones, solo ids) y ejecuta cada una <b>en nombre de su organización</b> con {@link SyntheticTenant}: la
 * publicación pasa por el mismo caso de uso y la misma RLS que si la hiciera la persona. Una ocurrencia que falla se
 * registra y la programación avanza: no hay reintento en bucle.
 */
@ApplicationScoped
public class ScheduledTemplates {

    public static final int BATCH = 200;

    @Inject DueSchedules due;
    @Inject SystemTx system;
    @Inject SyntheticTenant synthetic;
    @Inject ScheduleStep step;

    /** Ejecuta lo vencido a {@code now}. Devuelve cuántas programaciones se intentaron. */
    public int runDue(OffsetDateTime now) {
        // SystemTx: job sin usuario; solo lee los ids de las programaciones vencidas de todas las organizaciones.
        List<DueSchedules.Due> pending = system.call(() -> due.find(now, BATCH));
        for (DueSchedules.Due d : pending) {
            try {
                synthetic.runAs(d.organizationId(), OrgType.PYME, d.createdBy(), () -> runOne(d));
            } catch (RuntimeException e) {
                Log.errorf(e, "No se pudo ejecutar la programación %s", d.scheduleId());
            }
        }
        return pending.size();
    }

    private void runOne(DueSchedules.Due d) {
        try {
            step.attempt(d.scheduleId());
        } catch (RuntimeException e) {
            step.recordFailure(d.scheduleId(), e);
        }
    }
}
