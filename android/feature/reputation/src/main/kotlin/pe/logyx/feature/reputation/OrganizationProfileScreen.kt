package pe.logyx.feature.reputation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.cityLabel
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.InitialsAvatar
import pe.logyx.core.designsystem.components.LoadingMoreFooter
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxCard
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.RatingRow
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SkeletonBox
import pe.logyx.core.designsystem.components.SkeletonList
import pe.logyx.core.designsystem.components.StarRating
import pe.logyx.core.designsystem.components.TrustScoreBadge
import pe.logyx.core.designsystem.components.VerifiedBadge
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.ReputationView
import pe.logyx.core.model.ReviewView

/** Perfil público de una organización (RF40-45): Trust Score, promedios por dimensión y reseñas. */
@Composable
fun OrganizationProfileRoute(organizationId: String, onBack: () -> Unit) {
    val vm: OrganizationProfileViewModel = koinViewModel(key = "profile-$organizationId") { parametersOf(organizationId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, vm::onMessageShown)
    OrganizationProfileScreen(state, snackbar, onBack, vm::refresh, vm::loadMore)
}

@Composable
internal fun OrganizationProfileScreen(
    state: ProfileUiState,
    snackbar: pe.logyx.core.designsystem.components.LogyxSnackbarState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    LogyxScreen(stringResource(R.string.rep_profile_title), onBack = onBack, snackbar = snackbar) { padding ->
        val rep = state.reputation
        when {
            state.loading -> SkeletonList(Modifier.padding(padding), count = 3)
            rep == null -> ErrorState(state.error.orEmpty(), onRefresh, Modifier.padding(padding))
            else -> Refreshable(state.refreshing, onRefresh, Modifier.padding(padding).fillMaxSize()) {
                ProfileContent(rep, state, onLoadMore)
            }
        }
    }
}

@Composable
private fun ProfileContent(rep: ReputationView, state: ProfileUiState, onLoadMore: () -> Unit) {
    val isPyme = rep.type == OrgType.PYME
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(rep) }
        item { TrustCard(rep) }
        item {
            LogyxCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RatingRow(stringResource(R.string.rep_dim_punctuality), rep.averagePunctuality)
                    RatingRow(
                        stringResource(if (isPyme) R.string.rep_dim_cargo_declared else R.string.rep_dim_cargo_condition),
                        rep.averageCargoCondition,
                    )
                    RatingRow(stringResource(R.string.rep_dim_communication), rep.averageCommunication)
                }
            }
        }
        if (rep.totalReviews == 0 && state.reviews.isEmpty()) {
            item {
                EmptyState(stringResource(R.string.rep_empty_title), stringResource(R.string.rep_empty_message))
            }
        } else {
            item { Text(stringResource(R.string.rep_recent_reviews), style = MaterialTheme.typography.headlineSmall) }
            items(state.reviews, key = { it.id }) { ReviewCard(it) }
            if (state.loadingMore) item { LoadingMoreFooter() }
            else if (state.hasMore) item {
                LogyxButton(stringResource(R.string.rep_see_more), onLoadMore, variant = ButtonVariant.Secondary)
            }
        }
    }
}

@Composable
private fun Header(rep: ReputationView) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        InitialsAvatar(rep.legalName, size = 64.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(rep.legalName, style = MaterialTheme.typography.titleLarge)
            val type = stringResource(if (rep.type == OrgType.PYME) R.string.rep_type_pyme else R.string.rep_type_carrier)
            Text(
                listOfNotNull(type, rep.city?.let { cityLabel(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rep.rucVerified) VerifiedBadge()
        }
    }
}

@Composable
private fun TrustCard(rep: ReputationView) {
    LogyxCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Fmt.decimal1(rep.trustScore), style = LogyxText.kpi.copy(fontSize = 44.sp, lineHeight = 48.sp), color = MaterialTheme.colorScheme.onSurface)
                Column {
                    Text(stringResource(R.string.rep_trust_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TrustScoreBadge(rep.trustScore)
                }
            }
            Text(stringResource(R.string.rep_trust_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarRating(Math.round(rep.averageRating ?: 0.0).toInt(), size = 20.dp)
                val count = if (rep.totalReviews == 1) stringResource(R.string.rep_total_reviews_one) else stringResource(R.string.rep_total_reviews, rep.totalReviews)
                val text = rep.averageRating?.let { stringResource(R.string.rep_average, Fmt.decimal1(it)) + " · " + count } ?: count
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun ReviewCard(review: ReviewView) {
    LogyxCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    review.reviewerName ?: stringResource(R.string.rep_anonymous),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1,
                )
                Text(Fmt.date(review.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StarRating(Math.round(review.rating).toInt(), size = 18.dp)
            review.comment?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfilePreview() {
    LogyxTheme {
        val rep = ReputationView(
            "o1", "Transportes Andes SAC", OrgType.CARRIER, rucVerified = true, city = "LIMA", trustScore = 88.4,
            totalReviews = 12, averageRating = 4.6, averagePunctuality = 4.8, averageCargoCondition = 4.5, averageCommunication = 4.4,
            recentReviews = listOf(ReviewView("r1", reviewerName = "Ferretería Lima", rating = 5.0, comment = "Todo a tiempo.", createdAt = "2026-11-02T10:00:00-05:00")),
        )
        ProfileContent(rep, ProfileUiState(loading = false, reputation = rep, reviews = rep.recentReviews, hasMore = true), {})
    }
}
