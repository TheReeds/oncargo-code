package pe.logyx.identity.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RucValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"20100070970", "20600012348", "10456789124"})
    void aceptaRucConDigitoVerificadorCorrecto(String ruc) {
        assertTrue(RucValidator.isValid(ruc));
    }

    @ParameterizedTest
    @ValueSource(strings = {"20100070971", "30100070970", "2010007097", "2010007097A", ""})
    void rechazaRucInvalido(String ruc) {
        assertFalse(RucValidator.isValid(ruc));
    }

    @Test
    void rechazaNulo() {
        assertFalse(RucValidator.isValid(null));
    }
}
