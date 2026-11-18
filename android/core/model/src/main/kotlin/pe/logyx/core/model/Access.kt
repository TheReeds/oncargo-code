package pe.logyx.core.model

/**
 * Qué puede hacer el usuario según `UserInfo.permissions` (V14, ADR-0009). El backend es quien manda (403 `AUTH-007/008`);
 * esto solo sirve para mostrar u ocultar acciones. Las sesiones guardadas antes de V14 no traen `permissions`:
 * en ese caso se derivan del rol con la misma tabla que usa el backend.
 *
 * El conductor (`Role.DRIVER`) tiene solo `READ`, pero ejecuta sus paradas por el grupo `DRIVER`: no uses
 * [canOperate] para las pantallas del conductor.
 */
fun UserInfo.has(permission: OrgPermission): Boolean =
    if (permissions.isNotEmpty()) permission in permissions else permission in defaultPermissions(role, organizationType)

/** Publicar, ofertar, aceptar, contraofertar, chatear, asignar, cancelar, flota, viajes, reservas, incidencias, documentos y reseñas. */
val UserInfo.canOperate: Boolean get() = has(OrgPermission.OPERATE)

/** Invitar usuarios, cambiar roles, desactivar (solo ADMIN). */
val UserInfo.canManageUsers: Boolean get() = has(OrgPermission.MANAGE_USERS)

/** Editar el perfil de la organización (solo ADMIN). */
val UserInfo.canManageOrganization: Boolean get() = has(OrgPermission.MANAGE_ORGANIZATION)

/** Altas, bajas y contraseñas de conductores (solo ADMIN de un transportista). */
val UserInfo.canManageDrivers: Boolean get() = has(OrgPermission.MANAGE_DRIVERS)

/**
 * Rol Lector: solo consulta; cualquier POST/PUT/PATCH/DELETE da 403 `LOGYX-AUTH-007`. El conductor no cuenta como lector
 * (sus acciones van por otro grupo). Las pantallas lo usan para deshabilitar las acciones de escritura.
 */
val UserInfo.isReadOnly: Boolean
    get() = role != Role.DRIVER && !canOperate

/** Mismo cálculo que `OrgAccessPolicy` del backend, para sesiones sin `permissions`. */
internal fun defaultPermissions(role: Role, orgType: OrgType): Set<OrgPermission> = when (role) {
    Role.ADMIN -> buildSet {
        add(OrgPermission.READ); add(OrgPermission.OPERATE); add(OrgPermission.MANAGE_USERS); add(OrgPermission.MANAGE_ORGANIZATION)
        if (orgType == OrgType.CARRIER) add(OrgPermission.MANAGE_DRIVERS)
    }
    Role.OPS -> setOf(OrgPermission.READ, OrgPermission.OPERATE)
    // VIEWER, DRIVER y un rol que este cliente no conoce: lo más restrictivo.
    else -> setOf(OrgPermission.READ)
}

/**
 * La organización (o el rol) no puede realizar la acción. Clasificación de los errores 403 de RN01 y V14;
 * `Throwable.accessBlock` (`:core:data`) la deduce del código. Las pantallas muestran el mensaje del backend y,
 * si falta, [defaultMessage].
 */
enum class AccessBlock(val code: String, val defaultMessage: String) {
    /** RN01: la organización aún no fue verificada por OnCargo (`PENDING`/`REJECTED`). */
    NOT_VERIFIED(
        "LOGYX-ORG-003",
        "Tu empresa aún no está verificada por OnCargo. Hasta que lo esté no puedes publicar, ofertar ni reservar. " +
            "Revisa el estado en la pestaña Cuenta.",
    ),

    /** RN01: la organización está suspendida. */
    SUSPENDED(
        "LOGYX-ORG-004",
        "Tu empresa está suspendida en OnCargo. Mientras dure no puedes publicar, ofertar, reservar ni aceptar ofertas. " +
            "Escríbenos para revisar tu caso.",
    ),

    /** Rol Lector (`AUTH-007`). */
    READ_ONLY("LOGYX-AUTH-007", "Tu rol Lector solo permite consultar. Pide a un administrador de tu empresa que haga este cambio."),

    /** Solo ADMIN (`AUTH-008`). */
    ADMIN_ONLY("LOGYX-AUTH-008", "Solo un administrador de tu empresa puede hacer esto."),
    ;

    /** Bloqueos de RN01: el texto del backend dice "LOGYX"/"RN01", así que la app usa el suyo. */
    val isOrganizationBlock: Boolean get() = this == NOT_VERIFIED || this == SUSPENDED

    companion object {
        fun fromCode(code: String?): AccessBlock? = entries.firstOrNull { it.code == code }
    }
}
