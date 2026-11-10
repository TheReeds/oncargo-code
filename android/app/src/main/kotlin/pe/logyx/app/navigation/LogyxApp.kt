package pe.logyx.app.navigation

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.compose.koinInject
import pe.logyx.app.AppConfig
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.components.LoadingState
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.Role
import pe.logyx.core.model.UserInfo
import pe.logyx.feature.auth.AccountRoute
import pe.logyx.feature.auth.DemoAccount
import pe.logyx.feature.auth.LoginRoute
import pe.logyx.feature.driver.DriverTripRoute
import pe.logyx.feature.loads.CarrierHomeRoute
import pe.logyx.feature.loads.LoadDetailRoute
import pe.logyx.feature.loads.LoadsFeedRoute
import pe.logyx.feature.loads.MyBidsRoute
import pe.logyx.feature.requests.RequestDetailRoute
import pe.logyx.feature.requests.RequestsRoute
import pe.logyx.feature.shipments.ShipmentDetailRoute
import pe.logyx.feature.shipments.ShipmentsRoute

/** Raíz: decide entre carga, login y la app por rol según el estado de la sesión. */
@Composable
fun LogyxApp(auth: AuthRepository = koinInject(), config: AppConfig = koinInject()) {
    val session by auth.state.collectAsStateWithLifecycle()
    when (val s = session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize()) { LoadingState() }
        SessionState.LoggedOut -> {
            val context = LocalContext.current
            LoginRoute(
                onOpenRegister = { context.startActivity(Intent(Intent.ACTION_VIEW, "${config.webBaseUrl}/registro".toUri())) },
                demoAccounts = if (config.debug) DEMO_ACCOUNTS else emptyList(),
            )
        }
        // `key` recrea la navegación si cambia el usuario (cierre de sesión + otro rol).
        is SessionState.LoggedIn -> key(s.user.id) { RoleShell(s.user, config) }
    }
}

private val DEMO_ACCOUNTS = listOf(
    DemoAccount("PYME", "pyme@logyx.pe", "Logyx2026!"),
    DemoAccount("Transportista", "carrier@logyx.pe", "Logyx2026!"),
    DemoAccount("Conductor", "driver@logyx.pe", "Logyx2026!"),
)

private enum class AppRole { Carrier, Pyme, Driver, Unsupported }

private fun UserInfo.appRole() = when {
    role == Role.DRIVER -> AppRole.Driver
    organizationType == OrgType.CARRIER -> AppRole.Carrier
    organizationType == OrgType.PYME -> AppRole.Pyme
    else -> AppRole.Unsupported
}

private data class Tab(val label: String, val icon: ImageVector, val route: Any)

private val CARRIER_TABS = listOf(
    Tab("Inicio", Icons.Rounded.Home, CarrierHome),
    Tab("Cargas", Icons.Rounded.Search, CarrierFeed),
    Tab("Ofertas", Icons.Rounded.Gavel, CarrierBids),
    Tab("Envíos", Icons.Rounded.LocalShipping, ShipmentsTab),
    Tab("Cuenta", Icons.Rounded.AccountCircle, Account),
)
private val PYME_TABS = listOf(
    Tab("Solicitudes", Icons.Rounded.Inventory2, RequestsTab),
    Tab("Envíos", Icons.Rounded.LocalShipping, ShipmentsTab),
    Tab("Cuenta", Icons.Rounded.AccountCircle, Account),
)
private val DRIVER_TABS = listOf(
    Tab("Mi viaje", Icons.Rounded.Route, DriverTrip),
    Tab("Cuenta", Icons.Rounded.AccountCircle, Account),
)

@Composable
private fun RoleShell(user: UserInfo, config: AppConfig) {
    val role = user.appRole()
    if (role == AppRole.Unsupported) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Esta cuenta se gestiona desde la web", style = MaterialTheme.typography.titleLarge)
            Text("Los operadores de LOGYX usan el panel web.", style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.padding(top = 16.dp)) { AccountRoute(config.apiBaseUrl, config.versionName) }
        }
        return
    }
    val tabs = when (role) {
        AppRole.Carrier -> CARRIER_TABS
        AppRole.Pyme -> PYME_TABS
        else -> DRIVER_TABS
    }
    val start: Any = tabs.first().route
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showBar = tabs.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        // consumeWindowInsets evita que los Scaffold de cada pantalla vuelvan a aplicar los mismos insets.
        NavHost(nav, startDestination = start, modifier = Modifier.padding(inner).consumeInsets(inner)) {
            composable<CarrierHome> {
                CarrierHomeRoute(
                    onOpenLoad = { nav.navigate(LoadDetail(it)) },
                    onOpenFeed = { nav.navigateTab(CarrierFeed) },
                    onOpenBids = { nav.navigateTab(CarrierBids) },
                    onOpenShipment = { nav.navigate(ShipmentDetail(it)) },
                )
            }
            composable<CarrierFeed> { LoadsFeedRoute(onOpenLoad = { nav.navigate(LoadDetail(it)) }) }
            composable<CarrierBids> { MyBidsRoute(onOpenLoad = { nav.navigate(LoadDetail(it)) }) }
            composable<LoadDetail> { e -> LoadDetailRoute(e.toRoute<LoadDetail>().id, onBack = { nav.popBackStack() }) }

            composable<RequestsTab> { RequestsRoute(onOpenRequest = { nav.navigate(RequestDetail(it)) }) }
            composable<RequestDetail> { e ->
                RequestDetailRoute(
                    e.toRoute<RequestDetail>().id,
                    onBack = { nav.popBackStack() },
                    onOpenShipment = { nav.navigate(ShipmentDetail(it)) },
                )
            }

            composable<ShipmentsTab> { ShipmentsRoute(onOpen = { nav.navigate(ShipmentDetail(it)) }) }
            composable<ShipmentDetail> { e -> ShipmentDetailRoute(e.toRoute<ShipmentDetail>().id, onBack = { nav.popBackStack() }) }

            composable<DriverTrip> { DriverTripRoute() }
            composable<Account> { AccountRoute(config.apiBaseUrl, config.versionName) }
        }
    }
}

private fun Modifier.consumeInsets(padding: androidx.compose.foundation.layout.PaddingValues) =
    this.then(Modifier.consumeWindowInsets(padding))

private fun NavHostController.navigateTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
