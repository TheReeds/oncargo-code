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

    @Test
    void unaVersionDelTarifarioCambiaElSugeridoYDejaSuNumero() {
        var std = calculator.quote(1000, new BigDecimal("2000"), pe.logyx.catalog.domain.CargoType.FRAGILE);
        assertEquals(1, std.tariffVersion());
        assertEquals(0, BigDecimal.ZERO.compareTo(std.baseFee()));
        var m = new java.util.EnumMap<pe.logyx.catalog.domain.CargoType, BigDecimal>(pe.logyx.pricing.domain.model.Tariff.STANDARD.cargoMultipliers());
        m.put(pe.logyx.catalog.domain.CargoType.FRAGILE, new BigDecimal("2.00"));
        var t = new pe.logyx.pricing.domain.model.Tariff(7, new BigDecimal("150"), new BigDecimal("0.65"),
                new BigDecimal("0.80"), new BigDecimal("120"), new BigDecimal("12"), new BigDecimal("0.05"), m, null);
        t.validate();
        var q = new TariffCalculator(t, new PriceFloorPolicy(new BigDecimal("0.75"))).quote(1000, new BigDecimal("2000"),
                pe.logyx.catalog.domain.CargoType.FRAGILE);
        assertEquals(7, q.tariffVersion());
        // transporte 1000 × 2 × 0.80 × 2.00 = 3 200 (antes × 1.30 = 2 080); manipuleo 5 %; + base 150
        assertEquals(0, new BigDecimal("3200.00").compareTo(q.transportCost()));
        assertEquals(0, std.suggestedPrice().add(new BigDecimal("150")).add(new BigDecimal("1120"))
                .add(new BigDecimal("56")).compareTo(q.suggestedPrice()));
        m.put(pe.logyx.catalog.domain.CargoType.BULK, new BigDecimal("0.1"));
        var bad = new pe.logyx.pricing.domain.model.Tariff(8, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("120"), BigDecimal.ONE, BigDecimal.ZERO, m, null);
        var e = assertThrows(pe.logyx.shared.domain.BusinessException.class, bad::validate);
        assertEquals("cargoMultipliers.BULK", e.field());
    }
}
