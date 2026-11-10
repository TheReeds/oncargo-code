package pe.logyx.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pe.logyx.core.designsystem.LogyxPalette
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.TruckIllustration
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.Role
import pe.logyx.core.model.UserInfo
import pe.logyx.core.model.VehicleType

/** Cuenta de prueba mostrada solo en builds de desarrollo. */
data class DemoAccount(val label: String, val email: String, val password: String)

@Composable
fun LoginRoute(onOpenRegister: () -> Unit, demoAccounts: List<DemoAccount> = emptyList(), viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::submit,
        onOpenRegister = onOpenRegister,
        demoAccounts = demoAccounts,
        onDemo = { viewModel.fill(it.email, it.password) },
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
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        // Cabecera oscura de marca con camión
        Box(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(LogyxPalette.Ink900, LogyxPalette.Petrol700)))
                .statusBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Column {
                Text("LOGYX", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("Fletes entre Lima y la Sierra Sur", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(12.dp))
                TruckIllustration(VehicleType.TRUCK_MEDIUM, Modifier.size(width = 150.dp, height = 84.dp).align(Alignment.End))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Inicia sesión", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = state.email, onValueChange = onEmailChange, label = { Text("Correo") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = state.emailError != null, supportingText = state.emailError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = state.password, onValueChange = onPasswordChange, label = { Text("Contraseña") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                isError = state.passwordError != null, supportingText = state.passwordError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
            if (state.error != null) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small).padding(12.dp),
                ) { Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
            Button(onClick = onSubmit, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Ingresar", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onOpenRegister, modifier = Modifier.fillMaxWidth()) {
                Text("¿Sin cuenta? Regístrate en la web")
            }
            if (demoAccounts.isNotEmpty()) {
                Text("Cuentas demo (solo desarrollo)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    demoAccounts.forEach { AssistChip(onClick = { onDemo(it) }, label = { Text(it.label) }) }
                }
            }
        }
    }
}

private fun roleLabel(user: UserInfo) = when {
    user.role == Role.DRIVER -> "Conductor"
    user.organizationType == OrgType.CARRIER -> "Transportista"
    user.organizationType == OrgType.PYME -> "Empresa (PYME)"
    else -> "Operador"
}

@Composable
fun AccountRoute(apiUrl: String, versionName: String, viewModel: AccountViewModel = koinViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    AccountScreen(user, apiUrl, versionName, onLogout = viewModel::logout)
}

@Composable
fun AccountScreen(user: UserInfo?, apiUrl: String, versionName: String, onLogout: () -> Unit) {
    LogyxScreen("Cuenta") { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (user != null) {
                LogyxCard(Modifier.fillMaxWidth()) {
                    Text(user.fullName, style = MaterialTheme.typography.titleLarge)
                    Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(user.organizationName ?: "—", style = MaterialTheme.typography.titleSmall)
                        if (user.rucVerified) {
                            Spacer(Modifier.size(6.dp))
                            Icon(Icons.Rounded.Verified, "Empresa verificada", Modifier.size(18.dp), tint = LogyxTheme.colors.money)
                        }
                    }
                    Text(roleLabel(user), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Logout, null)
                Spacer(Modifier.size(8.dp))
                Text("Cerrar sesión")
            }
            Text("LOGYX $versionName · API $apiUrl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

