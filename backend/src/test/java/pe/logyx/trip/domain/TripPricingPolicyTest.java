package pe.logyx.trip.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.trip.domain.model.TripPricingMode;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TripPricingPolicyTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test
    void porKgEsTarifaPorPeso() {
        assertEquals(d("750.00"), TripPricingPolicy.price(TripPricingMode.PER_KG, d("0.50"), d("8000"), d("1500")));
    }

    @Test
    void porTramoSeProrrateaPorLaCapacidadPublicada() {
        // tarifa 4000 por 8000 kg → 0.50 por kg
        assertEquals(d("750.00"), TripPricingPolicy.price(TripPricingMode.PER_TRIP, d("4000"), d("8000"), d("1500")));
        // cargar todo el vehículo paga el tramo completo
        assertEquals(d("4000.00"), TripPricingPolicy.price(TripPricingMode.PER_TRIP, d("4000"), d("8000"), d("8000")));
    }

    @Test
    void redondeaAdosDecimalesHalfUp() {
        assertEquals(d("333.33"), TripPricingPolicy.price(TripPricingMode.PER_TRIP, d("1000"), d("3000"), d("1000")));
        assertEquals(d("0.67").max(TripPricingPolicy.MIN_PRICE),
                TripPricingPolicy.price(TripPricingMode.PER_TRIP, d("1000"), d("3000"), d("2")));
    }

    @Test
    void nuncaBajaDelPrecioMinimo() {
        assertEquals(TripPricingPolicy.MIN_PRICE,
                TripPricingPolicy.price(TripPricingMode.PER_KG, d("0.01"), d("8000"), d("1")));
    }
}
