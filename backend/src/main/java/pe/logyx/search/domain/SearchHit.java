package pe.logyx.search.domain;

import java.util.UUID;

/**
 * Un resultado de búsqueda: lo mínimo para mostrarlo y abrirlo. {@code code} es el código legible (OC-7K2QX) o, en
 * contrapartes, nulo; {@code href} es una ruta de la web (nunca una URL externa).
 */
public record SearchHit(SearchHitType type, UUID id, String code, String title, String subtitle, String href) {}
