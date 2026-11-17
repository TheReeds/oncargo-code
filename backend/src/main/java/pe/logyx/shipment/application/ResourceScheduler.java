package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shipment.application.port.out.RequestInfoProvider;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.domain.ResourceSchedulePolicy;
import pe.logyx.shipment.domain.ResourceSchedulePolicy.Commitment;
import pe.logyx.shipment.domain.ResourceSchedulePolicy.Window;
import pe.logyx.shipment.domain.model.Shipment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * F11: comprueba que un vehículo o un conductor no estén ya comprometidos en otro envío activo que se solape
 * ({@link ResourceSchedulePolicy}). Lo usan ofertar y adjudicar (marketplace), asignar conductor y cambiar vehículo.
 *
 * <p>Carreras: primero toma un bloqueo consultivo por recurso en la transacción del usuario (se libera al
 * confirmar), luego lee los envíos activos <b>ya confirmados</b> en una transacción de sistema nueva. Dos
 * adjudicaciones concurrentes del mismo vehículo se serializan: la segunda espera el commit de la primera y la ve.
 *
 * <p>SystemTx justificado: quien adjudica es la PYME, que bajo RLS no ve los demás envíos del transportista
 * (otras PYMEs); solo se leen ids, fechas y grupo de carga, nada se devuelve al cliente salvo las fechas del choque.
 */
@ApplicationScoped
public class ResourceScheduler {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Inject ShipmentRepository shipments;
    @Inject RequestInfoProvider requests;
    @Inject SystemTx system;

    public Window window(LocalDate pickupDate, BigDecimal distanceKm) {
        return ResourceSchedulePolicy.window(pickupDate, distanceKm, LocalDate.now(LIMA));
    }

    /** Ventana del envío según su solicitud (fecha de recojo y distancia). */
    @Transactional(Transactional.TxType.MANDATORY)
    public Window windowOf(Shipment s) {
        var info = requests.byIds(List.of(s.requestId())).get(s.requestId());
        LocalDate today = LocalDate.now(LIMA);
        return info == null ? new Window(today, today) : window(info.pickupDate(), info.distanceKm());
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void ensureVehicleFree(UUID vehicleId, Window window, UUID loadGroupId, UUID excludeShipmentId) {
        shipments.lockResource("vehicle:" + vehicleId);
        List<Commitment> active = system.call(() -> commitments(shipments.listActiveByVehicle(vehicleId)));
        ResourceSchedulePolicy.ensureVehicleFree(active, vehicleId, window, loadGroupId, excludeShipmentId);
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void ensureDriverFree(UUID driverId, UUID vehicleId, Window window, UUID loadGroupId, UUID excludeShipmentId) {
        shipments.lockResource("driver:" + driverId);
        List<Commitment> active = system.call(() -> commitments(shipments.listActiveByDriver(driverId)));
        ResourceSchedulePolicy.ensureDriverFree(active, driverId, vehicleId, window, loadGroupId, excludeShipmentId);
    }

    private List<Commitment> commitments(List<Shipment> list) {
        if (list.isEmpty()) return List.of();
        var infos = requests.byIds(list.stream().map(Shipment::requestId).toList());
        LocalDate today = LocalDate.now(LIMA);
        return list.stream().map(s -> {
            var i = infos.get(s.requestId());
            Window w = i == null ? new Window(today, today) : window(i.pickupDate(), i.distanceKm());
            return new Commitment(s.id(), s.vehicleId(), s.driverId(), s.loadGroupId(), w);
        }).toList();
    }
}
