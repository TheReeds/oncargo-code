package pe.logyx.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Pantalla estándar: barra superior OnCargo, host de mensajes y fondo steel-100.
 * Bajo la barra pinta el aviso de [LocalScreenBanner] (verificación, en el inicio) y, para el rol Lector, [ReadOnlyNotice].
 * Pásale el [LogyxSnackbarState] de `rememberLogyxSnackbarState()` y usa `MessageEffect` con el mensaje del ViewModel.
 */
@Composable
fun LogyxScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    snackbar: LogyxSnackbarState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val readOnly = LocalReadOnly.current
    val banner = LocalScreenBanner.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                LogyxTopBar(title, subtitle = subtitle, onBack = onBack, actions = actions)
                if (banner != null) Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { banner() }
                if (readOnly) ReadOnlyNotice(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { snackbar?.let { LogyxSnackbarHost(it) } },
        content = content,
    )
}
