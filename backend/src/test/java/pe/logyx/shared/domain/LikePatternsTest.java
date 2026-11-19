package pe.logyx.shared.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LikePatternsTest {

    @Test
    void escapaComodinesDelUsuario() {
        assertEquals("%100\\%%", LikePatterns.contains("100%"));
        assertEquals("%a\\_b%", LikePatterns.contains("A_B"));
        assertEquals("%c:\\\\x%", LikePatterns.contains("C:\\X"));
        assertEquals("oc-7k%", LikePatterns.prefix(" OC-7K "));
    }
}
