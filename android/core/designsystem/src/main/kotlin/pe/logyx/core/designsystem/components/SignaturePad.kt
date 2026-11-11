package pe.logyx.core.designsystem.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R
import java.io.ByteArrayOutputStream

/** Estado del [SignaturePad]: trazos y exportación a PNG (fondo blanco, tinta oscura). */
@Stable
class SignaturePadState {
    internal val strokes = mutableStateListOf<List<Offset>>()
    internal var size by mutableStateOf(IntSize.Zero)
    val isEmpty: Boolean get() = strokes.isEmpty()

    fun clear() = strokes.clear()

    /** PNG de la firma, o null si está vacía. */
    fun toPng(): ByteArray? {
        if (strokes.isEmpty() || size.width <= 0 || size.height <= 0) return null
        val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(14, 23, 38)
            style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { s ->
            if (s.size == 1) canvas.drawPoint(s[0].x, s[0].y, paint)
            else {
                val p = AndroidPath(); p.moveTo(s[0].x, s[0].y); s.drop(1).forEach { p.lineTo(it.x, it.y) }
                canvas.drawPath(p, paint)
            }
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}

@Composable
fun rememberSignaturePadState() = remember { SignaturePadState() }

/** Lienzo para que el receptor firme con el dedo. Exporta con [SignaturePadState.toPng]. */
@Composable
fun SignaturePad(state: SignaturePadState, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 200.dp) {
    val description = stringResource(R.string.ds_signature_description)
    val ink = MaterialTheme.colorScheme.onSurface
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .semantics { contentDescription = description },
    ) {
        Canvas(
            Modifier.matchParentSize().pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { current = listOf(it) },
                    onDrag = { change, _ -> change.consume(); current = current + change.position },
                    onDragEnd = { if (current.isNotEmpty()) state.strokes.add(current); current = emptyList() },
                    onDragCancel = { current = emptyList() },
                )
            },
        ) {
            state.size = IntSize(size.width.toInt(), size.height.toInt())
            val stroke = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            (state.strokes + listOf(current)).filter { it.isNotEmpty() }.forEach { s ->
                val path = Path().apply { moveTo(s[0].x, s[0].y); if (s.size == 1) lineTo(s[0].x + 0.1f, s[0].y) else s.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, ink, style = stroke)
            }
        }
        if (state.isEmpty && current.isEmpty()) {
            Text(
                stringResource(R.string.ds_signature_hint), Modifier.align(Alignment.Center).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignaturePadPreview() {
    LogyxTheme { SignaturePad(rememberSignaturePadState(), Modifier.padding(16.dp)) }
}
