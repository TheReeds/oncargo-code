package pe.logyx.feature.incidents

import android.content.ActivityNotFoundException
import android.content.Context
import android.provider.OpenableColumns
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import pe.logyx.core.model.informative
import pe.logyx.core.model.reportableIncidentCategories
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
    var pending by remember { mutableStateOf<File?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pending
        pending = null
        if (ok && file != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull().also { file.delete() } }
                viewModel.addAttachment(bytes, "incidencia_${System.currentTimeMillis()}.jpg", "image/jpeg")
            }
        } else file?.delete()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val read = withContext(Dispatchers.IO) { readPicked(context, uri) }
                viewModel.addAttachment(read?.bytes, read?.name ?: "archivo", read?.mime)
            }
        }
    }
    val takePhoto = {
        runCatching {
            val dir = File(context.cacheDir, "incident_photos").apply { mkdirs() }
            val file = File.createTempFile("incidencia_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pending = file
            camera.launch(uri)
        }.onFailure { e -> if (e is ActivityNotFoundException || e is IllegalArgumentException || e is java.io.IOException) viewModel.onAttachError() else throw e }
        Unit
    }
    val pickFile = {
        runCatching { picker.launch(ATTACHMENT_MIME_TYPES) }
            .onFailure { e -> if (e is ActivityNotFoundException) viewModel.onAttachError() else throw e }
        Unit
    }

    val goTo = state.reportedId
    LaunchedEffect(goTo, state.attachFailed) {
        if (goTo != null && state.attachFailed == 0) onReported(goTo)
    }
    if (goTo != null && state.attachFailed > 0) {
        LogyxConfirmDialog(
            title = stringResource(R.string.incidents_attach_failed_title),
            message = pluralStringResource(R.plurals.incidents_attach_failed_message, state.attachFailed, state.attachFailed),
            confirmLabel = stringResource(R.string.incidents_attach_failed_action),
            onConfirm = { onReported(goTo) },
            onDismiss = { onReported(goTo) },
        )
    }

    ReportIncidentScreen(
        state = state, onBack = onBack, onCategory = viewModel::onCategory, onDescription = viewModel::onDescription,
        onTakePhoto = takePhoto, onPickFile = pickFile, onRemoveAttachment = viewModel::removeAttachment, onSubmit = viewModel::askSubmit,
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
    onPickFile: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
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
            // V23 (2.7): categorías nuevas; `UNKNOWN` (valor que el servidor agregue después) no se ofrece.
            val labels = reportableIncidentCategories.associateWith { it.label }
            LogyxDropdown(
                label = stringResource(R.string.incidents_category_label),
                options = reportableIncidentCategories,
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
            AttachmentsSection(state, onTakePhoto, onPickFile, onRemoveAttachment)
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
            // Informativas (DELAY, ROAD_BLOCKED, PAYMENT_DISPUTE): no congelan el viaje (RN11).
            message = stringResource(if (state.category?.informative == true) R.string.incidents_confirm_message_informative else R.string.incidents_confirm_message),
            confirmLabel = stringResource(R.string.incidents_confirm_action),
            onConfirm = onConfirm, onDismiss = onDismissConfirm, destructive = true,
        )
    }
}

/** Hasta [MAX_ATTACHMENTS] adjuntos: miniaturas (`EvidenceTile`) con «Quitar», «Tomar foto» y «Elegir archivo». */
@Composable
private fun AttachmentsSection(state: ReportUiState, onTake: () -> Unit, onPick: () -> Unit, onRemove: (Int) -> Unit) {
    val enabled = !state.submitting && state.canAttachMore
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.incidents_attach_label), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.incidents_attach_count, state.attachments.size, MAX_ATTACHMENTS),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.incidents_attach_hint, MAX_ATTACHMENTS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.attachments.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a -> AttachmentTile(a, !state.submitting, { onRemove(a.id) }, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogyxButton(stringResource(R.string.incidents_attach_photo), onTake, Modifier.weight(1f), variant = ButtonVariant.Secondary, size = ButtonSize.Medium, enabled = enabled)
            LogyxButton(stringResource(R.string.incidents_attach_file), onPick, Modifier.weight(1f), variant = ButtonVariant.Secondary, size = ButtonSize.Medium, enabled = enabled)
        }
    }
}

@Composable
private fun AttachmentTile(item: IncidentAttachment, enabled: Boolean, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap = remember(item) {
        if (!item.isImage) null
        else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(item.bytes, 0, item.bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 360) sample *= 2
            BitmapFactory.decodeByteArray(item.bytes, 0, item.bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    val removeDescription = stringResource(R.string.incidents_attach_remove_a11y, item.fileName)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EvidenceTile(item.fileName, Modifier.fillMaxWidth(), image = bitmap, pdf = item.isPdf)
        LogyxButton(
            stringResource(R.string.incidents_attach_remove), onRemove,
            Modifier.fillMaxWidth().semantics { contentDescription = removeDescription },
            variant = ButtonVariant.Ghost, size = ButtonSize.Medium, enabled = enabled,
        )
    }
}

private class Picked(val bytes: ByteArray, val name: String, val mime: String?)

/** Lee el archivo elegido (hasta el máximo + 1 byte: así el ViewModel detecta el exceso sin cargar archivos enormes). */
private fun readPicked(context: Context, uri: Uri): Picked? = runCatching {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "archivo"
    val bytes = resolver.openInputStream(uri)?.use { it.readNBytes(MAX_ATTACHMENT_BYTES + 1) } ?: return null
    Picked(bytes, name, resolver.getType(uri))
}.getOrNull()

@Preview(showBackground = true)
@Composable
private fun ReportPreview() {
    LogyxTheme {
        ReportIncidentScreen(
            ReportUiState(category = IncidentCategory.DELAY, description = "El camión lleva 3 horas detenido en el peaje.", descriptionError = null),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
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
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
