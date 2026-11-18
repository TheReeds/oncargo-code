package pe.logyx.identity.domain.model;

import pe.logyx.identity.domain.RucValidator;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;
import pe.logyx.shared.tenancy.OrgType;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Empresa usuaria de la plataforma (PYME, transportista u operador LOGYX). */
public final class Organization {

    public enum RucVerification { NONE, CHECKSUM, SUNAT }

    /** Estado de la verificación de la organización por el operador LOGYX (RN01, RF08). */
    public enum VerificationStatus { PENDING, VERIFIED, REJECTED, SUSPENDED }

    /** Decisión del operador sobre la verificación (quién, cuándo y por qué). */
    public record Verification(VerificationStatus status, String note, OffsetDateTime updatedAt, UUID updatedBy) {
        public static Verification pending() { return new Verification(VerificationStatus.PENDING, null, null, null); }
    }

    private final UUID id;
    private final OrgType type;
    private String legalName;
    private String ruc;
    private final RucVerification rucVerification;
    private Verification verification;
    private String city;
    private String phone;
    private String address;
    private final OffsetDateTime createdAt;

    public Organization(UUID id, OrgType type, String legalName, String ruc, RucVerification rucVerification,
                        Verification verification, String city, String phone, OffsetDateTime createdAt) {
        this(id, type, legalName, ruc, rucVerification, verification, city, phone, null, createdAt);
    }

    public Organization(UUID id, OrgType type, String legalName, String ruc, RucVerification rucVerification,
                        Verification verification, String city, String phone, String address,
                        OffsetDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.legalName = legalName;
        this.ruc = ruc;
        this.rucVerification = rucVerification;
        this.verification = verification == null ? Verification.pending() : verification;
        this.city = city;
        this.phone = phone;
        this.address = address;
        this.createdAt = createdAt;
    }

    /**
     * Alta por autoregistro. Las cuentas de operador las crea LOGYX. El RUC se valida localmente por dígito
     * verificador (nivel CHECKSUM), lo que no la hace "verificada": queda PENDING hasta que el operador la revise
     * (RN01, RF08; la consulta a SUNAT queda pendiente y sería otra forma de llegar a VERIFIED).
     */
    public static Organization register(OrgType type, String legalName, String ruc, String city, String phone) {
        if (type == OrgType.OPERATOR) {
            throw BusinessException.forbidden("Las cuentas de operador las crea LOGYX");
        }
        if (!RucValidator.isValid(ruc)) {
            throw BusinessException.badRequest("LOGYX-ORG-001", "El RUC no es válido (dígito verificador)", "ruc");
        }
        String name = ContactInfoPolicy.clean(legalName, "legalName");                                       // RN07
        return new Organization(UUID.randomUUID(), type, name, ruc, RucVerification.CHECKSUM,
                Verification.pending(), city, phone, OffsetDateTime.now());
    }

    /**
     * Organización operadora de LOGYX. No hay autoregistro de operadores: la crea la plataforma
     * (seed de desarrollo o un proceso interno), nunca un endpoint público.
     */
    public static Organization provisionOperator(String legalName, String ruc, String city) {
        if (!RucValidator.isValid(ruc)) {
            throw BusinessException.badRequest("LOGYX-ORG-001", "El RUC no es válido (dígito verificador)", "ruc");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return new Organization(UUID.randomUUID(), OrgType.OPERATOR, legalName.trim(), ruc, RucVerification.CHECKSUM,
                new Verification(VerificationStatus.VERIFIED, "Cuenta interna de LOGYX", now, null), city, null, now);
    }

    /** El RUC pasó el dígito verificador (módulo 11) o SUNAT. No implica que la organización esté verificada. */
    public boolean rucValid() {
        return rucVerification != RucVerification.NONE;
    }

    /** Sello "Verificado" (RF08): el operador aprobó la organización. Es lo que exige RN01. */
    public boolean rucVerified() {
        return verification.status() == VerificationStatus.VERIFIED;
    }

    public boolean isSuspended() {
        return verification.status() == VerificationStatus.SUSPENDED;
    }

    /**
     * RN01: la organización puede asumir compromisos nuevos (publicar, ofertar, reservar, publicar viajes).
     * Suspendida: nunca (LOGYX-ORG-004). Sin verificar: solo si la verificación no es obligatoria en este entorno
     * ({@code logyx.verification.required=false}, p. ej. dev); si lo es, LOGYX-ORG-003.
     */
    public void ensureCanTransact(boolean verificationRequired) {
        if (isSuspended()) {
            throw new BusinessException(403, "LOGYX-ORG-004",
                    "Tu organización está suspendida por LOGYX" + reasonSuffix() + ". Escríbenos para revisarlo");
        }
        if (verificationRequired && !rucVerified()) {
            String state = verification.status() == VerificationStatus.REJECTED ? "fue rechazada" + reasonSuffix()
                    : "está pendiente de verificación por LOGYX";
            throw new BusinessException(403, "LOGYX-ORG-003", "Tu organización " + state
                    + ": hasta que esté verificada no puedes publicar ni ofertar (RN01)");
        }
    }

    /** El operador aprueba la organización (sello "Verificado"). */
    public void verify(UUID operatorProfileId, String note, OffsetDateTime now) {
        decide(VerificationStatus.VERIFIED, note, operatorProfileId, now);
    }

    /** El operador rechaza la verificación (motivo obligatorio). La organización puede corregir y volver a pedirla. */
    public void reject(UUID operatorProfileId, String reason, OffsetDateTime now) {
        decide(VerificationStatus.REJECTED, requireReason(reason), operatorProfileId, now);
    }

    /** El operador suspende la organización (motivo obligatorio): no asume compromisos nuevos. */
    public void suspend(UUID operatorProfileId, String reason, OffsetDateTime now) {
        decide(VerificationStatus.SUSPENDED, requireReason(reason), operatorProfileId, now);
    }

    private void decide(VerificationStatus target, String note, UUID by, OffsetDateTime now) {
        if (type == OrgType.OPERATOR) {
            throw BusinessException.conflict("LOGYX-ORG-005", "Las organizaciones operadoras no se verifican ni suspenden");
        }
        verification = new Verification(target, note == null || note.isBlank() ? null : note.trim(), now, by);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.badRequest("LOGYX-ORG-006", "Indica el motivo", "reason");
        }
        return reason;
    }

