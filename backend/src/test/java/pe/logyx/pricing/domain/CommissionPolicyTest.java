package pe.logyx.pricing.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.pricing.domain.model.AwardedPriceBreakdown;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Comisión al transportista (8 % inicial) e IGV (18 %) desglosado del precio adjudicado. */
class CommissionPolicyTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test
    void desglosaIgvYComisionDeUnPrecioRedondo() {
        AwardedPriceBreakdown b = CommissionPolicy.breakdown(d("1180.00"), d("0.08"));
        assertEquals(d("1180.00"), b.amount());
        assertEquals(d("1000.00"), b.netOfVat());
        assertEquals(d("180.00"), b.vat());
        assertEquals(d("0.18"), b.vatRate());
        assertEquals(d("94.40"), b.commission());       // 8 % de 1 180.00
        assertEquals(d("1085.60"), b.carrierNet());     // cobras neto
        assertEquals(d("0.08"), b.commissionRate());
    }

    @Test
    void netoMasIgvSiempreSumaElTotalYNetoMasComisionTambien() {
        for (String amount : new String[]{"1.00", "99.99", "1850.00", "3333.33", "12345.67"}) {
            AwardedPriceBreakdown b = CommissionPolicy.breakdown(d(amount), d("0.08"));
            assertEquals(b.amount(), b.netOfVat().add(b.vat()), "IGV " + amount);
            assertEquals(b.amount(), b.carrierNet().add(b.commission()), "comisión " + amount);
            assertEquals(2, b.commission().scale());
        }
    }

    @Test
    void redondeaMitadHaciaArribaALosCentimos() {
        // 8 % de 10.06 = 0.8048 → 0.80 ; 8 % de 10.07 = 0.8056 → 0.81
        assertEquals(d("0.80"), CommissionPolicy.breakdown(d("10.06"), d("0.08")).commission());
        assertEquals(d("0.81"), CommissionPolicy.breakdown(d("10.07"), d("0.08")).commission());
        // 8.25 % de 100.00 = 8.25
        assertEquals(d("8.25"), CommissionPolicy.breakdown(d("100"), d("0.0825")).commission());
    }

    @Test
    void conTasaCeroElTransportistaCobraTodo() {
        AwardedPriceBreakdown b = CommissionPolicy.breakdown(d("2500"), BigDecimal.ZERO);
        assertEquals(d("0.00"), b.commission());
        assertEquals(d("2500.00"), b.carrierNet());
    }

    @Test
    void rechazaMontosOTasasInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> CommissionPolicy.breakdown(d("-1"), d("0.08")));
        assertThrows(IllegalArgumentException.class, () -> CommissionPolicy.breakdown(d("100"), d("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> CommissionPolicy.breakdown(d("100"), d("1.01")));
    }
}
