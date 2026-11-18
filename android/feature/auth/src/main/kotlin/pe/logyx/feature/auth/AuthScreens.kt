package pe.logyx.feature.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pe.logyx.core.designsystem.LogyxPalette
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.ChipRow
import pe.logyx.core.designsystem.components.LogyxBadge
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.VerificationBanner
import pe.logyx.core.designsystem.components.needsAttention
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.InitialsAvatar
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxFilterChip
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.MessageKind
import pe.logyx.core.designsystem.components.PillTone
import pe.logyx.core.designsystem.components.TruckIllustration
import pe.logyx.core.designsystem.components.VerifiedBadge
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.Role
import pe.logyx.core.model.UserInfo
import pe.logyx.core.model.VerificationStatus
import pe.logyx.core.model.VehicleType

/** Cuenta de prueba mostrada solo en builds de desarrollo. */
data class DemoAccount(val label: String, val email: String, val password: String)

@Composable
fun LoginRoute(
    onOpenRegister: () -> Unit,
    demoAccounts: List<DemoAccount> = emptyList(),
    onOpenCatalog: (() -> Unit)? = null,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showForgot by rememberSaveable { mutableStateOf(false) }
    if (showForgot) ForgotPasswordSheet(prefillEmail = state.email, onDismiss = { showForgot = false })
    LoginScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onOpenRegister = onOpenRegister,
        demoAccounts = demoAccounts,
        onDemo = { viewModel.fill(it.email, it.password) },
        onOpenCatalog = onOpenCatalog,
        onForgotPassword = { showForgot = true },
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenRegister: () -> Unit,
    demoAccounts: List<DemoAccount> = emptyList(),
    onDemo: (DemoAccount) -> Unit = {},
    onOpenCatalog: (() -> Unit)? = null,
    onForgotPassword: () -> Unit = {},
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    // La cabecera es siempre oscura: iconos de la barra de estado claros mientras se vea el login.
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    DisposableEffect(view, darkTheme) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        // Cabecera oscura de marca con camión
        Box(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(LogyxPalette.Ink900, LogyxPalette.Petrol700)))
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // Logotipo para fondo oscuro: la cabecera es siempre oscura, también en tema claro.
                    Image(
                        painter = painterResource(pe.logyx.core.designsystem.R.drawable.oncargo_logo_on_dark),
                        contentDescription = stringResource(R.string.auth_brand),
                        modifier = Modifier.height(36.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.auth_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Box(Modifier.clearAndSetSemantics { }) {
                    TruckIllustration(VehicleType.TRUCK_MEDIUM, Modifier.size(width = 130.dp, height = 74.dp))
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.auth_login_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.auth_login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.error != null) {
                AlertBanner(
                    text = state.error,
                    kind = MessageKind.Error,
                    title = stringResource(R.string.auth_login_error_title),
                )
            }
            LogyxTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = stringResource(R.string.auth_email),
                placeholder = stringResource(R.string.auth_email_placeholder),
                error = state.emailError?.let { stringResource(R.string.auth_email_error) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            LogyxTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.auth_password),
                error = state.passwordError?.let { stringResource(R.string.auth_password_error) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(
                                if (showPassword) R.string.auth_hide_password else R.string.auth_show_password,
                            ),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
            LogyxButton(
                text = stringResource(R.string.auth_login_action),
                onClick = onSubmit,
                loading = state.loading,
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LogyxButton(
                    text = stringResource(R.string.auth_forgot_action),
                    onClick = onForgotPassword,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Medium,
                    fullWidth = false,
                )
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.auth_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LogyxButton(
                    text = stringResource(R.string.auth_register_action),
                    onClick = onOpenRegister,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Medium,
                    fullWidth = false,
                )
            }
            if (demoAccounts.isNotEmpty() || onOpenCatalog != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (demoAccounts.isNotEmpty()) {
                Text(
                    stringResource(R.string.auth_demo_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipRow {
                    demoAccounts.forEach { LogyxFilterChip(label = it.label, selected = state.email == it.email, onClick = { onDemo(it) }) }
                }
            }
            if (onOpenCatalog != null) {
                LogyxButton(
                    text = stringResource(R.string.auth_open_catalog),
                    onClick = onOpenCatalog,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Medium,
                    fullWidth = false,
                )
            }
        }
    }
}

@Composable
private fun roleLabel(user: UserInfo) = stringResource(
    when {
        user.role == Role.DRIVER -> R.string.auth_role_driver
        user.organizationType == OrgType.CARRIER -> R.string.auth_role_carrier
        user.organizationType == OrgType.PYME -> R.string.auth_role_pyme
        else -> R.string.auth_role_operator
    },
)

@Composable
fun AccountRoute(apiUrl: String, versionName: String, viewModel: AccountViewModel = koinViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    AccountScreen(
        user, apiUrl, versionName,
        onLogout = viewModel::logout,
        onLogoutAll = viewModel::logoutAll,
        loggingOutAll = ui.loggingOutAll,
        message = ui.message,
        onMessageShown = viewModel::onMessageShown,
        onPasswordChanged = { viewModel.showMessage(UiMessage.success(R.string.auth_password_changed)) },
    )
}

@Composable
fun AccountScreen(
    user: UserInfo?,
    apiUrl: String,
    versionName: String,
    onLogout: () -> Unit,
    onLogoutAll: () -> Unit = {},
    loggingOutAll: Boolean = false,
    message: UiMessage? = null,
    onMessageShown: () -> Unit = {},
    onPasswordChanged: () -> Unit = {},
    passwordSheet: @Composable (onDismiss: () -> Unit) -> Unit = { dismiss -> ChangePasswordSheet(onDismiss = dismiss, onChanged = onPasswordChanged) },
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmLogoutAll by rememberSaveable { mutableStateOf(false) }
    var changePassword by rememberSaveable { mutableStateOf(false) }
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(message, snackbar, onMessageShown)
    LogyxScreen(stringResource(R.string.auth_account_title), snackbar = snackbar) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (user != null) {
                if (user.role != Role.DRIVER && user.verificationStatus.needsAttention) {
                    VerificationBanner(user.verificationStatus, user.verificationNote)
                }
                LogyxCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val avatarDescription = stringResource(R.string.auth_avatar_description, user.fullName)
                        InitialsAvatar(user.fullName, description = avatarDescription)
                        Spacer(Modifier.height(4.dp))
                        Text(user.fullName, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(
                            user.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            LogyxBadge(roleLabel(user), tone = PillTone.Petrol)
                            if (user.rucVerified) VerifiedBadge()
                        }
                    }
                }
                LogyxCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle(stringResource(R.string.auth_section_data))
                        InfoRow(stringResource(R.string.auth_data_name), user.fullName)
                        InfoRow(stringResource(R.string.auth_data_email), user.email)
                        InfoRow(stringResource(R.string.auth_data_org), user.organizationName ?: "—")
                        InfoRow(stringResource(R.string.auth_data_role), roleLabel(user))
                        if (user.role != Role.DRIVER) {
                            InfoRow(stringResource(R.string.auth_data_verification), verificationLabel(user))
                        }
                    }
                }
            } else {
                AlertBanner(stringResource(R.string.auth_no_session), kind = MessageKind.Warning)
            }
            LogyxCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(stringResource(R.string.auth_section_app))
                    InfoRow(stringResource(R.string.auth_version), versionName)
                    InfoRow(stringResource(R.string.auth_api), apiUrl)
                }
            }
            if (user != null) {
                LogyxCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(stringResource(R.string.auth_section_security))
                        // Cambiar la contraseña y cerrar sesiones es autoservicio: también lo pueden hacer el rol Lector y el conductor.
                        LogyxButton(
                            text = stringResource(R.string.auth_change_password_action),
                            onClick = { changePassword = true },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Medium,
                            icon = Icons.Rounded.Lock,
                        )
                        LogyxButton(
                            text = stringResource(R.string.auth_logout_all_action),
                            onClick = { confirmLogoutAll = true },
                            variant = ButtonVariant.Ghost,
                            size = ButtonSize.Medium,
                            loading = loggingOutAll,
                        )
                    }
                }
            }
            LogyxButton(
                text = stringResource(R.string.auth_logout_action),
                onClick = { confirmLogout = true },
                variant = ButtonVariant.Secondary,
                icon = Icons.AutoMirrored.Rounded.Logout,
            )
        }
    }
    if (changePassword) passwordSheet { changePassword = false }
    if (confirmLogout) {
        LogyxConfirmDialog(
            title = stringResource(R.string.auth_logout_title),
            message = stringResource(R.string.auth_logout_message),
            confirmLabel = stringResource(R.string.auth_logout_action),
            onConfirm = { confirmLogout = false; onLogout() },
            onDismiss = { confirmLogout = false },
            destructive = true,
            icon = Icons.AutoMirrored.Rounded.Logout,
        )
    }
    if (confirmLogoutAll) {
        LogyxConfirmDialog(
            title = stringResource(R.string.auth_logout_all_title),
            message = stringResource(R.string.auth_logout_all_message),
            confirmLabel = stringResource(R.string.auth_logout_all_confirm),
            onConfirm = { confirmLogoutAll = false; onLogoutAll() },
            onDismiss = { confirmLogoutAll = false },
            destructive = true,
            icon = Icons.AutoMirrored.Rounded.Logout,
        )
    }
}

