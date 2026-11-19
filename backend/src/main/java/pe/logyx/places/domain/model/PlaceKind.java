package pe.logyx.places.domain.model;

/** Para qué extremo sirve una dirección guardada: recojo, entrega o ambos. */
public enum PlaceKind {
    PICKUP, DELIVERY, BOTH;

    /** Una dirección {@code BOTH} sirve en cualquier extremo. */
    public boolean serves(PlaceKind wanted) {
        return this == BOTH || wanted == BOTH || this == wanted;
    }
}
