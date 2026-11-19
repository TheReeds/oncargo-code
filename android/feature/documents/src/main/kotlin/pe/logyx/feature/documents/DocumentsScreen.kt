package pe.logyx.feature.documents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pe.logyx.core.data.userMessage
import pe.logyx.core.designsystem.Fmt
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.components.EmptyState
import pe.logyx.core.designsystem.components.ButtonVariant
import pe.logyx.core.designsystem.components.ErrorState
import pe.logyx.core.designsystem.components.LogyxButton
import pe.logyx.core.designsystem.components.EvidenceTile
import pe.logyx.core.designsystem.components.LogyxScreen
import pe.logyx.core.designsystem.components.LogyxSnackbarState
import pe.logyx.core.designsystem.components.MessageEffect
import pe.logyx.core.designsystem.components.Refreshable
import pe.logyx.core.designsystem.components.SkeletonBox
import pe.logyx.core.designsystem.components.UiMessage
import pe.logyx.core.designsystem.components.rememberLogyxSnackbarState
import pe.logyx.core.designsystem.label
import pe.logyx.core.model.DocumentType
import pe.logyx.core.model.DocumentView
import java.io.File

/** Miniaturas en memoria por id de documento (aprox. 8 MB). */
private val thumbCache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/** Documentos y evidencias de un envío (RF36-38): grilla de miniaturas, visor de imagen y apertura de PDF. */
@Composable
fun DocumentsRoute(shipmentId: String, onBack: () -> Unit) {
    val vm: DocumentsViewModel = koinViewModel(key = "docs-$shipmentId") { parametersOf(shipmentId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = rememberLogyxSnackbarState()
    MessageEffect(state.message, snackbar, vm::onMessageShown)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<DocumentView?>(null) }
    var opening by remember { mutableStateOf<String?>(null) }
    var uploadOpen by remember { mutableStateOf(false) }

    DocumentsScreen(
        state = state,
        onUpload = { uploadOpen = true },
        snackbar = snackbar,
        loadThumbnail = { doc -> loadThumbnail(doc) { vm.fetch(it) } },
        openingId = opening,
        onBack = onBack,
        onRefresh = vm::refresh,
        onRetry = vm::retry,
        onOpen = { doc ->
            if (doc.isPdf) {
                if (opening == null) scope.launch {
                    opening = doc.id
                    openPdf(context, doc) { vm.fetch(it) }.onFailure { vm.showError(it.toUiMessage()) }
                    opening = null
                }
            } else viewing = doc
        },
    )
    if (uploadOpen) {
        UploadDocumentSheet(
            onDismiss = { uploadOpen = false },
            onPicked = vm::upload,
            onCameraError = { vm.showError(UiMessage.error(R.string.docs_upload_camera_error)) },
        )
    }
    viewing?.let { doc ->
        ImageViewer(doc.type.label, loader = { vm.fetch(doc.id).getOrThrow() }, onClose = { viewing = null })
    }
}

private fun Throwable.toUiMessage(): UiMessage = when (this) {
    is NoPdfViewerException -> UiMessage.error(R.string.docs_pdf_no_viewer)
    is java.io.IOException -> UiMessage.error(R.string.docs_pdf_error)
    else -> UiMessage.error(userMessage())
}

private class NoPdfViewerException : Exception()

@Composable
internal fun DocumentsScreen(
    state: DocumentsUiState,
    snackbar: LogyxSnackbarState,
    loadThumbnail: suspend (DocumentView) -> ImageBitmap?,
    openingId: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (DocumentView) -> Unit,
    onUpload: () -> Unit = {},
) {
    LogyxScreen(
        stringResource(R.string.docs_title), onBack = onBack, snackbar = snackbar,
        bottomBar = {
            if (!state.loading && state.error == null) {
                Box(Modifier.padding(16.dp)) {
                    LogyxButton(stringResource(R.string.docs_upload_action), onUpload, variant = ButtonVariant.Secondary, loading = state.uploading, requiresOperate = true)
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LazyVerticalGrid(
                GridCells.Fixed(3), Modifier.padding(padding), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) { items(6) { SkeletonBox(null, 100.dp) } }
            state.error != null -> ErrorState(state.error, onRetry, Modifier.padding(padding))
            else -> Refreshable(state.refreshing, onRefresh, Modifier.padding(padding).fillMaxSize()) {
                if (state.documents.isEmpty()) {
                    EmptyState(stringResource(R.string.docs_empty_title), stringResource(R.string.docs_empty_message))
                } else {
                    LazyVerticalGrid(
                        GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.documents, key = { it.id }) { doc -> DocumentTile(doc, loadThumbnail, doc.id == openingId, onOpen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentTile(doc: DocumentView, loadThumbnail: suspend (DocumentView) -> ImageBitmap?, opening: Boolean, onOpen: (DocumentView) -> Unit) {
    var image by remember(doc.id) { mutableStateOf(thumbCache.get(doc.id)) }
    if (doc.isImage && image == null) {
        LaunchedEffect(doc.id) { image = loadThumbnail(doc) }
    }
    val date = doc.createdAt?.let { Fmt.date(it) }
    val caption = listOfNotNull(doc.type.label, date).joinToString(" · ")
    Box {
        EvidenceTile(caption, image = image, pdf = doc.isPdf, onClick = { onOpen(doc) })
        if (opening) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 3.dp)
    }
}

/** Descarga la miniatura (cacheada por id) y la decodifica con inSampleSize. */
private suspend fun loadThumbnail(doc: DocumentView, fetch: suspend (String) -> Result<ByteArray>): ImageBitmap? {
    thumbCache.get(doc.id)?.let { return it }
    val bytes = fetch(doc.id).getOrNull() ?: return null
    val bmp = withContext(Dispatchers.Default) { decode(bytes, 320) } ?: return null
    return bmp.asImageBitmap().also { thumbCache.put(doc.id, it) }
}

private fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Guarda el PDF en cacheDir/documents y lo abre con un visor externo vía FileProvider. */
private suspend fun openPdf(context: Context, doc: DocumentView, fetch: suspend (String) -> Result<ByteArray>): Result<Unit> = runCatching {
    val bytes = fetch(doc.id).getOrThrow()
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "documents").apply { mkdirs() }
        File(dir, "${doc.id}.pdf").also { it.writeBytes(bytes) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        throw NoPdfViewerException()
    }
}

/** Visor a pantalla completa: pellizco para zoom, doble toque alterna 1x/3x, arrastre cuando hay zoom. */
@Composable
private fun ImageViewer(description: String, loader: suspend () -> ByteArray, onClose: () -> Unit) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching {
            val bytes = loader()
            withContext(Dispatchers.Default) { decode(bytes, 2048) }?.asImageBitmap()
        }.onSuccess { bitmap = it; failed = it == null }.onFailure { failed = true }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val img = bitmap
            when {
                img != null -> ZoomableImage(img, description)
                failed -> Text(
                    stringResource(R.string.docs_viewer_error), Modifier.align(Alignment.Center).padding(32.dp),
                    color = Color.White, style = MaterialTheme.typography.bodyLarge,
                )
                else -> {
                    val loading = stringResource(R.string.docs_viewer_loading)
                    CircularProgressIndicator(Modifier.align(Alignment.Center).semantics { contentDescription = loading }, color = Color.White)
                }
            }
            IconButton(onClose, Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.docs_viewer_close), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ZoomableImage(image: ImageBitmap, description: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        image, description,
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 3f
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        contentScale = ContentScale.Fit,
    )
}

@Preview(showBackground = true)
@Composable
private fun DocumentsPreview() {
    LogyxTheme {
        DocumentsScreen(
            DocumentsUiState(
                loading = false,
                documents = listOf(
                    DocumentView("1", type = DocumentType.DELIVERY_PHOTO, contentType = "image/jpeg", createdAt = "2026-11-02T10:00:00-05:00"),
                    DocumentView("2", type = DocumentType.REMISSION_GUIDE, contentType = "application/pdf", createdAt = "2026-11-01T09:00:00-05:00"),
                ),
            ),
            rememberLogyxSnackbarState(), { null }, null, {}, {}, {}, {},
        )
    }
}
