package pe.logyx.fleet.domain.model;

/**
 * Documentos de flota con vencimiento (2.4). {@code requiresExpiry}: sin fecha de vencimiento no se acepta la subida.
 * {@code seal}: de ahí salen los sellos «SOAT vigente» y «Revisión técnica vigente» que ve la PYME en la oferta.
 */
public enum FleetDocumentType {
    SOAT(Owner.VEHICLE, true, true, "SOAT"),
    TECHNICAL_INSPECTION(Owner.VEHICLE, true, true, "Revisión técnica"),
    PROPERTY_CARD(Owner.VEHICLE, false, false, "Tarjeta de propiedad"),
    INSURANCE_POLICY(Owner.VEHICLE, true, false, "Póliza de seguro"),
    MTC_PERMIT(Owner.VEHICLE, true, false, "Permiso MTC"),
    DRIVER_LICENSE(Owner.DRIVER, true, false, "Licencia de conducir"),
    DNI(Owner.DRIVER, false, false, "DNI");

    public enum Owner { VEHICLE, DRIVER }

    private final Owner owner;
    private final boolean requiresExpiry;
    private final boolean seal;
    private final String label;

    FleetDocumentType(Owner owner, boolean requiresExpiry, boolean seal, String label) {
        this.owner = owner;
        this.requiresExpiry = requiresExpiry;
        this.seal = seal;
        this.label = label;
    }

    public Owner owner() { return owner; }
    public boolean requiresExpiry() { return requiresExpiry; }
    public boolean isSeal() { return seal; }
    public String label() { return label; }
}
