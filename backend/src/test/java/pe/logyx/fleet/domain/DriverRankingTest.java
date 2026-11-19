package pe.logyx.fleet.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.fleet.domain.DriverRanking.Candidate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DriverRankingTest {

    private static Candidate c(String name, boolean active, boolean free, int workload, Double km) {
        return new Candidate(UUID.randomUUID(), name, active, free, workload, km);
    }

    @Test
    void primeroLosAsignablesLuegoCercaniaYCargaDeTrabajo() {
        var ocupado = c("Ana", true, false, 0, 0.0);
        var inactivo = c("Beto", false, true, 0, 0.0);
        var lejos = c("Carla", true, true, 0, 650.0);
        var cercaMuyCargado = c("Dario", true, true, 9, 30.0);
        var cercaLibre = c("Elena", true, true, 2, 80.0);
        var sinDato = c("Fabio", true, true, 0, null);
        var orden = DriverRanking.rank(List.of(ocupado, inactivo, lejos, cercaMuyCargado, cercaLibre, sinDato))
                .stream().map(Candidate::name).toList();
        // Dentro de los 100 km pesa la carga de trabajo (Elena 2 < Darío 9); luego Carla (600 km) y el que no tiene dato
        assertEquals(List.of("Elena", "Dario", "Carla", "Fabio"), orden.subList(0, 4));
        assertTrue(orden.subList(4, 6).containsAll(List.of("Ana", "Beto")), "los no asignables van al final");
    }

    @Test
    void asignableExigeCuentaActivaYLibre() {
        assertTrue(c("x", true, true, 0, null).assignable());
        assertFalse(c("x", false, true, 0, null).assignable());
        assertFalse(c("x", true, false, 0, null).assignable());
    }
}
