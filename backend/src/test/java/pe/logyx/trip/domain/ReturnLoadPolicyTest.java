package pe.logyx.trip.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.trip.domain.ReturnLoadPolicy.Candidate;
import pe.logyx.trip.domain.ReturnLoadPolicy.Leg;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReturnLoadPolicyTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    static Leg leg(City destination, boolean delivered, LocalDate from, String capacity, Vehicle.Type type) {
        return new Leg(UUID.randomUUID(), UUID.randomUUID(), "ASA-101", type, new BigDecimal(capacity), destination,
                delivered, from);
    }

    static Candidate cand(City origin, City destination, String kg, LocalDate pickup, CargoType cargo) {
        return new Candidate(UUID.randomUUID(), origin, destination, new BigDecimal(kg), pickup, cargo);
    }

    @Test
    void mismaCiudadYCompatibleEsRetornoConMaximoPuntaje() {
        Leg l = leg(City.JULIACA, true, TODAY, "20000", Vehicle.Type.TRUCK_LARGE);
        Candidate c = cand(City.JULIACA, City.LIMA, "12000", TODAY, CargoType.BULK);
        var m = ReturnLoadPolicy.match(List.of(l), List.of(c), TODAY);
        assertEquals(1, m.size());
        assertEquals(0, m.get(0).distanceKm());
        assertEquals(100, m.get(0).score());
    }

    @Test
    void ciudadCercanaDentroDelRadioEntraPeroConMenorPuntaje() {
        Leg l = leg(City.JULIACA, true, TODAY, "20000", Vehicle.Type.TRUCK_LARGE);
        Candidate same = cand(City.JULIACA, City.LIMA, "1000", TODAY, CargoType.GENERAL);
        Candidate near = cand(City.PUNO, City.TACNA, "1000", TODAY, CargoType.GENERAL);
        var m = ReturnLoadPolicy.match(List.of(l), List.of(near, same), TODAY);
        assertEquals(2, m.size());
        assertEquals(same.requestId(), m.get(0).candidate().requestId(), "la misma ciudad primero");
        assertEquals(44.0, m.get(1).distanceKm());
        assertTrue(m.get(1).score() < m.get(0).score());
    }

    @Test
    void ciudadLejanaNoEs() {
        Leg l = leg(City.JULIACA, true, TODAY, "20000", Vehicle.Type.TRUCK_LARGE);
        assertTrue(ReturnLoadPolicy.match(List.of(l),
                List.of(cand(City.AREQUIPA, City.LIMA, "1000", TODAY, CargoType.GENERAL)), TODAY).isEmpty());
    }

    @Test
    void noCabeEnElVehiculoNoSeSugiere() {
        Leg l = leg(City.JULIACA, true, TODAY, "8000", Vehicle.Type.TRUCK_MEDIUM);
        assertTrue(ReturnLoadPolicy.match(List.of(l),
                List.of(cand(City.JULIACA, City.LIMA, "12000", TODAY, CargoType.BULK)), TODAY).isEmpty());
    }

    @Test
    void fechaFueraDeLaVentanaOAnteriorALaDisponibilidadNoSirve() {
        Leg l = leg(City.JULIACA, true, TODAY, "20000", Vehicle.Type.TRUCK_LARGE);
        assertEquals(1, ReturnLoadPolicy.match(List.of(l),
                List.of(cand(City.JULIACA, City.LIMA, "100", TODAY.plusDays(7), CargoType.GENERAL)), TODAY).size());
        assertTrue(ReturnLoadPolicy.match(List.of(l),
                List.of(cand(City.JULIACA, City.LIMA, "100", TODAY.plusDays(8), CargoType.GENERAL)), TODAY).isEmpty());
        assertTrue(ReturnLoadPolicy.match(List.of(l),
                List.of(cand(City.JULIACA, City.LIMA, "100", TODAY.minusDays(1), CargoType.GENERAL)), TODAY).isEmpty());
    }

    @Test
    void entregadoHaceMasDeTresDiasYaNoGeneraRetorno() {
        Leg old = leg(City.JULIACA, true, TODAY.minusDays(4), "20000", Vehicle.Type.TRUCK_LARGE);
        assertTrue(ReturnLoadPolicy.match(List.of(old),
                List.of(cand(City.JULIACA, City.LIMA, "100", TODAY, CargoType.GENERAL)), TODAY).isEmpty());
        Leg recent = leg(City.JULIACA, true, TODAY.minusDays(3), "20000", Vehicle.Type.TRUCK_LARGE);
        assertEquals(1, ReturnLoadPolicy.match(List.of(recent),
                List.of(cand(City.JULIACA, City.LIMA, "100", TODAY, CargoType.GENERAL)), TODAY).size());
    }

    @Test
    void noEsRetornoSiVaAlMismoDestinoDelTramo() {
        Leg l = leg(City.JULIACA, false, TODAY, "20000", Vehicle.Type.TRUCK_LARGE);
        assertTrue(ReturnLoadPolicy.match(List.of(l),
                List.of(cand(City.PUNO, City.JULIACA, "100", TODAY, CargoType.GENERAL)), TODAY).isEmpty());
    }

    @Test
    void perecederoSoloEnRefrigerado() {
        Candidate lacteos = cand(City.JULIACA, City.LIMA, "100", TODAY, CargoType.PERISHABLE);
        assertTrue(ReturnLoadPolicy.match(
                List.of(leg(City.JULIACA, false, TODAY, "20000", Vehicle.Type.TRUCK_LARGE)), List.of(lacteos), TODAY).isEmpty());
        assertEquals(1, ReturnLoadPolicy.match(
                List.of(leg(City.JULIACA, false, TODAY, "6000", Vehicle.Type.REFRIGERATED)), List.of(lacteos), TODAY).size());
    }

    @Test
    void siVariosTramosEncajanGanaElDeMejorPuntaje() {
        Leg exact = leg(City.JULIACA, false, TODAY, "20000", Vehicle.Type.TRUCK_LARGE);
        Leg near = leg(City.PUNO, false, TODAY, "20000", Vehicle.Type.TRUCK_LARGE);
        Candidate c = cand(City.JULIACA, City.LIMA, "100", TODAY, CargoType.GENERAL);
        var m = ReturnLoadPolicy.match(List.of(near, exact), List.of(c), TODAY);
        assertEquals(1, m.size());
        assertEquals(exact.shipmentId(), m.get(0).leg().shipmentId());
    }
}
