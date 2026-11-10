package pe.logyx.pricing;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.City;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private PricingService service(double km) {
        PricingService s = new PricingService();
        s.distance = (a, b) -> km;
        s.floorRatio = new BigDecimal("0.75");
        return s;
    }

    @Test
    void calculaComponentesSegunFormulaDocumentada() {
        // 1000 km, 5 t, carga general
        var q = service(1000).quote(City.LIMA, City.AREQUIPA, new BigDecimal("5000"), CargoType.GENERAL);
        assertEquals(new BigDecimal("650.00"), q.fuelCost());        // 1000 × 0.65
        assertEquals(new BigDecimal("4000.00"), q.transportCost());  // 1000 × 5 × 0.80
        assertEquals(new BigDecimal("108.00"), q.tollCost());        // ceil(1000/120)=9 × 12
        assertEquals(new BigDecimal("200.00"), q.handlingFee());     // 5% de 4000
        assertEquals(new BigDecimal("4958.00"), q.suggestedPrice());
        assertEquals(new BigDecimal("3718.50"), q.priceFloor());     // RN03: 75%
    }

    @Test
    void aplicaMultiplicadorPorTipoDeCarga() {
        var general = service(500).quote(City.LIMA, City.ICA, new BigDecimal("2000"), CargoType.GENERAL);
        var peligrosa = service(500).quote(City.LIMA, City.ICA, new BigDecimal("2000"), CargoType.HAZARDOUS);
        assertEquals(0, peligrosa.transportCost().compareTo(general.transportCost().multiply(new BigDecimal("1.5"))));
    }

    @Test
    void distanciaHaversineLimaArequipaEsRazonable() {
        double km = new HaversineDistanceProvider().roadDistanceKm(City.LIMA, City.AREQUIPA);
        // Por carretera son ~1,010 km; la aproximación debe quedar en ±15%.
        assertTrue(km > 860 && km < 1160, "km=" + km);
    }
}
