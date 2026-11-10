package pe.logyx.core.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Pantalla estándar: barra superior LOGYX, host de mensajes y fondo steel-100.
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
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LogyxTopBar(title, subtitle = subtitle, onBack = onBack, actions = actions) },
        bottomBar = bottomBar,
        snackbarHost = { snackbar?.let { LogyxSnackbarHost(it) } },
        content = content,
    )
}
