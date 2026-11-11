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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import pe.logyx.app.R
import androidx.compose.ui.res.stringResource
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.designsystem.components.ProvideRealtimeStatus
import androidx.compose.runtime.collectAsState
import pe.logyx.core.data.SessionState
import pe.logyx.core.designsystem.catalog.UiCatalogScreen
import pe.logyx.core.designsystem.components.LoadingState
import pe.logyx.core.designsystem.components.LogyxBottomBar
import pe.logyx.core.designsystem.components.LogyxNavItem
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
import pe.logyx.feature.documents.DocumentsRoute
import pe.logyx.feature.incidents.IncidentDetailRoute
import pe.logyx.feature.incidents.IncidentsListRoute
import pe.logyx.feature.incidents.ReportIncidentRoute
import pe.logyx.feature.negotiation.ChatRoute
import pe.logyx.feature.negotiation.ThreadsRoute
import pe.logyx.feature.notifications.NotificationsRoute
import pe.logyx.feature.notifications.rememberUnreadCount
import pe.logyx.feature.reputation.OrganizationProfileRoute
import pe.logyx.feature.trips.CarrierTripDetailRoute
import pe.logyx.feature.trips.CarrierTripsRoute
import pe.logyx.feature.trips.PublishTripRoute
import pe.logyx.feature.trips.PymeTripDetailRoute
import pe.logyx.feature.trips.PymeTripsRoute
import pe.logyx.feature.trips.ReturnLoadsRoute
import pe.logyx.feature.trips.SmartLoadsRoute
import pe.logyx.feature.reputation.ReviewRoute
import pe.logyx.core.model.NotificationTarget
import pe.logyx.core.model.NotificationType
import pe.logyx.core.model.NotificationView
import androidx.compose.material.icons.rounded.Notifications

