package pe.logyx.fleet.application.port.out;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que ocupa la flota del transportista y lo que hace falta para sugerir una asignación (2.13): envíos activos,
 * ofertas pendientes y viajes publicados, con la ventana {@code recojo + ⌈km/500⌉ días} de {@code ResourceSchedulePolicy}.
 * Puerto para que {@code fleet} no dependa de {@code shipment}, {@code marketplace} ni {@code trip}; lo implementa
 * {@code fleet/infrastructure/integration/FleetPlanningAdapter} bajo el RLS del transportista.
 */
public interface FleetPlanning {

    enum Kind { SHIPMENT, BID, TRIP }

    /**
     * Un compromiso de la agenda. {@code driverId} solo en envíos; {@code destination}: ciudad donde termina (para
     * estimar qué conductor queda más cerca del siguiente recojo). {@code label}: ruta, sin datos de otras partes.
     */
    record Slot(Kind kind, UUID refId, String label, LocalDate from, LocalDate to, UUID vehicleId, UUID driverId,
                City destination) {}

    /** Compromisos de mi flota: todos mis envíos activos + las ofertas pendientes y viajes abiertos de esos vehículos. */
    java.util.List<Slot> slots(Collection<UUID> vehicleIds);

    /** El envío a asignar (de mi organización) con la ventana que ocuparía. */
    record ShipmentPlan(UUID shipmentId, String routeLabel, City origin, City destination, CargoType cargoType,
                        BigDecimal weightKg, LocalDate from, LocalDate to, UUID vehicleId, UUID driverId,
                        boolean assignable) {}

    Optional<ShipmentPlan> shipmentPlan(UUID shipmentId);

    /** ¿Puede ese vehículo llevar el envío? Mismas reglas que ofertar (RN02 por fecha, agenda y documentos). */
    record VehicleFit(boolean eligible, String code, String message) {}

    VehicleFit vehicleFit(UUID shipmentId, UUID vehicleId);

    /** Kilómetros por carretera entre dos ciudades (para ordenar conductores por cercanía). */
    double distanceKm(City from, City to);
}
