package pe.logyx.feature.reputation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.LogyxSnackbarState
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.RatingRow
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StarRating
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.ReviewView

/** Calificar un envío entregado (RN06, RN09): tres dimensiones de 1 a 5 y comentario opcional. */
@Composable
fun ReviewRoute(shipmentId: String, onBack: () -> Unit, onDone: () -> Unit) {
    val vm: ReviewViewModel = koinViewModel(key = "review-$shipmentId") { parametersOf(shipmentId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, vm::onMessageShown)
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ReviewScreen(
        state, snackbar, onBack, vm::load, vm::onPunctuality, vm::onCargoCondition, vm::onCommunication, vm::onComment, vm::submit,
    )
}

@Composable
internal fun ReviewScreen(
    state: ReviewUiState,
    snackbar: LogyxSnackbarState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPunctuality: (Int) -> Unit,
    onCargoCondition: (Int) -> Unit,
    onCommunication: (Int) -> Unit,
    onComment: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    LogyxScreen(stringResource(R.string.rep_review_title), onBack = onBack, snackbar = snackbar) { padding ->
        when {
            state.loading -> SkeletonList(Modifier.padding(padding), count = 2)
            state.error != null -> ErrorState(state.error, onRetry, Modifier.padding(padding))
            !state.canReview -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EmptyState(stringResource(R.string.rep_blocked_title), state.blockedReason.orEmpty())
                if (state.existing.isNotEmpty()) ExistingReviews(state.existing, state.ratesCarrier)
            }
            else -> ReviewForm(state, Modifier.padding(padding), onPunctuality, onCargoCondition, onCommunication, onComment, onSubmit)
        }
    }
}

@Composable
private fun cargoLabel(ratesCarrier: Boolean) =
    stringResource(if (ratesCarrier) R.string.rep_dim_cargo_condition else R.string.rep_dim_cargo_declared)

@Composable
private fun ExistingReviews(reviews: List<ReviewView>, ratesCarrier: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.rep_existing_title), style = MaterialTheme.typography.headlineSmall)
        reviews.forEach { r ->
            LogyxCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RatingRow(stringResource(R.string.rep_dim_punctuality), r.punctuality.toDouble())
                    RatingRow(cargoLabel(ratesCarrier), r.cargoCondition.toDouble())
                    RatingRow(stringResource(R.string.rep_dim_communication), r.communication.toDouble())
                    r.comment?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun ReviewForm(
    state: ReviewUiState,
    modifier: Modifier,
    onPunctuality: (Int) -> Unit,
    onCargoCondition: (Int) -> Unit,
    onCommunication: (Int) -> Unit,
    onComment: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(if (state.ratesCarrier) R.string.rep_review_intro_carrier else R.string.rep_review_intro_pyme),
            style = MaterialTheme.typography.bodyLarge,
        )
        Dimension(stringResource(R.string.rep_dim_punctuality), state.punctuality, onPunctuality)
        Dimension(cargoLabel(state.ratesCarrier), state.cargoCondition, onCargoCondition)
        Dimension(stringResource(R.string.rep_dim_communication), state.communication, onCommunication)
        LogyxTextField(
            state.comment, onComment, stringResource(R.string.rep_comment_label), Modifier.fillMaxWidth(),
            hint = stringResource(R.string.rep_comment_hint),
            error = state.commentError?.let { stringResource(R.string.rep_comment_error) },
            optional = true, singleLine = false, minLines = 3,
        )
        if (!state.complete) {
            Text(stringResource(R.string.rep_submit_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LogyxButton(stringResource(R.string.rep_submit), { confirming = true }, enabled = state.canSubmit, loading = state.submitting)
    }
    if (confirming) {
        LogyxConfirmDialog(
            title = stringResource(R.string.rep_confirm_title),
            message = stringResource(R.string.rep_confirm_message),
            confirmLabel = stringResource(R.string.rep_submit),
            onConfirm = { confirming = false; onSubmit() },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun Dimension(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        StarRating(value, onValueChange = onChange, size = 44.dp)
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewPreview() {
    LogyxTheme {
        ReviewScreen(
            ReviewUiState(loading = false, canReview = true, reviewerType = OrgType.PYME, punctuality = 4),
            rememberLogyxSnackbarState(), {}, {}, {}, {}, {}, {}, {},
        )
    }
}