@Composable
private fun verificationLabel(user: UserInfo) = stringResource(
    when (user.verificationStatus) {
        VerificationStatus.VERIFIED -> R.string.auth_verification_verified
        VerificationStatus.PENDING -> R.string.auth_verification_pending
        VerificationStatus.REJECTED -> R.string.auth_verification_rejected
        VerificationStatus.SUSPENDED -> R.string.auth_verification_suspended
        else -> if (user.rucVerified) R.string.auth_verification_verified else R.string.auth_verification_unknown
    },
)

/** Cambiar contraseña en una hoja: errores bajo cada campo (`AUTH-006` actual incorrecta, `AUTH-010` igual a la actual). */
@Composable
fun ChangePasswordSheet(onDismiss: () -> Unit, onChanged: () -> Unit, viewModel: ChangePasswordViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) { viewModel.onDoneShown(); onChanged(); onDismiss() }
    }
    ChangePasswordSheetContent(
        state, onDismiss, viewModel::onCurrentChange, viewModel::onNewChange, viewModel::onConfirmChange, viewModel::submit,
    )
}

@Composable
fun ChangePasswordSheetContent(
    state: ChangePasswordUiState,
    onDismiss: () -> Unit,
    onCurrentChange: (String) -> Unit,
    onNewChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var show by rememberSaveable { mutableStateOf(false) }
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = { show = !show }) {
            Icon(
                if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = stringResource(if (show) R.string.auth_hide_password else R.string.auth_show_password),
            )
        }
    }
    val transform = if (show) VisualTransformation.None else PasswordVisualTransformation()
    LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.auth_change_password_title)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.auth_change_password_intro),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { AlertBanner(it, kind = MessageKind.Error) }
            LogyxTextField(
                value = state.current, onValueChange = onCurrentChange, label = stringResource(R.string.auth_current_password),
                error = state.serverErrors["currentPassword"] ?: state.currentError?.let { passwordErrorText(it) },
                visualTransformation = transform, trailing = toggle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
            LogyxTextField(
                value = state.new, onValueChange = onNewChange, label = stringResource(R.string.auth_new_password),
                hint = stringResource(R.string.auth_new_password_hint),
                error = state.serverErrors["newPassword"] ?: state.newError?.let { passwordErrorText(it) },
                visualTransformation = transform,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
            LogyxTextField(
                value = state.confirm, onValueChange = onConfirmChange, label = stringResource(R.string.auth_confirm_password),
                error = state.confirmError?.let { passwordErrorText(it) },
                visualTransformation = transform,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
            Text(
                stringResource(R.string.auth_change_password_effect),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LogyxButton(stringResource(R.string.auth_change_password_save), onSubmit, loading = state.saving)
        }
    }
}

@Composable
private fun passwordErrorText(e: PasswordFormError) = stringResource(
    when (e) {
        PasswordFormError.CurrentEmpty -> R.string.auth_error_current_empty
        PasswordFormError.NewTooShort -> R.string.auth_error_new_short
        PasswordFormError.NewTooLong -> R.string.auth_error_new_long
        PasswordFormError.NewSameAsCurrent -> R.string.auth_error_new_same
        PasswordFormError.ConfirmMismatch -> R.string.auth_error_confirm
    },
)

/** "¿Olvidaste tu contraseña?": el mensaje de confirmación es el mismo exista o no el correo. */
@Composable
fun ForgotPasswordSheet(prefillEmail: String, onDismiss: () -> Unit, viewModel: ForgotPasswordViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(prefillEmail) { viewModel.prefill(prefillEmail) }
    ForgotPasswordSheetContent(state, onDismiss, viewModel::onEmailChange, viewModel::submit)
}

@Composable
fun ForgotPasswordSheetContent(state: ForgotPasswordUiState, onDismiss: () -> Unit, onEmailChange: (String) -> Unit, onSubmit: () -> Unit) {
    LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.auth_forgot_title)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.sent) {
                AlertBanner(
                    text = state.serverMessage ?: stringResource(R.string.auth_forgot_sent),
                    kind = MessageKind.Success,
                    title = stringResource(R.string.auth_forgot_sent_title),
                )
                Text(
                    stringResource(R.string.auth_forgot_sent_help),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LogyxButton(stringResource(R.string.auth_forgot_close), onDismiss, variant = ButtonVariant.Secondary)
            } else {
                Text(
                    stringResource(R.string.auth_forgot_intro),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.error?.let { AlertBanner(it, kind = MessageKind.Error) }
                LogyxTextField(
                    value = state.email, onValueChange = onEmailChange, label = stringResource(R.string.auth_email),
                    placeholder = stringResource(R.string.auth_email_placeholder),
                    error = if (state.emailInvalid) stringResource(R.string.auth_email_error) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                )
                LogyxButton(stringResource(R.string.auth_forgot_send), onSubmit, loading = state.sending)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun LoginScreenPreview() {
    LogyxTheme {
        LoginScreen(
            state = LoginUiState(email = "carrier@logyx.pe", passwordError = LoginFieldError.EmptyPassword, error = "Correo o contraseña incorrectos. Revísalos e inténtalo de nuevo."),
            onEmailChange = {}, onPasswordChange = {}, onSubmit = {}, onOpenRegister = {},
            demoAccounts = listOf(DemoAccount("Transportista", "carrier@logyx.pe", "x"), DemoAccount("PYME", "pyme@logyx.pe", "x")),
            onOpenCatalog = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun AccountScreenPreview() {
    LogyxTheme {
        AccountScreen(
            user = UserInfo("1", "carrier@logyx.pe", "Ana Quispe", Role.ADMIN, "2", "Transportes Andinos SAC", OrgType.CARRIER, true),
            apiUrl = "http://10.0.2.2:8080", versionName = "1.0.0", onLogout = {},
        )
    }
}
