package pe.logyx.shipment.domain;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Qué se presenta al cerrar una parada (roadmap 2.5):
 * <ul>
 *   <li><b>Recojo:</b> peso medido ({@code weightKg}, opcional; obligatorio si la PYME lo pidió → LOGYX-TRK-010) y foto
 *       de la carga {@code PICKUP_PHOTO} si la PYME la pidió (LOGYX-TRK-009). La guía de remisión es opcional (foto o
 *       PDF como documento {@code REMISSION_GUIDE}).</li>
 *   <li><b>Entrega:</b> nombre y documento de quien recibe ({@code receivedByName}, {@code receivedByDoc}). El
 *       servidor los acepta opcionales (clientes anteriores); la app los pide. Documento: DNI de 8 dígitos o carné de
 *       extranjería / pasaporte de 9 a 12 letras o dígitos (LOGYX-TRK-011).</li>
 * </ul>
 * El peso va de 1 kg a 60 000 kg con 2 decimales; el nombre, de 2 a 120 caracteres.
 */
public final class StopCompletionPolicy {

    public static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("60000");
    private static final Pattern DNI = Pattern.compile("\\d{8}");
    private static final Pattern OTHER_DOC = Pattern.compile("[A-Z0-9]{9,12}");

    /** Datos ya limpios. */
    public record Data(BigDecimal loadedWeightKg, String receivedByName, String receivedByDoc) {
        public static final Data NONE = new Data(null, null, null);
    }

    private StopCompletionPolicy() {}

    /**
     * Valida y normaliza. Los datos que no corresponden al tipo de parada se ignoran (un peso en la entrega, un
     * receptor en el recojo).
     */
    public static Data validate(ShipmentStop.Type type, Shipment.PickupRequirements req, boolean hasPickupPhoto,
                                BigDecimal weightKg, String receivedByName, String receivedByDoc) {
        if (type == ShipmentStop.Type.PICKUP) {
            if (req.photo() && !hasPickupPhoto) {
                throw BusinessException.conflict("LOGYX-TRK-009",
                        "Sube la foto de la carga antes de cerrar el recojo: la PYME la pidió");
            }
            if (weightKg == null) {
                if (req.weight()) {
                    throw BusinessException.badRequest("LOGYX-TRK-010",
                            "Indica el peso medido en el recojo: la PYME lo pidió", "weightKg");
                }
                return Data.NONE;
            }
            if (weightKg.signum() <= 0 || weightKg.compareTo(MAX_WEIGHT_KG) > 0 || weightKg.scale() > 2) {
                throw BusinessException.badRequest("LOGYX-TRK-010",
                        "El peso medido debe estar entre 1 y 60 000 kg", "weightKg");
            }
            return new Data(weightKg.setScale(2, RoundingMode.UNNECESSARY), null, null);
        }
        String name = blankToNull(receivedByName);
        String doc = blankToNull(receivedByDoc);
        if (name != null && (name.length() < 2 || name.length() > 120)) {
            throw BusinessException.badRequest("LOGYX-TRK-011", "Indica el nombre de quien recibe", "receivedByName");
        }
        if (doc != null) {
            doc = doc.replaceAll("[\\s.-]", "").toUpperCase();
            if (!DNI.matcher(doc).matches() && !OTHER_DOC.matcher(doc).matches()) {
                throw BusinessException.badRequest("LOGYX-TRK-011",
                        "El documento debe ser un DNI de 8 dígitos o un carné de extranjería", "receivedByDoc");
            }
        }
        return new Data(null, name, doc);
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        String t = v.strip().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }
}
