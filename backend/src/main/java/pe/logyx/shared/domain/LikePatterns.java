package pe.logyx.shared.domain;

/** Patrones LIKE seguros: el texto del usuario nunca aporta comodines. Usar con {@code escape '\'}. */
public final class LikePatterns {

    private LikePatterns() {}

    /** {@code %texto%} en minúsculas con {@code % _ \} escapados. */
    public static String contains(String text) {
        return "%" + escape(text) + "%";
    }

    /** {@code texto%} en minúsculas con {@code % _ \} escapados. */
    public static String prefix(String text) {
        return escape(text) + "%";
    }

    private static String escape(String text) {
        return text.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
