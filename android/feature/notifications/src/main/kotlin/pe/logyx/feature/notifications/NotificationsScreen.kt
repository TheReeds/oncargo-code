package pe.logyx.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LoadingMoreFooter
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SegmentedControl
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import org.koin.compose.koinInject
import pe.logyx.core.data.RealtimeRepository
import pe.logyx.core.data.refreshSignals
import pe.logyx.core.model.RealtimeTypes
import pe.logyx.core.model.NotificationType
import pe.logyx.core.model.NotificationView

/** Centro de notificaciones (pestaña, sin atrás). La navegación por `relatedType/relatedId` la cablea `:app` en [onOpen]. */
@Composable
fun NotificationsRoute(
    onOpen: (NotificationView) -> Unit,
    vm: NotificationsViewModel = koinViewModel(),
    realtime: RealtimeRepository = koinInject(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    // Refresca al entrar/volver a primer plano y con cada `notification.created` (respaldo 60 s solo sin stream en vivo).
    LaunchedEffect(owner, realtime) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.poll()
            realtime.refreshSignals(setOf(RealtimeTypes.NOTIFICATION_CREATED), UNREAD_POLL_MS).collect { vm.poll() }
        }
    }
    NotificationsScreen(
        state = state,
        onFilter = vm::setFilter,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
        onMarkAll = vm::markAllRead,
        onClick = { n -> vm.markRead(n); onOpen(n) },
        onMessageShown = vm::onMessageShown,
    )
}

@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onFilter: (NotificationFilter) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onMarkAll: () -> Unit,
    onClick: (NotificationView) -> Unit,
    onMessageShown: () -> Unit,
) {
    val labelAll = stringResource(R.string.notifications_filter_all)
    val labelUnread = stringResource(R.string.notifications_filter_unread)
    val labelMine = stringResource(R.string.notifications_filter_mine)
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(
        title = stringResource(R.string.notifications_title),
        snackbar = snackbar,
        actions = {
            if (state.hasUnread) {
                LogyxButton(
                    stringResource(R.string.notifications_mark_all), onMarkAll,
                    variant = ButtonVariant.Ghost, size = ButtonSize.Medium, fullWidth = false,
                    enabled = !state.markingAll,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegmentedControl(
                options = NotificationFilter.entries, selected = state.filter, onSelect = onFilter,
                optionLabel = { when (it) { NotificationFilter.ALL -> labelAll; NotificationFilter.UNREAD -> labelUnread; NotificationFilter.MINE -> labelMine } },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.loading -> SkeletonList(Modifier.padding(16.dp))
                state.error != null -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh, Modifier.weight(1f)) {
                    if (state.items.isEmpty()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                EmptyState(
                                    title = stringResource(R.string.notifications_empty_title),
                                    message = stringResource(
                                        when (state.filter) {
                                            NotificationFilter.UNREAD -> R.string.notifications_empty_unread_message
                                            NotificationFilter.MINE -> R.string.notifications_empty_mine_message
                                            NotificationFilter.ALL -> R.string.notifications_empty_message
                                        },
                                    ),
                                )
                            }
                        }
                    } else {
                        val listState = rememberLazyListState()
                        val nearEnd by remember {
                            derivedStateOf {
                                val info = listState.layoutInfo
                                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                                info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
                            }
                        }
                        LaunchedEffect(nearEnd, state.items.size, state.hasMore) { if (nearEnd && state.hasMore) onLoadMore() }
                        LazyColumn(
                            Modifier.fillMaxSize(), state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.items, key = { it.id }) { n -> NotificationRow(n, onClick = { onClick(n) }) }
                            if (state.loadingMore) item { LoadingMoreFooter() }
                        }
                    }
                }
            }
        }
    }
}

internal fun NotificationType.icon() = when (this) {
    NotificationType.NEW_BID -> Icons.Rounded.LocalOffer
    NotificationType.BID_ACCEPTED -> Icons.Rounded.CheckCircle
    NotificationType.BID_REJECTED -> Icons.Rounded.Cancel
    NotificationType.COUNTER_OFFER -> Icons.Rounded.SwapHoriz
    NotificationType.NEW_MESSAGE -> Icons.Rounded.Chat
    NotificationType.SHIPMENT_STATUS_CHANGE -> Icons.Rounded.LocalShipping
    NotificationType.INCIDENT_REPORTED -> Icons.Rounded.ReportProblem
    NotificationType.INCIDENT_RESOLVED -> Icons.Rounded.TaskAlt
    NotificationType.NEW_REVIEW -> Icons.Rounded.Star
    NotificationType.TRIP_BOOKING_REQUESTED -> Icons.Rounded.Route
    NotificationType.TRIP_BOOKING_CONFIRMED -> Icons.Rounded.CheckCircle
    NotificationType.TRIP_BOOKING_REJECTED, NotificationType.TRIP_BOOKING_CANCELLED, NotificationType.TRIP_CANCELLED -> Icons.Rounded.Cancel
    NotificationType.SMART_LOAD_SUGGESTED, NotificationType.SMART_LOAD_ACCEPTED -> Icons.Rounded.LocalOffer
    NotificationType.AUCTION_CLOSED -> Icons.Rounded.Gavel
    NotificationType.REQUEST_CANCELLED, NotificationType.SHIPMENT_CANCELLED -> Icons.Rounded.Cancel
    NotificationType.BID_WITHDRAWN -> Icons.Rounded.RemoveCircleOutline
    NotificationType.ORGANIZATION_VERIFICATION -> Icons.Rounded.Verified
    NotificationType.ACCESS_REQUESTED -> Icons.Rounded.Key
    NotificationType.UNKNOWN -> Icons.Rounded.Notifications
}

@Composable
private fun NotificationRow(n: NotificationView, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val unreadState = stringResource(R.string.notifications_unread_state)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (n.read) scheme.surface else scheme.primaryContainer.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { if (!n.read) stateDescription = unreadState }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(scheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(n.type.icon(), null, Modifier.size(22.dp), tint = scheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                n.title, style = MaterialTheme.typography.titleSmall,
                fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(n.body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dateTime(n.createdAt), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                // V16: «Tuyo» = de algo que esta persona creó (su solicitud, su oferta).
                if (n.mine) {
                    Text(
                        stringResource(R.string.notifications_mine_badge), style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(scheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
        }
        if (!n.read) Box(Modifier.padding(top = 6.dp).size(10.dp).background(scheme.primary, CircleShape))
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationsPreview() {
    LogyxTheme {
        NotificationsScreen(
            state = NotificationsUiState(
                loading = false,
                items = listOf(
                    NotificationView("1", NotificationType.NEW_BID, "Nueva oferta", "Transportes Andes ofertó S/ 1,200", read = false, createdAt = "2026-11-10T10:00:00Z"),
                    NotificationView("2", NotificationType.SHIPMENT_STATUS_CHANGE, "Tu carga está en ruta", "Salió de Lima", read = true, createdAt = "2026-11-09T10:00:00Z"),
                ),
            ),
            onFilter = {}, onRefresh = {}, onLoadMore = {}, onMarkAll = {}, onClick = {}, onMessageShown = {},
        )
    }
}
