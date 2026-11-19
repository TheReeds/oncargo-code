package pe.logyx.shared.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Escritor CSV para Excel en es-PE: UTF-8 con BOM, separador {@code ;}, saltos {@code \r\n}. Las celdas de texto que
 * empiezan por {@code = + - @} (o tabulador/retorno) llevan un apóstrofo delante para neutralizar la inyección de
 * fórmulas; los números se escriben con {@link #number}, que no se ve afectado (un negativo no es una fórmula).
 * Solo debe recibir filas ya leídas por las mismas consultas (y la RLS) que la vista JSON.
 */
public final class CsvWriter {

    public static final String MEDIA_TYPE = "text/csv;charset=UTF-8";
    private static final String BOM = "﻿";
    private static final char SEP = ';';

    private final StringBuilder out = new StringBuilder(BOM);

    public CsvWriter header(String... columns) {
        return row(List.of(columns));
    }

    public CsvWriter row(List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) out.append(SEP);
            out.append(cell(cells.get(i)));
        }
        out.append("\r\n");
        return this;
    }

    /** Un número tal cual (coma decimal de es-PE no: Excel acepta el punto en archivos UTF-8 con separador {@code ;}). */
    public static Object number(Number n) {
        return n == null ? null : new Raw(n instanceof java.math.BigDecimal b ? b.toPlainString() : n.toString());
    }

    private record Raw(String text) {}

    static String cell(Object value) {
        if (value == null) return "";
        if (value instanceof Raw r) return r.text();
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        boolean quote = text.indexOf(SEP) >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        return quote ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    public byte[] bytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