/** Raíz: decide entre carga, login y la app por rol según el estado de la sesión. */
@Composable
fun LogyxApp(auth: AuthRepository = koinInject(), config: AppConfig = koinInject()) {
    val session by auth.state.collectAsStateWithLifecycle()
    when (val s = session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize()) { LoadingState() }
        SessionState.LoggedOut -> {
            val context = LocalContext.current
            // Catálogo del sistema de diseño: solo en builds debug.
            var showCatalog by rememberSaveable { mutableStateOf(false) }
            if (config.debug && showCatalog) {
                BackHandler { showCatalog = false }
                UiCatalogScreen(onBack = { showCatalog = false })
            } else {
                LoginRoute(
                    onOpenRegister = { context.startActivity(Intent(Intent.ACTION_VIEW, "${config.webBaseUrl}/registro".toUri())) },
                    demoAccounts = if (config.debug) DEMO_ACCOUNTS else emptyList(),
                    onOpenCatalog = if (config.debug) ({ showCatalog = true }) else null,
                )
            }
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

private data class Tab(@androidx.annotation.StringRes val label: Int, val icon: ImageVector, val route: Any)

private val CARRIER_TABS = listOf(
    Tab(R.string.tab_home, Icons.Rounded.Home, CarrierHome),
    Tab(R.string.tab_loads, Icons.Rounded.Search, CarrierFeed),
    Tab(R.string.tab_bids, Icons.Rounded.Gavel, CarrierBids),
    Tab(R.string.tab_shipments, Icons.Rounded.LocalShipping, ShipmentsTab),
    Tab(R.string.tab_notifications, Icons.Rounded.Notifications, NotificationsTab),
    Tab(R.string.tab_account, Icons.Rounded.AccountCircle, Account),
)
private val PYME_TABS = listOf(
    Tab(R.string.tab_requests, Icons.Rounded.Inventory2, RequestsTab),
    Tab(R.string.tab_trips, Icons.Rounded.Route, PymeTrips),
    Tab(R.string.tab_shipments, Icons.Rounded.LocalShipping, ShipmentsTab),
    Tab(R.string.tab_notifications, Icons.Rounded.Notifications, NotificationsTab),
    Tab(R.string.tab_account, Icons.Rounded.AccountCircle, Account),
)
private val DRIVER_TABS = listOf(
    Tab(R.string.tab_trip, Icons.Rounded.Route, DriverTrip),
    Tab(R.string.tab_account, Icons.Rounded.AccountCircle, Account),
)

@Composable
private fun RoleShell(user: UserInfo, config: AppConfig) {
    val role = user.appRole()
    if (role == AppRole.Unsupported) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.unsupported_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.unsupported_message), style = MaterialTheme.typography.bodyMedium)
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
    val unread by rememberUnreadCount(enabled = role != AppRole.Driver)
    val showBar = tabs.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }

    val realtimeStatus by koinInject<RealtimeRepository>().status.collectAsState()
    ProvideRealtimeStatus(realtimeStatus) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                val selectedIndex = tabs.indexOfFirst { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
                LogyxBottomBar(
                    items = tabs.map {
                        LogyxNavItem(stringResource(it.label), it.icon, badgeCount = if (it.route == NotificationsTab) unread else null)
                    },
                    selectedIndex = selectedIndex,
                    onSelect = { nav.navigateTab(tabs[it].route) },
                )
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
                    onOpenTrips = { nav.navigate(CarrierTrips) },
                    onOpenReturnLoads = { nav.navigate(ReturnLoads) },
                    onOpenSmartLoads = { nav.navigate(SmartLoads) },
                )
            }
            composable<CarrierTrips> {
                CarrierTripsRoute(
                    onBack = { nav.popBackStack() },
                    onOpenTrip = { nav.navigate(CarrierTripDetail(it)) },
                    onPublish = { nav.navigate(PublishTrip) },
                )
            }
            composable<PublishTrip> {
                PublishTripRoute(
                    onBack = { nav.popBackStack() },
                    onPublished = { id ->
                        // Reemplaza el formulario por el detalle del viaje recién publicado.
                        nav.navigate(CarrierTripDetail(id, published = true)) { popUpTo<PublishTrip> { inclusive = true } }
                    },
                )
            }
            composable<CarrierTripDetail> { e ->
                val d = e.toRoute<CarrierTripDetail>()
                CarrierTripDetailRoute(
                    d.id, d.published, onBack = { nav.popBackStack() },
                    onOpenShipment = { nav.navigate(ShipmentDetail(it)) },
                )
            }
            composable<PymeTripDetail> { e ->
                PymeTripDetailRoute(
                    e.toRoute<PymeTripDetail>().id, onBack = { nav.popBackStack() },
                    onOpenShipment = { nav.navigate(ShipmentDetail(it)) },
                )
            }
            composable<ReturnLoads> { ReturnLoadsRoute(onBack = { nav.popBackStack() }) }
            composable<SmartLoads> { SmartLoadsRoute(onBack = { nav.popBackStack() }) }
            composable<PymeTrips> { PymeTripsRoute(onOpenShipment = { nav.navigate(ShipmentDetail(it)) }) }
            composable<CarrierFeed> { LoadsFeedRoute(onOpenLoad = { nav.navigate(LoadDetail(it)) }) }
            composable<CarrierBids> {
                MyBidsRoute(
                    onOpenLoad = { nav.navigate(LoadDetail(it)) },
                    onOpenChat = { requestId, title -> nav.navigate(Chat(requestId, user.organizationId, title)) },
                )
            }
            composable<LoadDetail> { e ->
                LoadDetailRoute(
                    e.toRoute<LoadDetail>().id,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { requestId, title -> nav.navigate(Chat(requestId, user.organizationId, title)) },
                    onOpenOrganization = { nav.navigate(OrgProfile(it)) },
                )
            }

            composable<RequestsTab> { RequestsRoute(onOpenRequest = { nav.navigate(RequestDetail(it)) }) }
            composable<RequestDetail> { e ->
                RequestDetailRoute(
                    e.toRoute<RequestDetail>().id,
                    onBack = { nav.popBackStack() },
                    onOpenShipment = { nav.navigate(ShipmentDetail(it)) },
                    onOpenChat = { carrierOrgId, name -> nav.navigate(Chat(e.toRoute<RequestDetail>().id, carrierOrgId, name)) },
                    onOpenThreads = { nav.navigate(Threads(e.toRoute<RequestDetail>().id)) },
                    onOpenOrganization = { nav.navigate(OrgProfile(it)) },
                )
            }

            composable<ShipmentsTab> { ShipmentsRoute(onOpen = { nav.navigate(ShipmentDetail(it)) }) }
            composable<ShipmentDetail> { e ->
                val id = e.toRoute<ShipmentDetail>().id
                ShipmentDetailRoute(
                    id, onBack = { nav.popBackStack() },
                    onOpenDocuments = { nav.navigate(Documents(id)) },
                    onOpenIncidents = { nav.navigate(IncidentsList(id)) },
                    onReportIncident = { nav.navigate(ReportIncident(id)) },
                    onReview = { nav.navigate(Review(id)) },
                )
            }

            composable<NotificationsTab> { NotificationsRoute(onOpen = { n -> nav.openNotification(n, role, user.organizationId) }) }
            composable<Chat> { e ->
                val c = e.toRoute<Chat>()
                ChatRoute(c.requestId, c.carrierOrgId, c.title, onBack = { nav.popBackStack() })
            }
            composable<Threads> { e ->
                val requestId = e.toRoute<Threads>().requestId
                ThreadsRoute(requestId, onBack = { nav.popBackStack() }, onOpenChat = { orgId, name -> nav.navigate(Chat(requestId, orgId, name)) })
            }
            composable<OrgProfile> { e -> OrganizationProfileRoute(e.toRoute<OrgProfile>().orgId, onBack = { nav.popBackStack() }) }
            composable<Review> { e ->
                ReviewRoute(e.toRoute<Review>().shipmentId, onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
            }
            composable<Documents> { e -> DocumentsRoute(e.toRoute<Documents>().shipmentId, onBack = { nav.popBackStack() }) }
            composable<IncidentsList> { e ->
                val id = e.toRoute<IncidentsList>().shipmentId
                IncidentsListRoute(
                    id, onBack = { nav.popBackStack() },
                    onOpenIncident = { nav.navigate(IncidentDetail(it)) },
                    onReport = { nav.navigate(ReportIncident(id)) },
                )
            }
            composable<ReportIncident> { e ->
                ReportIncidentRoute(
                    e.toRoute<ReportIncident>().shipmentId, onBack = { nav.popBackStack() },
                    onReported = { incidentId -> nav.popBackStack(); nav.navigate(IncidentDetail(incidentId)) },
                )
            }
            composable<IncidentDetail> { e -> IncidentDetailRoute(e.toRoute<IncidentDetail>().incidentId, onBack = { nav.popBackStack() }) }

            composable<DriverTrip> {
                DriverTripRoute(
                    onReportIncident = { nav.navigate(ReportIncident(it)) },
                    onOpenIncidents = { nav.navigate(IncidentsList(it)) },
                )
            }
            composable<Account> { AccountRoute(config.apiBaseUrl, config.versionName) }
        }
    }
    }
}

