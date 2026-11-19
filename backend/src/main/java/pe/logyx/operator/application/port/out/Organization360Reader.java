package pe.logyx.operator.application.port.out;

import java.util.List;
import java.util.UUID;

/** Conteos y listas de la ficha 360 de una organización (2.15): SQL de solo lectura bajo las políticas del operador. */
public interface Organization360Reader {

    record Counts(long vehicles, long vehiclesArchived, long drivers, long shipmentsPending, long shipmentsInTransit,
                  long shipmentsDelivered, long shipmentsCancelled, long cancelledByThisOrg, long bids,
                  long bidsWithdrawn, long bidsAccepted, long requests, long requestsCancelled, long incidents,
                  long incidentsActive, long incidentsReported) {}

    record VehicleRow(UUID id, String plate, String type, String status, boolean archived) {}

    Counts counts(UUID organizationId);

    List<VehicleRow> vehicles(UUID organizationId);

    /** Envíos más recientes en los que es parte. */
    List<UUID> recentShipments(UUID organizationId, int limit);
}
