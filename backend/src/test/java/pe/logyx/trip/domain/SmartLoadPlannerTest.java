package pe.logyx.trip.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.trip.domain.SmartLoadPlanner.Load;
import pe.logyx.trip.domain.SmartLoadPlanner.Plan;
import pe.logyx.trip.domain.SmartLoadPlanner.VehicleSlot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pe.logyx.trip.domain.Roads.PROXIMITY;

class SmartLoadPlannerTest {

    static final LocalDate D = LocalDate.of(2026, 11, 12);

    static VehicleSlot truck(String kg) {
        return new VehicleSlot(UUID.randomUUID(), Vehicle.Type.TRUCK_MEDIUM, "ASA-101", new BigDecimal(kg));
    }

    static Load load(City o, City d, String kg, LocalDate pickup, String income, CargoType cargo) {
        return new Load(UUID.randomUUID(), UUID.randomUUID(), o, d, new BigDecimal(kg), pickup, new BigDecimal(income), cargo);
    }

    static Load load(City o, City d, String kg, String income) {
        return load(o, d, kg, D, income, CargoType.GENERAL);
    }

    @Test
    void combinaDosCargasCompatiblesConOcupacionSuficiente() {
        Load a = load(City.LIMA, City.JULIACA, "3000", "2500");
        Load b = load(City.LIMA, City.PUNO, "2500", "2200");
        List<Plan> plans = SmartLoadPlanner.plan(List.of(truck("8000")), List.of(a, b), PROXIMITY);
        assertEquals(1, plans.size());
        Plan p = plans.get(0);
        assertEquals(2, p.loads().size());
        assertEquals(new BigDecimal("5500"), p.totalWeightKg());
        assertEquals(new BigDecimal("4700"), p.income());
        assertEquals(new BigDecimal("68.75"), p.occupancyPct());
        // 2 recojos y 2 entregas, recojos primero
        assertEquals(4, p.route().size());
        assertEquals(SmartLoadPlanner.StopType.PICKUP, p.route().get(0).type());
        assertEquals(SmartLoadPlanner.StopType.DELIVERY, p.route().get(3).type());
    }

    @Test
    void bajoElSesentaPorCientoNoSeSugiere() {
        Load a = load(City.LIMA, City.JULIACA, "2000", "1500");
        Load b = load(City.LIMA, City.PUNO, "2000", "1500");
        assertTrue(SmartLoadPlanner.plan(List.of(truck("8000")), List.of(a, b), PROXIMITY).isEmpty());   // 50 %
    }

    @Test
    void exactamenteSesentaPorCientoSiCuenta() {
        Load a = load(City.LIMA, City.JULIACA, "2400", "1500");
        Load b = load(City.LIMA, City.PUNO, "2400", "1500");
        assertEquals(1, SmartLoadPlanner.plan(List.of(truck("8000")), List.of(a, b), PROXIMITY).size());   // 4800/8000 = 60 %
    }

    @Test
    void noSuperaLaCapacidadDelVehiculo() {
        Load a = load(City.LIMA, City.JULIACA, "5000", "3000");
        Load b = load(City.LIMA, City.PUNO, "4000", "3000");
        assertTrue(SmartLoadPlanner.plan(List.of(truck("8000")), List.of(a, b), PROXIMITY).isEmpty());
    }

    @Test
    void fechasConMasDeUnDiaDeDiferenciaNoSeCombinan() {
        Load a = load(City.LIMA, City.JULIACA, "3000", D, "2500", CargoType.GENERAL);
        Load b = load(City.LIMA, City.PUNO, "3000", D.plusDays(2), "2200", CargoType.GENERAL);
        assertTrue(SmartLoadPlanner.plan(List.of(truck("8000")), List.of(a, b), PROXIMITY).isEmpty());
        Load c = load(City.LIMA, City.PUNO, "3000", D.plusDays(1), "2200", CargoType.GENERAL);
        assertEquals(1, SmartLoadPlanner.plan(List.of(truck("8000")), List.of(a, c), PROXIMITY).size());
    }

    @Test
    void destinosLejanosONoCercanosNoSeCombinan() {
        Load a = load(City.LIMA, City.JULIACA, "3000", "2500");
        Load b = load(City.LIMA, City.TACNA, "3000", "2200");           // Juliaca–Tacna > 80 km
        assertTrue(SmartLoadPlanner.plan(List.of(truck("8000")), List.of(a, b), PROXIMITY).isEmpty());
        Load c = load(City.AREQUIPA, City.JULIACA, "3000", "2200");     // orígenes Lima–Arequipa > 50 km
        assertTrue(SmartLoadPlanner.plan(List.of(truck("8000")), List.of(a, c), PROXIMITY).isEmpty());
    }

