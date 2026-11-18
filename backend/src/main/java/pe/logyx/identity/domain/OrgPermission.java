package pe.logyx.identity.domain;

/**
 * Qué puede hacer un usuario según su rol de organización (F03). Lo expone {@code UserInfo.permissions} para que
 * web y Android muestren u oculten acciones; el backend lo hace cumplir igual ({@link OrgAccessPolicy}).
 */
public enum OrgPermission {
    /** Consultar todo lo de su organización. */
    READ,
    /** Operar: publicar, ofertar, aceptar, contraofertar, chatear, asignar, cancelar, flota de vehículos, viajes… */
    OPERATE,
    /** Invitar usuarios, cambiar roles, desactivar. */
    MANAGE_USERS,
    /** Editar el perfil de la organización. */
    MANAGE_ORGANIZATION,
    /** Cuentas de conductores: alta, desactivar/reactivar, restablecer contraseña (solo transportistas). */
    MANAGE_DRIVERS
}
