package pe.logyx.templates.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Programaciones que ya tocan, de todas las organizaciones. Se lee sin RLS (el job no tiene usuario; ver
 * {@code SystemTx}) y solo devuelve ids: la ejecución de cada una vuelve a entrar bajo el tenant de su organización.
 */
public interface DueSchedules {

    record Due(UUID scheduleId, UUID organizationId, UUID createdBy) {}

    /** Las más atrasadas primero, hasta {@code max}. Excluye pausadas, terminadas y plantillas archivadas. */
    List<Due> find(OffsetDateTime now, int max);
}
