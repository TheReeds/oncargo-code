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
        assertEquals(d("80.00"), b.commission());       // 8 % del valor de venta (1 000.00), sin IGV
        assertEquals(d("14.40"), b.commissionVat());    // IGV de la comisión
        assertEquals(d("1085.60"), b.carrierNet());     // 1 180 - 80 - 14.40
        assertEquals(d("0.08"), b.commissionRate());
    }

    @Test
    void netoMasIgvSiempreSumaElTotalYNetoMasComisionTambien() {
        for (String amount : new String[]{"1.00", "99.99", "1850.00", "3333.33", "12345.67"}) {
            AwardedPriceBreakdown b = CommissionPolicy.breakdown(d(amount), d("0.08"));
            assertEquals(b.amount(), b.netOfVat().add(b.vat()), "IGV " + amount);
            assertEquals(b.amount(), b.carrierNet().add(b.commission()).add(b.commissionVat()), "comisión " + amount);
            assertEquals(2, b.commissionVat().scale());
            assertEquals(2, b.commission().scale());
        }
    }

    @Test
    void redondeaMitadHaciaArribaALosCentimos() {
        // valor de venta de 11.80 = 10.00 → 8 % = 0.80 (IGV de la comisión 0.144 → 0.14)
        AwardedPriceBreakdown b = CommissionPolicy.breakdown(d("11.80"), d("0.08"));
        assertEquals(d("0.80"), b.commission());
        assertEquals(d("0.14"), b.commissionVat());
        // valor de venta de 10.06 / 1.18 = 8.52 → 8 % = 0.6816 → 0.68 ; 10.30 / 1.18 = 8.73 → 0.6984 → 0.70
        assertEquals(d("0.68"), CommissionPolicy.breakdown(d("10.06"), d("0.08")).commission());
        assertEquals(d("0.70"), CommissionPolicy.breakdown(d("10.30"), d("0.08")).commission());
        // 8.25 % de 1 000.00 (valor de venta de 1 180.00) = 82.50 e IGV 14.85
        AwardedPriceBreakdown c = CommissionPolicy.breakdown(d("1180"), d("0.0825"));
        assertEquals(d("82.50"), c.commission());
        assertEquals(d("14.85"), c.commissionVat());
    }

    @Test
    void conTasaCeroElTransportistaCobraTodo() {
        AwardedPriceBreakdown b = CommissionPolicy.breakdown(d("2500"), BigDecimal.ZERO);
        assertEquals(d("0.00"), b.commission());
        assertEquals(d("0.00"), b.commissionVat());
        assertEquals(d("2500.00"), b.carrierNet());
    }

    @Test
    void rechazaMontosOTasasInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> CommissionPolicy.breakdown(d("-1"), d("0.08")));
        assertThrows(IllegalArgumentException.class, () -> CommissionPolicy.breakdown(d("100"), d("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> CommissionPolicy.breakdown(d("100"), d("1.01")));
    }
}
