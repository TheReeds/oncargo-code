package pe.logyx.negotiation.domain;

import pe.logyx.shared.domain.BusinessException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * RN07 / RF29: el chat no admite datos de contacto, para que la negociación no se salga de la plataforma.
 * Se bloquean:
 * <ul>
 *   <li>Teléfonos: 9 dígitos seguidos, admitiendo un espacio, punto o guion entre dígitos ("987 654 321",
 *       "987-654-321") y el prefijo +51 / 51. Secuencias de más dígitos (fechas, códigos largos) no cuentan.</li>
 *   <li>Emails ({@code nombre@dominio.pe}).</li>
 *   <li>URLs ({@code http(s)://}, {@code www.}) y dominios sueltos con TLD común ({@code transportes.com.pe},
 *       {@code wa.me}, {@code bit.ly}).</li>
 *   <li>Usuarios de redes ({@code @usuario}).</li>
 * </ul>
 */
public final class ContactInfoPolicy {

    private static final String SEP = "[\\s.\\-]?";
    private static final List<Pattern> PATTERNS = List.of(
            // teléfono: (+51) + 9 dígitos, sin más dígitos pegados antes o después
            Pattern.compile("(?<!\\d" + SEP + ")(?:\\+?51" + SEP + ")?(?:\\d" + SEP + "){8}\\d(?!" + SEP + "\\d)"),
            // email
            Pattern.compile("[\\w.+\\-]+@[\\w\\-]+(?:\\.[\\w\\-]+)+"),
            // URL explícita
            Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+"),
            // dominio suelto
            Pattern.compile("(?i)\\b[a-z0-9][a-z0-9\\-]*\\.(?:com|net|org|pe|io|app|me|info|biz|co|xyz|ly|link|site|online)"
                    + "(?:\\.[a-z]{2})?\\b"),
            // usuario de red social
            Pattern.compile("(?<![\\w@])@[A-Za-z][A-Za-z0-9_.]{2,}"));

    private ContactInfoPolicy() {}

    public static boolean containsContactInfo(String text) {
        if (text == null || text.isBlank()) return false;
        return PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    /** Lanza LOGYX-NEG-001 (400) con {@code field} si el texto trae datos de contacto. */
    public static void ensureNoContactInfo(String text, String field) {
        if (containsContactInfo(text)) {
            throw BusinessException.badRequest("LOGYX-NEG-001", "El mensaje contiene datos de contacto (teléfono, "
                    + "email, enlace o usuario de red social). Coordina todo por el chat de LOGYX (RN07)", field);
        }
    }
}
