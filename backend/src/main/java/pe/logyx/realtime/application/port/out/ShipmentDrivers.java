package pe.logyx.realtime.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Usuario (profile) del conductor asignado a un envío, para dirigirle sus eventos. Se usa en el despacho (SystemTx). */
public interface ShipmentDrivers {
    Optional<UUID> driverProfileOf(UUID shipmentId);
}