    // ------------------------------------------------------------ Perfil editable (F20)

    /** La razón social y el RUC solo se corrigen mientras el operador no la verificó (PENDING) o si la rechazó. */
    public boolean legalDataEditable() {
        return verification.status() == VerificationStatus.PENDING
                || verification.status() == VerificationStatus.REJECTED;
    }

    /** Datos de contacto no sensibles: siempre editables por el ADMIN. Vacío = se borra. */
    public void updateContact(String newCity, String newPhone, String newAddress) {
        if (newCity != null) city = blankToNull(newCity);
        if (newPhone != null) phone = blankToNull(newPhone);
        if (newAddress != null) address = blankToNull(newAddress);
    }

    /**
     * Corrige razón social y/o RUC ({@code null} = sin cambio). Con {@code VERIFIED}/{@code SUSPENDED} no se puede
     * (LOGYX-ORG-007). Si estaba {@code REJECTED}, vuelve a {@code PENDING} para que el operador la revise de nuevo:
     * devuelve {@code true} en ese caso. La unicidad del RUC la verifica el caso de uso.
     */
    public boolean changeLegalData(String newLegalName, String newRuc, OffsetDateTime now) {
        String name = newLegalName == null ? null : ContactInfoPolicy.clean(newLegalName, "legalName");  // RN07
        boolean nameChanged = name != null && !name.equals(legalName);
        boolean rucChanged = newRuc != null && !newRuc.trim().equals(ruc);
        if (!nameChanged && !rucChanged) return false;
        if (!legalDataEditable()) {
            throw BusinessException.conflict("LOGYX-ORG-007", "Tu organización ya fue verificada: para cambiar la "
                    + "razón social o el RUC contacta al equipo de OnCargo", nameChanged ? "legalName" : "ruc");
        }
        if (rucChanged && !RucValidator.isValid(newRuc.trim())) {
            throw BusinessException.badRequest("LOGYX-ORG-001", "El RUC no es válido (dígito verificador)", "ruc");
        }
        if (nameChanged) legalName = name;
        if (rucChanged) ruc = newRuc.trim();
        if (verification.status() == VerificationStatus.REJECTED) {
            verification = new Verification(VerificationStatus.PENDING, null, now, null);
            return true;
        }
        return false;
    }

    private static String blankToNull(String v) {
        return v.isBlank() ? null : v.trim();
    }

    private String reasonSuffix() {
        return verification.note() == null ? "" : " (" + verification.note() + ")";
    }

    public UUID id() { return id; }
    public OrgType type() { return type; }
    public String legalName() { return legalName; }
    public String ruc() { return ruc; }
    public RucVerification rucVerification() { return rucVerification; }
    public Verification verification() { return verification; }
    public VerificationStatus verificationStatus() { return verification.status(); }
    public String city() { return city; }
    public String phone() { return phone; }
    public String address() { return address; }
    public OffsetDateTime createdAt() { return createdAt; }
}
