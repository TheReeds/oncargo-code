package pe.logyx.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R
import pe.logyx.core.model.OrgPermission
import pe.logyx.core.model.VerificationStatus

/**
 * Rol Lector (V14, B2): solo consulta. `:app` lo provee desde `UserInfo.isReadOnly`. Con `true`, los botones marcados con
 * `LogyxButton(requiresOperate = true)` **no se muestran** (roadmap v2 1.7: ocultar, no deshabilitar) y [LogyxScreen]
 * muestra el aviso "Modo lectura" con «Pedir acceso». El backend es quien manda (403 `LOGYX-AUTH-007`).
 */
val LocalReadOnly = staticCompositionLocalOf { false }

/**
 * Permisos efectivos del usuario (roadmap v2 1.7): `:app` los provee desde `UserInfo` (`permissionsOf(user)`). Fuente
 * única para mostrar u ocultar acciones con [Can] / [rememberCan]. Por omisión todos (previews, tests sin proveedor).
 */
val LocalPermissions = staticCompositionLocalOf<Set<OrgPermission>> { OrgPermission.entries.toSet() }

/**
 * «Pedir acceso» (roadmap v2 1.7/1.15): `:app` provee la acción que avisa a los ADMIN (`POST /organizations/me/access-requests`).
 * null = sin botón (previews, conductor).
 */
val LocalRequestAccess = staticCompositionLocalOf<((OrgPermission) -> Unit)?> { null }

/** true si el usuario tiene [permission] (por omisión `OPERATE`). */
@Composable
fun rememberCan(permission: OrgPermission = OrgPermission.OPERATE): Boolean = permission in LocalPermissions.current

/**
 * Patrón único para acciones de escritura en Android (espejo de `<Can>` de la web): muestra [content] solo si el usuario
 * tiene [permission]; si no, [fallback] (por omisión nada). La información nunca se oculta, solo las acciones.
 * ```kotlin
 * Can { LogyxFab(...) }                                   // FAB de ofertar, oculto al Lector
 * Can(OrgPermission.MANAGE_USERS, fallback = { ReadOnlyNotice() }) { ... }
 * ```
 */
@Composable
fun Can(
    permission: OrgPermission = OrgPermission.OPERATE,
    fallback: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (rememberCan(permission)) content() else fallback()
}

/**
 * Aviso que [LogyxScreen] pinta justo bajo la barra superior (p. ej. la verificación pendiente en el inicio).
 * `:app` lo provee solo en las pantallas de inicio; en el resto es null.
 */
val LocalScreenBanner = compositionLocalOf<(@Composable () -> Unit)?> { null }

/** Aviso del estado de verificación de la organización (RN01): PENDING (ámbar), REJECTED y SUSPENDED (rojo). */
@Composable
fun VerificationBanner(status: VerificationStatus?, note: String?, modifier: Modifier = Modifier) {
    val (kind, title, text) = when (status) {
        VerificationStatus.PENDING -> Triple(MessageKind.Warning, R.string.ds_verification_pending_title, R.string.ds_verification_pending_text)
        VerificationStatus.REJECTED -> Triple(MessageKind.Error, R.string.ds_verification_rejected_title, R.string.ds_verification_rejected_text)
        VerificationStatus.SUSPENDED -> Triple(MessageKind.Error, R.string.ds_verification_suspended_title, R.string.ds_verification_suspended_text)
        else -> return
    }
    val reason = note?.takeIf { it.isNotBlank() }
    AlertBanner(
        text = stringResource(text) + (reason?.let { " " + stringResource(R.string.ds_verification_note, it) } ?: ""),
        modifier = modifier,
        kind = kind,
        title = stringResource(title),
    )
}

/** El estado de verificación restringe publicar, ofertar y reservar (se debe mostrar [VerificationBanner]). */
val VerificationStatus?.needsAttention: Boolean
    get() = this == VerificationStatus.PENDING || this == VerificationStatus.REJECTED || this == VerificationStatus.SUSPENDED

/** Franja de "Modo lectura" para el rol Lector, con «Pedir acceso» si `:app` provee [LocalRequestAccess]. */
@Composable
fun ReadOnlyNotice(modifier: Modifier = Modifier, permission: OrgPermission = OrgPermission.OPERATE) {
    val request = LocalRequestAccess.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AlertBanner(stringResource(R.string.ds_read_only_notice), kind = MessageKind.Info)
        if (request != null) {
            LogyxButton(
                stringResource(R.string.ds_request_access), { request(permission) },
                variant = ButtonVariant.Ghost, size = ButtonSize.Medium, fullWidth = false,
            )
        }
    }
}

/** Provee [LocalReadOnly] (para pruebas y previews). */
@Composable
fun ProvideReadOnly(readOnly: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReadOnly provides readOnly, content = content)
}

@Preview(showBackground = true)
@Composable
private fun AccountStatusPreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp)) {
            VerificationBanner(VerificationStatus.PENDING, null)
            VerificationBanner(VerificationStatus.REJECTED, "El RUC no coincide con la razón social.", Modifier.padding(top = 8.dp))
            VerificationBanner(VerificationStatus.SUSPENDED, "Reclamos sin atender.", Modifier.padding(top = 8.dp))
            ReadOnlyNotice(Modifier.padding(top = 8.dp))
        }
    }
}
