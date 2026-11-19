package pe.logyx.feature.incidents

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.EvidenceTile
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxConfirmDialog
import pe.logyx.core.designsystem.components.LogyxDropdown
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.Can
import pe.logyx.core.designsystem.components.LogyxTextField
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.asString
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.IncidentCategory
import java.io.File

/** Reportar una incidencia de un envío. [onReported] recibe el id de la incidencia creada. */
@Composable
fun ReportIncidentRoute(
    shipmentId: String,
    onBack: () -> Unit,
    onReported: (incidentId: String) -> Unit,
    viewModel: ReportIncidentViewModel = koinViewModel { parametersOf(shipmentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<File, Uri>?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = pending
        pending = null
        if (ok && target != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { target.first.readBytes() }.getOrNull().also { target.first.delete() } }
                if (bytes != null && bytes.isNotEmpty()) viewModel.onPhoto(bytes) else viewModel.onPhotoError()
            }
        } else target?.first?.delete()
    }
    val takePhoto = {
        runCatching {
            val dir = File(context.cacheDir, "incident_photos").apply { mkdirs() }
            val file = File.createTempFile("incidencia_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pending = file to uri
            camera.launch(uri)
        }.onFailure { e -> if (e is ActivityNotFoundException || e is IllegalArgumentException || e is java.io.IOException) viewModel.onPhotoError() else throw e }
        Unit
    }

    val goTo = state.reportedId
    LaunchedEffect(goTo, state.photoFailed) {
        if (goTo != null && !state.photoFailed) onReported(goTo)
    }
    if (goTo != null && state.photoFailed) {
        LogyxConfirmDialog(
            title = stringResource(R.string.incidents_photo_failed_title),
            message = stringResource(R.string.incidents_photo_failed_message),
            confirmLabel = stringResource(R.string.incidents_photo_failed_action),
            onConfirm = { onReported(goTo) },
            onDismiss = { onReported(goTo) },
        )
    }

    ReportIncidentScreen(
        state = state, onBack = onBack, onCategory = viewModel::onCategory, onDescription = viewModel::onDescription,
        onTakePhoto = takePhoto, onRemovePhoto = { viewModel.onPhoto(null) }, onSubmit = viewModel::askSubmit,
        onConfirm = viewModel::confirmSubmit, onDismissConfirm = viewModel::dismissConfirm, onMessageShown = viewModel::onMessageShown,
    )
}

@Composable
fun ReportIncidentScreen(
    state: ReportUiState,
    onBack: () -> Unit,
    onCategory: (IncidentCategory) -> Unit,
    onDescription: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSubmit: () -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, onMessageShown)
    LogyxScreen(stringResource(R.string.incidents_report_title), onBack = onBack, snackbar = snackbar) { padding ->
        // Roadmap v2 1.7: el Lector no ve el formulario (la pantalla ya muestra "Modo lectura" con «Pedir acceso»).
        Can {
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.incidents_report_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val labels = IncidentCategory.entries.associateWith { it.label }
            LogyxDropdown(
                label = stringResource(R.string.incidents_category_label),
                options = IncidentCategory.entries.toList(),
                selected = state.category,
                onSelected = onCategory,
                optionLabel = { labels.getValue(it) },
                placeholder = stringResource(R.string.incidents_category_select),
                error = state.categoryError?.asString(),
                enabled = !state.submitting,
            )
            LogyxTextField(
                value = state.description,
                onValueChange = onDescription,
                label = stringResource(R.string.incidents_description_label),
                placeholder = stringResource(R.string.incidents_description_placeholder),
                hint = stringResource(R.string.incidents_description_counter, state.description.length, MAX_DESCRIPTION),
                error = state.descriptionError?.asString(),
                singleLine = false, minLines = 4, maxLines = 8,
                enabled = !state.submitting,
            )
            PhotoSection(state.photo, !state.submitting, onTakePhoto, onRemovePhoto)
            LogyxButton(
                stringResource(R.string.incidents_submit), onSubmit,
                variant = ButtonVariant.Primary, loading = state.submitting, requiresOperate = true,
            )
        }
        }
    }
    if (state.confirming) {
        LogyxConfirmDialog(
            title = stringResource(R.string.incidents_confirm_title),
            message = stringResource(R.string.incidents_confirm_message),
            confirmLabel = stringResource(R.string.incidents_confirm_action),
            onConfirm = onConfirm, onDismiss = onDismissConfirm, destructive = true,
        )
    }
}

@Composable
private fun PhotoSection(photo: IncidentPhoto?, enabled: Boolean, onTake: () -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.incidents_photo_label), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.incidents_photo_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (photo != null) {
                val bitmap = remember(photo) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size, opts)?.asImageBitmap()
                }
                EvidenceTile(stringResource(R.string.incidents_photo_caption), Modifier.width(112.dp), image = bitmap)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LogyxButton(
                    stringResource(if (photo == null) R.string.incidents_photo_take else R.string.incidents_photo_retake), onTake,
                    variant = ButtonVariant.Secondary, size = ButtonSize.Medium, enabled = enabled,
                )
                if (photo != null) {
                    LogyxButton(stringResource(R.string.incidents_photo_remove), onRemove, variant = ButtonVariant.Ghost, size = ButtonSize.Medium, enabled = enabled)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportPreview() {
    LogyxTheme {
        ReportIncidentScreen(
            ReportUiState(category = IncidentCategory.DELAY, description = "El camión lleva 3 horas detenido en el peaje.", descriptionError = null),
            {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportErrorPreview() {
    LogyxTheme {
        ReportIncidentScreen(
            ReportUiState(categoryError = pe.logyx.core.designsystem.components.UiText.of(R.string.incidents_category_required),
                descriptionError = pe.logyx.core.designsystem.components.UiText.of(R.string.incidents_description_required)),
            {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
