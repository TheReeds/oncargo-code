package pe.logyx.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyTest {

    @Test
    void formatoPeruanoConMilesYDosDecimales() {
        assertEquals("S/ 1,850.00", Money.soles(new BigDecimal("1850")));
        assertEquals("S/ 1,234,567.50", Money.soles(new BigDecimal("1234567.5")));
        assertEquals("S/ 99.99", Money.soles(new BigDecimal("99.99")));
        assertEquals("S/ 0.00", Money.soles(BigDecimal.ZERO));
    }

    @Test
    void redondeaAMitadHaciaArriba() {
        assertEquals("S/ 10.01", Money.soles(new BigDecimal("10.005")));
    }
}
