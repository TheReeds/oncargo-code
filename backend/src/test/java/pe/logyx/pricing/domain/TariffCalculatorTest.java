package pe.logyx.pricing.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.CargoType;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TariffCalculatorTest {

    private final TariffCalculator calculator = new TariffCalculator(new PriceFloorPolicy(new BigDecimal("0.75")));

    @Test
    void calculaComponentesSegunFormulaDocumentada() {
        // 1000 km, 5 t, carga general
        var q = calculator.quote(1000, new BigDecimal("5000"), CargoType.GENERAL);
        assertEquals(new BigDecimal("650.00"), q.fuelCost());        // 1000 × 0.65
        assertEquals(new BigDecimal("4000.00"), q.transportCost());  // 1000 × 5 × 0.80
        assertEquals(new BigDecimal("108.00"), q.tollCost());        // ceil(1000/120)=9 × 12
        assertEquals(new BigDecimal("200.00"), q.handlingFee());     // 5% de 4000
        assertEquals(new BigDecimal("4958.00"), q.suggestedPrice());
        assertEquals(new BigDecimal("3718.50"), q.priceFloor());     // RN03: 75%
    }

    @Test
    void aplicaMultiplicadorPorTipoDeCarga() {
        var general = calculator.quote(500, new BigDecimal("2000"), CargoType.GENERAL);
        var peligrosa = calculator.quote(500, new BigDecimal("2000"), CargoType.HAZARDOUS);
        assertEquals(0, peligrosa.transportCost().compareTo(general.transportCost().multiply(new BigDecimal("1.5"))));
    }

    @Test
    void todoTipoDeCargaTieneMultiplicador() {
        for (CargoType c : CargoType.values()) {
            assertTrue(TariffCalculator.multiplier(c).signum() > 0, c.name());
        }
    }
}
