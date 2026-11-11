package pe.logyx.feature.negotiation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.AlertBanner
import pe.logyx.core.designsystem.components.BubbleState
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.ChatBubble
import pe.logyx.core.designsystem.components.ChatComposer
import pe.logyx.core.designsystem.components.CountBadge
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StatusPill
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.pill
import pe.logyx.core.model.BidStatus
import pe.logyx.core.model.ChatThreadView

// ───────────────────────── Chat ─────────────────────────

@Composable
fun ChatRoute(
    requestId: String,
    carrierOrgId: String,
    title: String?,
    onBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel(key = "chat-$requestId-$carrierOrgId") { parametersOf(requestId, carrierOrgId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Polling solo en primer plano (RF26): se cancela al salir de STARTED.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.pollLoop() }
    }
    ChatScreen(
        state, title, onBack, viewModel::load, viewModel::loadOlder, viewModel::onDraft, viewModel::send,
        viewModel::retry, viewModel::discard, viewModel::onMessageShown,
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    title: String?,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onLoadOlder: () -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(
        title = title ?: stringResource(R.string.negotiation_chat_title),
        onBack = onBack,
        snackbar = snackbar,
        bottomBar = {
            ChatComposer(
                value = state.draft,
                onValueChange = onDraft,
                onSend = onSend,
                error = state.composerError,
                enabled = !state.readOnly,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.fillMaxSize(), count = 4)
                state.error != null -> ErrorState(state.error, onRetryLoad)
                else -> MessageList(state, onLoadOlder, onRetry, onDiscard)
            }
        }
    }
}

@Composable
private fun MessageList(state: ChatUiState, onLoadOlder: () -> Unit, onRetry: (String) -> Unit, onDiscard: (String) -> Unit) {
    val listState = rememberLazyListState()
    val lastId = state.entries.lastOrNull()?.id
    // Auto-scroll al último mensaje cuando llega o se envía uno nuevo (no al cargar los anteriores).
    val headerCount = if (state.nextCursor != null) 1 else 0
    LaunchedEffect(lastId) {
        if (state.entries.isNotEmpty()) listState.scrollToItem(headerCount + state.entries.size - 1)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (state.nextCursor != null) {
            item(key = "older") {
                LogyxButton(
                    stringResource(R.string.negotiation_chat_older), onLoadOlder,
                    variant = ButtonVariant.Ghost, size = ButtonSize.Medium, loading = state.loadingOlder,
                )
            }
        }
        if (state.entries.isEmpty()) {
            item(key = "empty") {
                EmptyState(stringResource(R.string.negotiation_chat_empty_title), stringResource(R.string.negotiation_chat_empty_message))
            }
        }
        items(state.entries, key = { it.id }) { e ->
            Column {
                ChatBubble(
                    text = e.body,
                    mine = e.mine,
                    time = if (e.isLocal) null else Fmt.time(e.createdAt),
                    sender = e.senderName,
                    system = e.system,
                    price = e.proposedPrice,
                    state = e.state,
                    onRetry = if (e.state == BubbleState.Failed) ({ onRetry(e.id) }) else null,
                )
                if (e.state == BubbleState.Failed) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        LogyxButton(
                            stringResource(R.string.negotiation_chat_discard), { onDiscard(e.id) },
                            variant = ButtonVariant.Ghost, size = ButtonSize.Medium, fullWidth = false,
                        )
                    }
                }
            }
        }
        if (state.readOnly) {
            item(key = "closed") {
                AlertBanner(state.readOnlyReason ?: stringResource(R.string.negotiation_chat_closed), Modifier.padding(top = 8.dp))
            }
        }
    }
}

// ───────────────────────── Hilos (PYME) ─────────────────────────

@Composable
fun ThreadsRoute(
    requestId: String,
    onBack: () -> Unit,
    onOpenChat: (carrierOrgId: String, carrierName: String?) -> Unit,
    viewModel: ThreadsViewModel = koinViewModel(key = "threads-$requestId") { parametersOf(requestId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ThreadsScreen(state, onBack, viewModel::refresh, onOpenChat)
}

@Composable
fun ThreadsScreen(
    state: ThreadsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenChat: (String, String?) -> Unit,
) {
    LogyxScreen(stringResource(R.string.negotiation_threads_title), onBack = onBack) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> SkeletonList(Modifier.fillMaxSize(), count = 4)
                state.error != null && state.threads.isEmpty() -> ErrorState(state.error, onRefresh)
                else -> Refreshable(state.refreshing, onRefresh) {
                    if (state.threads.isEmpty()) {
                        EmptyState(
                            stringResource(R.string.negotiation_threads_empty_title),
                            stringResource(R.string.negotiation_threads_empty_message),
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.threads, key = { it.carrierOrgId }) { t ->
                                ThreadCard(t) { onOpenChat(t.carrierOrgId, t.carrierName) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadCard(t: ChatThreadView, onClick: () -> Unit) {
    LogyxCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    t.carrierName ?: stringResource(R.string.negotiation_threads_carrier_fallback),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                t.bidAmount?.let {
                    Text(
                        stringResource(R.string.negotiation_threads_offer, Fmt.money(it)),
                        style = LogyxText.moneySmall, color = LogyxTheme.colors.moneyText,
                    )
                }
                t.bidStatus?.let { StatusPill(it.pill()) }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (t.lastMessageAt != null) {
                    Text(Fmt.dateTime(t.lastMessageAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (t.messageCount > 0) {
                    CountBadge(t.messageCount, description = stringResource(R.string.negotiation_threads_count_description, t.messageCount))
                }
            }
        }
        Text(
            t.lastMessage ?: stringResource(R.string.negotiation_threads_no_messages),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───────────────────────── Previews ─────────────────────────

@Preview(showBackground = true)
@Composable
private fun ChatPreview() {
    LogyxTheme {
        ChatScreen(
            ChatUiState(
                loading = false,
                entries = listOf(
                    ChatEntry("1", "¿Pueden recoger mañana?", mine = false, senderName = "Cargo Sur", createdAt = "2026-11-10T09:00:00-05:00"),
                    ChatEntry("2", "Sí, a las 8:00.", mine = true, createdAt = "2026-11-10T09:05:00-05:00"),
                    ChatEntry("3", "La PYME propone S/ 1,700.00", mine = false, system = true, proposedPrice = 1700.0, createdAt = "2026-11-10T09:10:00-05:00"),
                    ChatEntry("local-1", "Gracias", mine = true, state = BubbleState.Failed),
                ),
                composerError = "No puedes compartir teléfonos, correos ni enlaces. Coordina todo por el chat de LOGYX.",
            ),
            "Cargo Sur", {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ThreadsPreview() {
    LogyxTheme {
        ThreadsScreen(
            ThreadsUiState(
                loading = false,
                threads = listOf(ChatThreadView("r1", "c1", "Cargo Sur", "b1", 1700.0, BidStatus.PENDING, 3, "Sí, a las 8:00.", "2026-11-10T09:05:00-05:00")),
            ),
            {}, {}, { _, _ -> },
        )
    }
}