private fun Modifier.consumeInsets(padding: androidx.compose.foundation.layout.PaddingValues) =
    this.then(Modifier.consumeWindowInsets(padding))

/** Navega al recurso de una notificación (`relatedType`/`relatedId`). */
private fun NavHostController.openNotification(n: NotificationView, role: AppRole, orgId: String) {
    val id = n.relatedId
    when {
        id == null -> Unit
        n.relatedType == NotificationTarget.TRIP -> if (role == AppRole.Pyme) navigate(PymeTripDetail(id)) else navigate(CarrierTripDetail(id))
        n.relatedType == NotificationTarget.SMART_LOAD -> navigate(SmartLoads)
        n.type == NotificationType.NEW_MESSAGE && n.relatedType == NotificationTarget.REQUEST ->
            navigate(if (role == AppRole.Pyme) Threads(id) else Chat(id, orgId, null))
        n.relatedType == NotificationTarget.REQUEST -> navigate(if (role == AppRole.Pyme) RequestDetail(id) else LoadDetail(id))
        n.relatedType == NotificationTarget.SHIPMENT -> navigate(ShipmentDetail(id))
        n.relatedType == NotificationTarget.INCIDENT -> navigate(IncidentDetail(id))
        n.relatedType == NotificationTarget.COUNTER_OFFER -> navigateTab(CarrierBids)
        n.relatedType == NotificationTarget.BID -> navigateTab(if (role == AppRole.Pyme) RequestsTab else CarrierBids)
        n.relatedType == NotificationTarget.REVIEW -> navigate(OrgProfile(orgId))
    }
}

private fun NavHostController.navigateTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
