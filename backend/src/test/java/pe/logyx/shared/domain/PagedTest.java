package pe.logyx.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PagedTest {

    @Test
    void porOmisionPagina0Tamano20() {
        var r = PageRequest.of(null, null);
        assertEquals(0, r.page());
        assertEquals(20, r.size());
    }

    @Test
    void tamanoMaximo100YPaginaNoNegativa() {
        assertEquals("size", assertThrows(BusinessException.class, () -> PageRequest.of(0, 101)).field());
        assertEquals("size", assertThrows(BusinessException.class, () -> PageRequest.of(0, 0)).field());
        assertEquals("page", assertThrows(BusinessException.class, () -> PageRequest.of(-1, 10)).field());
        assertDoesNotThrow(() -> PageRequest.of(0, 100));
    }

    @Test
    void recortaEnMemoriaConTotal() {
        List<Integer> all = IntStream.range(0, 45).boxed().toList();
        var p = Paged.slice(all, new PageRequest(2, 20));
        assertEquals(5, p.items().size());
        assertEquals(45, p.total());
        assertTrue(Paged.slice(all, new PageRequest(9, 20)).items().isEmpty());
    }
}
