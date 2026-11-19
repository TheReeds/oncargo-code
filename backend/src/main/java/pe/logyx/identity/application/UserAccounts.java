package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.port.out.PasswordHasher;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cuentas de usuario que otros módulos necesitan crear o leer (p. ej. conductores de fleet). */
@ApplicationScoped
@Transactional
public class UserAccounts {

    @Inject ProfileRepository profiles;
    @Inject PasswordHasher passwords;
    @Inject SystemTx system;
    @Inject AccountSessions sessions;
    @Inject pe.logyx.shared.tenancy.TenantContext tenant;

    /**
     * Crea la cuenta de un conductor (rol DRIVER) en la organización indicada.
     * El conductor confirma el consentimiento en su primer ingreso a la app;
     * aquí lo registra el administrador del transportista que lo da de alta.
     */
    public Profile createDriverAccount(UUID organizationId, String fullName, String email, String password) {
        // Unicidad global del email: con RLS solo veríamos los usuarios de la propia organización.
        if (system.call(() -> profiles.emailTaken(email))) {
            throw BusinessException.conflict("LOGYX-AUTH-002", "El email ya está registrado", "email");
        }
        Profile p = Profile.create(organizationId, email, passwords.hash(password), fullName,
                Profile.Role.DRIVER, OffsetDateTime.now());
        profiles.insert(p);
        return p;
    }

    /**
     * Desactiva o reactiva la cuenta de un conductor de la organización actual (F31). Desactivar sube la versión de
     * sesión: pierde el acceso de inmediato (y sus refresh tokens). El ADMIN lo comprueba quien llama (fleet).
     */
    public Profile setDriverActive(UUID profileId, boolean active) {
        Profile p = driverProfile(profileId);
        if (active) p.reactivate(); else p.deactivate();
        profiles.update(p);
        sessions.revoked(p.id());
        return p;
    }

    /** El ADMIN del transportista define una contraseña nueva para su conductor; cierra sus sesiones. */
    public Profile resetDriverPassword(UUID profileId, String newPassword) {
        Profile p = driverProfile(profileId);
        p.changePassword(passwords.hash(newPassword), OffsetDateTime.now());
        profiles.update(p);
        sessions.revoked(p.id());
        return p;
    }

    private Profile driverProfile(UUID profileId) {
        return profiles.findById(profileId)
                .filter(p -> p.isDriver() && p.organizationId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Conductor"));
    }

    /** Perfiles visibles para el usuario actual (RLS: los de su organización). */
    public Map<UUID, Profile> byIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return profiles.findAllById(ids).stream().collect(Collectors.toMap(Profile::id, Function.identity()));
    }

    public Optional<Profile> byId(UUID id) {
        return profiles.findById(id);
    }

    public Optional<Profile> byEmail(String email) {
        return profiles.findByEmail(email);
    }

    /**
     * Personas que reciben los avisos de una organización (V16): activas, con su contraseña propia (sin invitaciones
     * pendientes) y sin conductores (no tienen centro de avisos). Lo usa {@code notification} dentro del despacho del
     * outbox (SystemTx): ahí ve los perfiles de cualquier organización; bajo RLS, solo los de la propia.
     */
    public java.util.List<Profile> noticeRecipients(UUID organizationId) {
        return profiles.listOrganizationUsers(organizationId).stream().filter(Profile::canLogIn).toList();
    }
}
