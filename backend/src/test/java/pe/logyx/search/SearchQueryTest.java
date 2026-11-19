package pe.logyx.search;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.City;
import pe.logyx.search.domain.SearchHitType;
import pe.logyx.search.domain.SearchQuery;
import pe.logyx.shared.domain.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueryTest {

    @Test
    void minimoDosCaracteres() {
        var e = assertThrows(BusinessException.class, () -> SearchQuery.parse("a", null));
        assertEquals("q", e.field());
        assertThrows(BusinessException.class, () -> SearchQuery.parse("  ", null));
        assertThrows(BusinessException.class, () -> SearchQuery.parse("x".repeat(81), null));
        assertEquals("ab", SearchQuery.parse(" ab ", null).text());
    }

    @Test
    void reconoceCodigoYRuta() {
        var q = SearchQuery.parse("oc 7k2qx", null);
        assertEquals("OC-7K2QX", q.code());
        var route = SearchQuery.parse("lima  juliaca", null);
        assertTrue(route.isRoute());
        assertTrue(route.cities().containsAll(java.util.Set.of(City.LIMA, City.JULIACA)));
        assertFalse(SearchQuery.parse("lima", null).isRoute());
        assertTrue(SearchQuery.parse("4500123", null).cities().isEmpty());
    }

    @Test
    void tiposPorOmisionTodosYInvalidoFalla() {
        assertEquals(3, SearchQuery.parse("lima", null).types().size());
        var q = SearchQuery.parse("lima", "request, shipment");
        assertTrue(q.wants(SearchHitType.REQUEST) && q.wants(SearchHitType.SHIPMENT) && !q.wants(SearchHitType.COUNTERPART));
        var e = assertThrows(BusinessException.class, () -> SearchQuery.parse("lima", "foo"));
        assertEquals("types", e.field());
    }

    @Test
    void comodinesDelUsuarioQuedanEscapados() {
        assertEquals("%100\\%%", SearchQuery.parse("100%", null).contains());
    }
}
