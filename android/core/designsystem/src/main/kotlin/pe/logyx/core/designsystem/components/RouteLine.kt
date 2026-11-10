package pe.logyx.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme

/**
 * `Lima ○────── 1,010 km ──────▶ ■ Juliaca`, con fecha/dirección debajo de cada ciudad.
 * Origen = círculo, destino = cuadrado, línea de ruta en petrol.
 */
@Composable
fun RouteLine(
    origin: String,
    destination: String,
    modifier: Modifier = Modifier,
    distance: String? = null,
    originSub: String? = null,
    destinationSub: String? = null,
) {
    val route = LogyxTheme.colors.route
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(origin, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (originSub != null) Text(originSub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Column(Modifier.weight(1.1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                val y = size.height / 2
                drawCircle(route, 5.dp.toPx(), Offset(5.dp.toPx(), y), style = Stroke(2.dp.toPx()))
                val endX = size.width - 5.dp.toPx()
                drawRect(route, Offset(endX - 5.dp.toPx(), y - 5.dp.toPx()), Size(10.dp.toPx(), 10.dp.toPx()))
                drawLine(
                    route, Offset(12.dp.toPx(), y), Offset(endX - 9.dp.toPx(), y), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
                val tri = Path().apply {
                    moveTo(endX - 16.dp.toPx(), y - 4.dp.toPx()); lineTo(endX - 8.dp.toPx(), y); lineTo(endX - 16.dp.toPx(), y + 4.dp.toPx()); close()
                }
                drawPath(tri, route)
            }
            if (distance != null) Text(distance, style = LogyxText.mono, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(destination, style = MaterialTheme.typography.titleMedium, maxLines = 1, textAlign = TextAlign.End)
            if (destinationSub != null) Text(destinationSub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, textAlign = TextAlign.End)
        }
    }
}

