package pe.logyx.feature.documents

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.logyx.core.designsystem.components.ButtonSize
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.LogyxBottomSheet
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.LogyxDropdown
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.DocumentType
import java.io.File

/**
 * Hoja «Subir documento» (roadmap 2.16): elige el tipo (guía de remisión, comprobante, orden de servicio u otro) y la fuente,
 * la cámara del sistema (sin permiso CAMERA) o un archivo (JPG, PNG o PDF). [onPicked] recibe los bytes ya leídos
 * (null si no se pudieron leer) para que el ViewModel valide y suba.
 */
@Composable
internal fun UploadDocumentSheet(
    onDismiss: () -> Unit,
    onPicked: (bytes: ByteArray?, fileName: String, mime: String?, type: DocumentType) -> Unit,
    onCameraError: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(DocumentType.REMISSION_GUIDE) }
    var pending by remember { mutableStateOf<File?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pending
        pending = null
        if (ok && file != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull().also { file.delete() } }
                onPicked(bytes, "documento_${System.currentTimeMillis()}.jpg", "image/jpeg", type)
                onDismiss()
            }
        } else file?.delete()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val read = withContext(Dispatchers.IO) { readUri(context, uri) }
                onPicked(read?.bytes, read?.name ?: "documento", read?.mime, type)
                onDismiss()
            }
        }
    }

    val takePhoto = {
        runCatching {
            val dir = File(context.cacheDir, "documents").apply { mkdirs() }
            val file = File.createTempFile("documento_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pending = file
            camera.launch(uri)
        }.onFailure { e ->
            if (e is ActivityNotFoundException || e is IllegalArgumentException || e is java.io.IOException) onCameraError() else throw e
        }
        Unit
    }

    LogyxBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.docs_upload_title)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val labels = UPLOADABLE_TYPES.associateWith { it.label }
            LogyxDropdown(
                stringResource(R.string.docs_upload_type), UPLOADABLE_TYPES, type, { type = it }, { labels.getValue(it) },
                hint = stringResource(R.string.docs_upload_hint),
            )
            LogyxButton(stringResource(R.string.docs_upload_camera), takePhoto, variant = ButtonVariant.Primary, requiresOperate = true)
            LogyxButton(
                stringResource(R.string.docs_upload_file), { picker.launch(UPLOAD_MIME_TYPES) },
                variant = ButtonVariant.Secondary, size = ButtonSize.Medium, requiresOperate = true,
            )
            Text(
                stringResource(R.string.docs_upload_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private class Picked(val bytes: ByteArray, val name: String, val mime: String?)

/** Lee el archivo elegido (hasta [MAX_UPLOAD_BYTES] + 1 para que el ViewModel detecte el exceso sin cargar archivos enormes). */
private fun readUri(context: Context, uri: Uri): Picked? = runCatching {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "documento"
    val bytes = resolver.openInputStream(uri)?.use { it.readNBytes(MAX_UPLOAD_BYTES + 1) } ?: return null
    Picked(bytes, name, resolver.getType(uri))
}.getOrNull()
