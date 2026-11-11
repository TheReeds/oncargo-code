package pe.logyx.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Ejecuta [block] (típicamente `viewModel.liveLoop()`, que colecta `RealtimeRepository.refreshSignals`) **solo mientras la
 * pantalla está en primer plano** (`STARTED`): se cancela al pasar a segundo plano —y con ello se cierra la conexión
 * SSE compartida— y se relanza al volver. [key] reinicia el bloque (p. ej. el id mostrado).
 */
@Composable
fun LiveEffect(key: Any?, block: suspend () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current = rememberUpdatedState(block)
    LaunchedEffect(key, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { current.value() }
    }
}
