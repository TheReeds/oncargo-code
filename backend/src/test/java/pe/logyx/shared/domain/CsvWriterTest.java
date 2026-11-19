package pe.logyx.shared.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.api.CsvWriter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CsvWriterTest {

    private static String text(CsvWriter w) {
        return new String(w.bytes(), StandardCharsets.UTF_8);
    }

    @Test
    void empiezaConBomUsaPuntoYComaYCierraConCrLf() {
        String out = text(new CsvWriter().header("A", "B").row(Arrays.asList("x", null)));
        assertTrue(out.startsWith("\uFEFF"));
        assertEquals("\uFEFFA;B\r\nx;\r\n", out);
    }

    @Test
    void escapaSeparadorComillasYSaltos() {
        String out = text(new CsvWriter().row(Arrays.asList("a;b", "dice \"hola\"", "l1\nl2")));
        assertEquals("\uFEFF\"a;b\";\"dice \"\"hola\"\"\";\"l1\nl2\"\r\n", out);
    }

    @Test
    void neutralizaFormulas() {
        String out = text(new CsvWriter().row(Arrays.asList("=1+1", "+cmd", "-2", "@SUM(A1)", "\tx", "normal")));
        assertEquals("\uFEFF'=1+1;'+cmd;'-2;'@SUM(A1);'\tx;normal\r\n", out);
    }

    @Test
    void losNumerosNoSeTocan() {
        String out = text(new CsvWriter().row(Arrays.asList(CsvWriter.number(new BigDecimal("-12.50")),
                CsvWriter.number(3), CsvWriter.number(null))));
        assertEquals("\uFEFF-12.50;3;\r\n", out);
    }
}
