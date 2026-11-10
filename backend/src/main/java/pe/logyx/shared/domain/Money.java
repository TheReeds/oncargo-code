package pe.logyx.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formato monetario peruano para mensajes al usuario: {@code S/ 1,850.00}. */
public final class Money {

    private Money() {}

    /** Soles con separador de miles (coma) y 2 decimales (punto). */
    public static String soles(BigDecimal amount) {
        // DecimalFormat no es thread-safe: se crea uno por llamada.
        var f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return "S/ " + f.format(amount);
    }
}
