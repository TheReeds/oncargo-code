package pe.logyx.marketplace.domain;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Código legible de la solicitud (y del envío que hereda): {@code OC-} + 5 caracteres de un alfabeto sin los que se
 * confunden al dictarlos o leerlos (0, 1, I, L, O, U): {@code OC-7K2QX}. 30^5 ≈ 24 millones de códigos.
 * Es el mismo alfabeto del respaldo SQL de V17.
 */
public final class RequestCode {

    public static final String PREFIX = "OC-";
    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int LENGTH = 5;
    private static final Pattern VALID = Pattern.compile("OC-[" + ALPHABET + "]{5}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private RequestCode() {}

    public static String random() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    public static boolean isValid(String code) {
        return code != null && VALID.matcher(code).matches();
    }

    /**
     * Lo que se escribe a mano ("oc 7k2qx", "7K2QX", "OC-7K2QX") → {@code OC-7K2QX}; {@code null} si no puede ser un
     * código (para que la búsqueda por texto siga su camino).
     */
    public static String parse(String text) {
        if (text == null) return null;
        String t = text.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s_]", "");
        if (t.startsWith("OC")) t = t.substring(2).replaceFirst("^-", "");
        String candidate = PREFIX + t;
        return isValid(candidate) ? candidate : null;
    }
}
