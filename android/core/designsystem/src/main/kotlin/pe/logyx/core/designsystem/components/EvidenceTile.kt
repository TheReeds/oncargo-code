package pe.logyx.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

enum class TileStatus { None, Uploading, Uploaded, Failed }

/**
 * Miniatura cuadrada de una evidencia/documento. [image] null = aún cargando o PDF ([pdf] muestra un icono).
 * [status] pinta el estado de subida; [progress] 0..1 mientras sube.
 */
@Composable
fun EvidenceTile(
    caption: String,
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
    pdf: Boolean = false,
    status: TileStatus = TileStatus.None,
    progress: Float = 0f,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val statusText = when (status) {
        TileStatus.Uploading -> stringResource(R.string.ds_tile_uploading)
        TileStatus.Uploaded -> stringResource(R.string.ds_tile_uploaded)
        TileStatus.Failed -> stringResource(R.string.ds_tile_failed)
        TileStatus.None -> ""
    }
    Column(modifier.semantics { contentDescription = "$caption $statusText".trim() }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                .background(scheme.surfaceVariant)
                .border(1.dp, if (status == TileStatus.Failed) LogyxTheme.colors.dangerText else scheme.outlineVariant, RoundedCornerShape(10.dp))
                .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it },
            contentAlignment = Alignment.Center,
        ) {
            when {
                image != null -> Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                pdf -> Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(40.dp), tint = scheme.onSurfaceVariant)
                else -> Icon(Icons.Rounded.Image, null, Modifier.size(40.dp), tint = scheme.outline)
            }
            when (status) {
                TileStatus.Uploading -> Box(Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.35f)), Alignment.Center) {
                    CircularProgressIndicator({ progress.coerceIn(0.05f, 1f) }, Modifier.size(32.dp), color = scheme.onPrimary, strokeWidth = 3.dp)
                }
                TileStatus.Uploaded -> Icon(Icons.Rounded.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp), tint = LogyxTheme.colors.money)
                TileStatus.Failed -> Icon(Icons.Rounded.ErrorOutline, null, Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp), tint = LogyxTheme.colors.dangerText)
                TileStatus.None -> Unit
            }
        }
        Text(caption, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 2)
    }
}

@Preview(showBackground = true)
@Composable
private fun TilesPreview() {
    LogyxTheme {
        Row(Modifier.padding(16.dp)) {
            EvidenceTile("Foto 1", Modifier.size(90.dp), status = TileStatus.Uploaded)
            EvidenceTile("Foto 2", Modifier.size(90.dp).padding(start = 8.dp), status = TileStatus.Uploading, progress = 0.4f)
            EvidenceTile("Firma", Modifier.size(90.dp).padding(start = 8.dp), status = TileStatus.Failed)
            EvidenceTile("Guía.pdf", Modifier.size(90.dp).padding(start = 8.dp), pdf = true)
        }
    }
}
