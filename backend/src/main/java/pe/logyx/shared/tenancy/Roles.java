package pe.logyx.shared.tenancy;

/**
 * Grupos que viajan en el JWT. Un usuario de organización recibe el tipo de su
 * organización (PYME / CARRIER / OPERATOR); un conductor recibe solo DRIVER,
 * para que no pueda usar los endpoints administrativos de su transportista.
 */
public final class Roles {
    public static final String PYME = "PYME";
    public static final String CARRIER = "CARRIER";
    public static final String OPERATOR = "OPERATOR";
    public static final String DRIVER = "DRIVER";

    private Roles() {}
}
