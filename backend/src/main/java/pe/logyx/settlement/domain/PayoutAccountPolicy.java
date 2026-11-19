package pe.logyx.settlement.domain;

import pe.logyx.settlement.domain.model.PayoutKind;
import pe.logyx.shared.domain.BusinessException;

/**
 * Datos de cobro (2.12): CCI de 20 dígitos para un banco; celular peruano de 9 dígitos (empieza en 9) para Yape o Plin.
 * Titular 3–150 caracteres; banco obligatorio en cuentas bancarias. Hasta {@link #MAX_ACTIVE} vigentes por empresa.
 */
public final class PayoutAccountPolicy {

    public static final int MAX_ACTIVE = 5;

    private PayoutAccountPolicy() {}

    /** Normaliza el número (sin espacios ni guiones) y lo valida. */
    public static String normalizeNumber(PayoutKind kind, String raw) {
        String n = raw == null ? "" : raw.replaceAll("[\\s-]", "");
        if (kind == PayoutKind.BANK) {
            if (!n.matches("\\d{20}")) {
                throw BusinessException.badRequest("LOGYX-STL-007", "El CCI tiene 20 dígitos", "number");
            }
        } else {
            if (n.startsWith("+51")) n = n.substring(3);
            if (!n.matches("9\\d{8}")) {
                throw BusinessException.badRequest("LOGYX-STL-007", "Indica el celular de 9 dígitos asociado", "number");
            }
        }
        return n;
    }

    public static String holder(String raw) {
        String h = raw == null ? "" : raw.trim();
        if (h.length() < 3 || h.length() > 150) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el titular (3 a 150 caracteres)", "holderName");
        }
        return h;
    }

    public static String bank(PayoutKind kind, String raw) {
        if (kind != PayoutKind.BANK) return null;
        String b = raw == null ? "" : raw.trim();
        if (b.length() < 2 || b.length() > 60) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el banco", "bankName");
        }
        return b;
    }

    public static void ensureRoom(long active) {
        if (active >= MAX_ACTIVE) {
            throw BusinessException.conflict("LOGYX-STL-008", "Puedes tener hasta " + MAX_ACTIVE + " datos de cobro vigentes");
        }
    }
}
