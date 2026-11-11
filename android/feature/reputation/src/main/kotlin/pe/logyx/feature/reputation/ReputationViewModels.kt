package pe.logyx.feature.reputation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.logyx.core.data.AuthRepository
import pe.logyx.core.data.ReputationRepository
import pe.logyx.core.data.SessionState
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.model.OrgType
import pe.logyx.core.model.ReputationView
import pe.logyx.core.model.ReviewView
import pe.logyx.core.model.ShipmentReviewsView

data class ProfileUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val reputation: ReputationView? = null,
    val reviews: List<ReviewView> = emptyList(),
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val message: UiMessage? = null,
)

class OrganizationProfileViewModel(
    private val organizationId: String,
    private val repo: ReputationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()
    private var nextPage = 0
    private var expanded = false

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.reputation == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            repo.reputation(organizationId).fold(
                onSuccess = { rep ->
                    nextPage = 0
                    expanded = false
                    _state.update {
                        it.copy(
                            loading = false, refreshing = false, reputation = rep, reviews = rep.recentReviews,
                            hasMore = rep.recentReviews.size < rep.totalReviews,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = if (it.reputation == null) e.userMessage() else null,
                            message = if (it.reputation != null) UiMessage.error(e.userMessage()) else null)
                    }
                },
            )
        }
    }

    /** "Ver más": pide la siguiente página de reseñas (la primera vez reemplaza las recientes por la página 0). */
    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            repo.reviews(organizationId, nextPage).fold(
                onSuccess = { page ->
                    val base = if (expanded) _state.value.reviews else emptyList()
                    val merged = (base + page.items).distinctBy { it.id }
                    expanded = true
                    nextPage = page.page + 1
                    _state.update { it.copy(loadingMore = false, reviews = merged, hasMore = page.hasMore) }
                },
                onFailure = { e -> _state.update { it.copy(loadingMore = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}

data class ReviewUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val canReview: Boolean = false,
    /** Motivo (mensaje de la API) cuando no se puede calificar. */
    val blockedReason: String? = null,
    /** Calificaciones ya enviadas por ti en este envío (solo lectura). */
    val existing: List<ReviewView> = emptyList(),
    /** Quién califica: PYME califica al transportista; CARRIER, a la PYME. */
    val reviewerType: OrgType? = null,
    val punctuality: Int = 0,
    val cargoCondition: Int = 0,
    val communication: Int = 0,
    val comment: String = "",
    val commentError: String? = null,
    val submitting: Boolean = false,
    val done: Boolean = false,
    val message: UiMessage? = null,
) {
    val ratesCarrier: Boolean get() = reviewerType != OrgType.CARRIER
    val complete: Boolean get() = punctuality in 1..5 && cargoCondition in 1..5 && communication in 1..5
    val canSubmit: Boolean get() = canReview && complete && commentError == null && !submitting
}

const val MAX_COMMENT = 1000

class ReviewViewModel(
    private val shipmentId: String,
    private val repo: ReputationRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val reviewerType = (auth.state.value as? SessionState.LoggedIn)?.user?.organizationType
    private val _state = MutableStateFlow(ReviewUiState(reviewerType = reviewerType))
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.shipmentReviews(shipmentId).fold(
                onSuccess = { r: ShipmentReviewsView ->
                    _state.update {
                        it.copy(loading = false, canReview = r.canReview, blockedReason = r.blockedReason, existing = r.reviews)
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }

    fun onPunctuality(v: Int) = _state.update { it.copy(punctuality = v) }
    fun onCargoCondition(v: Int) = _state.update { it.copy(cargoCondition = v) }
    fun onCommunication(v: Int) = _state.update { it.copy(communication = v) }
    fun onComment(v: String) = _state.update {
        it.copy(comment = v, commentError = if (v.length > MAX_COMMENT) "" else null)
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            repo.submit(shipmentId, s.punctuality, s.cargoCondition, s.communication, s.comment.trim().ifEmpty { null }).fold(
                onSuccess = {
                    _state.update { it.copy(submitting = false, done = true, message = UiMessage.success(R.string.rep_thanks)) }
                },
                onFailure = { e -> _state.update { it.copy(submitting = false, message = UiMessage.error(e.userMessage())) } },
            )
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