    @Test
    void cargaPeligrosaNoSeMezclaConOtrasYPerecederoSoloVaEnRefrigerado() {
        Load g = load(City.LIMA, City.JULIACA, "3000", D, "2500", CargoType.GENERAL);
        Load h = load(City.LIMA, City.PUNO, "3000", D, "2200", CargoType.HAZARDOUS);
        assertTrue(SmartLoadPlanner.plan(List.of(truck("8000")), List.of(g, h), PROXIMITY).isEmpty());

        Load p1 = load(City.LIMA, City.JULIACA, "2000", D, "2500", CargoType.PERISHABLE);
        Load p2 = load(City.LIMA, City.PUNO, "2000", D, "2200", CargoType.PERISHABLE);
        assertTrue(SmartLoadPlanner.plan(List.of(truck("3000")), List.of(p1, p2), PROXIMITY).isEmpty(), "camión común no lleva perecederos");
        VehicleSlot reefer = new VehicleSlot(UUID.randomUUID(), Vehicle.Type.REFRIGERATED, "ASA-303", new BigDecimal("6000"));
        assertEquals(1, SmartLoadPlanner.plan(List.of(reefer), List.of(p1, p2), PROXIMITY).size());
    }

    @Test
    void eligeLaCombinacionDeMayorIngresoYNoRepiteCargasEntreVehiculos() {
        Load a = load(City.LIMA, City.JULIACA, "3000", "1000");
        Load b = load(City.LIMA, City.JULIACA, "3000", "5000");
        Load c = load(City.LIMA, City.PUNO, "3000", "2000");
        VehicleSlot v1 = truck("6000");     // admite dos de las tres
        VehicleSlot v2 = truck("9000");
        List<Plan> plans = SmartLoadPlanner.plan(List.of(v2, v1), List.of(a, b, c), PROXIMITY);
        assertEquals(1, plans.size(), "las tres cargas no alcanzan para dos sugerencias de ≥ 2 ítems");
        assertEquals(v1.vehicleId(), plans.get(0).vehicle().vehicleId(), "primero el vehículo más chico");
        assertEquals(new BigDecimal("7000"), plans.get(0).income(), "b + c");
    }

    @Test
    void unaSolaCargaNoEsCombinacion() {
        assertTrue(SmartLoadPlanner.plan(List.of(truck("4000")), List.of(load(City.LIMA, City.JULIACA, "4000", "3000")), PROXIMITY).isEmpty());
    }

    @Test
    void esDeterminista() {
        List<Load> loads = List.of(load(City.LIMA, City.JULIACA, "3000", "2500"), load(City.LIMA, City.PUNO, "2500", "2200"));
        VehicleSlot v = truck("8000");
        assertEquals(SmartLoadPlanner.plan(List.of(v), loads, PROXIMITY).get(0).loads(), SmartLoadPlanner.plan(List.of(v), loads, PROXIMITY).get(0).loads());
    }

    @Test
    void ordenaLasParadasPorMenosKmPorCarretera() {
        // Juliaca → Lima y Puno → Lima el mismo día: Puno → Juliaca → Lima = 42.8 + 1255.1 = 1297.9 km,
        // mejor que Juliaca → Puno → Lima = 42.9 + 1281.3 = 1324.2 km (ADR-0008).
        Load j = load(City.JULIACA, City.LIMA, "3000", "2500");
        Load p = load(City.PUNO, City.LIMA, "3000", "2500");
        Plan plan = SmartLoadPlanner.plan(List.of(truck("8000")), List.of(j, p), PROXIMITY).get(0);
        assertEquals(List.of(City.PUNO, City.JULIACA, City.LIMA, City.LIMA), plan.route().stream().map(SmartLoadPlanner.Stop::city).toList());
        assertEquals(1297.9, plan.routeKm(), 0.05);
        assertEquals(p.requestId(), plan.loads().get(0).requestId(), "pickupOrder 0 = Puno");
    }

    @Test
    void losRecojosRespetanLaFechaAunqueSeaMasLargo() {
        Load j = load(City.JULIACA, City.LIMA, "3000", D, "2500", CargoType.GENERAL);
        Load p = load(City.PUNO, City.LIMA, "3000", D.plusDays(1), "2500", CargoType.GENERAL);
        Plan plan = SmartLoadPlanner.plan(List.of(truck("8000")), List.of(j, p), PROXIMITY).get(0);
        assertEquals(City.JULIACA, plan.route().get(0).city());
        assertEquals(1324.2, plan.routeKm(), 0.05);
    }

    @Test
    void lasEntregasVanEnElOrdenMasCorto() {
        // Desde Lima a Juliaca y a Puno: Lima → Juliaca → Puno (1248.5 + 42.9) es más corto que Lima → Puno → Juliaca.
        Load toPuno = load(City.LIMA, City.PUNO, "3000", "2500");
        Load toJuliaca = load(City.LIMA, City.JULIACA, "3000", "2500");
        Plan plan = SmartLoadPlanner.plan(List.of(truck("8000")), List.of(toPuno, toJuliaca), PROXIMITY).get(0);
        assertEquals(List.of(City.JULIACA, City.PUNO), plan.route().subList(2, 4).stream().map(SmartLoadPlanner.Stop::city).toList());
        assertEquals(1291.4, plan.routeKm(), 0.05);
        // deliveryOrder[i] = posición de entrega de la carga recogida en el lugar i
        int juliacaPickup = plan.loads().indexOf(toJuliaca);
        assertEquals(0, plan.deliveryOrder().get(juliacaPickup));
    }
}
