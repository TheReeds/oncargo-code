package pe.logyx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.koin.android.ext.android.inject
import pe.logyx.app.navigation.LogyxApp
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.LogyxTheme

class MainActivity : ComponentActivity() {
    private val auth: AuthRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // La splash de marca se mantiene mientras se lee la sesión guardada (evita el destello de «cargando»).
        installSplashScreen().setKeepOnScreenCondition { auth.state.value == SessionState.Loading }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LogyxTheme { LogyxApp() } }
    }
}
