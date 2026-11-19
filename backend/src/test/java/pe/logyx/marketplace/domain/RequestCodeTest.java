package pe.logyx.marketplace.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RequestCodeTest {

    @Test
    void generaCodigosValidosSinCaracteresAmbiguos() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String code = RequestCode.random();
            assertTrue(RequestCode.isValid(code), code);
            assertEquals(8, code.length());
            assertFalse(code.substring(3).matches(".*[01ILOU].*"), "caracteres ambiguos en " + code);
            seen.add(code);
        }
        assertTrue(seen.size() > 1990, "demasiadas repeticiones: " + seen.size());
    }

    @Test
    void interpretaLoQueSeEscribeADictado() {
        assertEquals("OC-7K2QX", RequestCode.parse("oc-7k2qx"));
        assertEquals("OC-7K2QX", RequestCode.parse("OC 7K2QX"));
        assertEquals("OC-7K2QX", RequestCode.parse("  7k2qx "));
        assertEquals("OC-7K2QX", RequestCode.parse("OC7K2QX"));
    }

    @Test
    void loQueNoEsUnCodigoDaNull() {
        assertNull(RequestCode.parse("lima"));
        assertNull(RequestCode.parse("OC-7K2"));
        assertNull(RequestCode.parse("OC-7K2QO"));   // la O no existe en el alfabeto
        assertNull(RequestCode.parse("4500123"));
        assertNull(RequestCode.parse(null));
    }
}
