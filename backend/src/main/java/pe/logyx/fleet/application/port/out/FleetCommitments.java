package pe.logyx.fleet.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lo que compromete a un vehículo o a un conductor en otros módulos (envíos activos, ofertas pendientes, viajes
 * publicados). Puerto para no acoplar {@code fleet} a {@code shipment}, {@code marketplace} y {@code trip}, que ya
 * dependen de {@code fleet}; lo implementa {@code fleet/infrastructure/integration}.
 */
public interface FleetCommitments {

    /** Un envío activo: id y días que ocupa el recurso (ambos inclusive). */
    record ActiveShipment(UUID shipmentId, LocalDate from, LocalDate to) {}

    List<ActiveShipment> activeShipmentsOfVehicle(UUID vehicleId);

    List<ActiveShipment> activeShipmentsOfDriver(UUID driverId);

    /** Ofertas PENDING del vehículo (su agenda: pueden convertirse en envío). */
    int pendingBidsOfVehicle(UUID vehicleId);

    /** Viajes publicados (abiertos o por llenar) con ese vehículo (RN12). */
    int openTripsOfVehicle(UUID vehicleId);
}
