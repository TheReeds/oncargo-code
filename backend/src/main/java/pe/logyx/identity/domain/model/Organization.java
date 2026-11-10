package pe.logyx.identity.domain.model;

import pe.logyx.identity.domain.RucValidator;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Empresa usuaria de la plataforma (PYME, transportista u operador LOGYX). */
public final class Organization {

    public enum RucVerification { NONE, CHECKSUM, SUNAT }

    private final UUID id;
    private final OrgType type;
    private final String legalName;
    private final String ruc;
    private final RucVerification rucVerification;
    private final String city;
    private final String phone;
    private final OffsetDateTime createdAt;

    public Organization(UUID id, OrgType type, String legalName, String ruc, RucVerification rucVerification,
                        String city, String phone, OffsetDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.legalName = legalName;
        this.ruc = ruc;
        this.rucVerification = rucVerification;
        this.city = city;
        this.phone = phone;
        this.createdAt = createdAt;
    }

    /**
     * Alta por autoregistro. Las cuentas de operador las crea LOGYX; el RUC se
     * verifica localmente por dígito verificador (RN01, nivel CHECKSUM).
     */
    public static Organization register(OrgType type, String legalName, String ruc, String city, String phone) {
        if (type == OrgType.OPERATOR) {
            throw BusinessException.forbidden("Las cuentas de operador las crea LOGYX");
        }
        if (!RucValidator.isValid(ruc)) {
            throw BusinessException.badRequest("LOGYX-ORG-001", "El RUC no es válido (dígito verificador)", "ruc");
        }
        return new Organization(UUID.randomUUID(), type, legalName.trim(), ruc, RucVerification.CHECKSUM,
                city, phone, OffsetDateTime.now());
    }

    /**
     * Organización operadora de LOGYX. No hay autoregistro de operadores: la crea la plataforma
     * (seed de desarrollo o un proceso interno), nunca un endpoint público.
     */
    public static Organization provisionOperator(String legalName, String ruc, String city) {
        if (!RucValidator.isValid(ruc)) {
            throw BusinessException.badRequest("LOGYX-ORG-001", "El RUC no es válido (dígito verificador)", "ruc");
        }
        return new Organization(UUID.randomUUID(), OrgType.OPERATOR, legalName.trim(), ruc, RucVerification.CHECKSUM,
                city, null, OffsetDateTime.now());
    }

    public boolean rucVerified() {
        return rucVerification != RucVerification.NONE;
    }

    public UUID id() { return id; }
    public OrgType type() { return type; }
    public String legalName() { return legalName; }
    public String ruc() { return ruc; }
    public RucVerification rucVerification() { return rucVerification; }
    public String city() { return city; }
    public String phone() { return phone; }
    public OffsetDateTime createdAt() { return createdAt; }
}
