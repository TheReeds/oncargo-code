package pe.logyx.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxPalette
import pe.logyx.core.model.VehicleType

/**
 * Siluetas vectoriales simples por tipo de vehículo (dibujadas con Canvas, sin assets).
 * La cabina siempre mira a la derecha; el lienzo lógico es de 160 x 90.
 */
@Composable
fun TruckIllustration(type: VehicleType, modifier: Modifier = Modifier.size(width = 120.dp, height = 68.dp), muted: Boolean = false) {
    val body = if (muted) MaterialTheme.colorScheme.outline.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primary
    val cab = if (muted) MaterialTheme.colorScheme.outline.copy(alpha = 0.8f) else LogyxPalette.Ink900
    val accent = if (muted) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else LogyxPalette.Emerald500
    val glass = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val s = size.width / 160f
        withScale(s) { drawTruck(type, body, cab, accent, glass) }
    }
}

private inline fun DrawScope.withScale(s: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.scale(s, s, Offset.Zero)
    block()
    drawContext.transform.scale(1f / s, 1f / s, Offset.Zero)
}

private fun DrawScope.rr(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 4f) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r, r))

private fun DrawScope.wheel(cx: Float, ink: Color, hub: Color) {
    drawCircle(ink, 9f, Offset(cx, 74f))
    drawCircle(hub, 3.5f, Offset(cx, 74f))
}

private fun DrawScope.drawTruck(type: VehicleType, body: Color, cab: Color, accent: Color, glass: Color) {
    val ink = LogyxPalette.Ink900.copy(alpha = 0.92f)
    // Suelo
    drawLine(accent.copy(alpha = 0.35f), Offset(4f, 84f), Offset(156f, 84f), strokeWidth = 2f)
    when (type) {
        VehicleType.VAN -> {
            // Furgoneta de una sola caja
            val p = Path().apply {
                moveTo(18f, 70f); lineTo(18f, 30f); quadraticTo(18f, 24f, 24f, 24f)
                lineTo(110f, 24f); lineTo(134f, 46f); lineTo(146f, 50f); lineTo(146f, 70f); close()
            }
            drawPath(p, body)
            drawPath(Path().apply { moveTo(114f, 30f); lineTo(130f, 46f); lineTo(114f, 46f); close() }, glass)
            rr(accent, 18f, 58f, 128f, 4f, 2f)
            wheel(44f, ink, glass); wheel(122f, ink, glass)
        }
        VehicleType.TRUCK_SMALL, VehicleType.TRUCK_MEDIUM, VehicleType.TRUCK_LARGE -> {
            val boxW = when (type) { VehicleType.TRUCK_SMALL -> 78f; VehicleType.TRUCK_MEDIUM -> 96f; else -> 112f }
            val x0 = 10f
            val cabX = x0 + boxW + 4f
            rr(body, x0, 18f, boxW, 52f, 5f)
            rr(accent, x0, 56f, boxW, 4f, 2f)
            cabShape(cabX, cab, glass)
            wheel(x0 + 22f, ink, glass); wheel(cabX + 22f, ink, glass)
            if (type == VehicleType.TRUCK_LARGE) wheel(x0 + 46f, ink, glass)
        }
        VehicleType.PLATFORM -> {
            val x0 = 10f; val boxW = 100f; val cabX = x0 + boxW + 4f
            rr(ink, x0, 60f, boxW, 8f, 2f)
            rr(body, x0 + 6f, 36f, 40f, 24f, 3f)
            rr(accent, x0 + 50f, 44f, 38f, 16f, 3f)
            cabShape(cabX, cab, glass)
            wheel(x0 + 24f, ink, glass); wheel(x0 + 74f, ink, glass); wheel(cabX + 22f, ink, glass)
        }
        VehicleType.REFRIGERATED -> {
            val x0 = 10f; val boxW = 98f; val cabX = x0 + boxW + 4f
            rr(Color(0xFFE6F1F5), x0, 18f, boxW, 52f, 5f)
            rr(body, x0, 18f, boxW, 8f, 4f)
            // Copo de nieve simplificado
            val c = Offset(x0 + boxW / 2, 46f)
            for (a in 0 until 3) {
                val ang = Math.toRadians(a * 60.0)
                val dx = (kotlin.math.cos(ang) * 12).toFloat(); val dy = (kotlin.math.sin(ang) * 12).toFloat()
                drawLine(body, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), strokeWidth = 3f)
            }
            cabShape(cabX, cab, glass)
            wheel(x0 + 22f, ink, glass); wheel(cabX + 22f, ink, glass)
        }
        VehicleType.TANKER -> {
            val x0 = 10f; val boxW = 100f; val cabX = x0 + boxW + 4f
            rr(ink, x0, 62f, boxW, 6f, 2f)
            rr(body, x0 + 2f, 24f, boxW - 4f, 40f, 20f)
            drawLine(accent, Offset(x0 + 20f, 32f), Offset(x0 + boxW - 20f, 32f), strokeWidth = 3f)
            drawArc(glass, 0f, 360f, false, Offset(x0 + boxW / 2 - 5, 17f), Size(10f, 8f), style = Stroke(2f))
            cabShape(cabX, cab, glass)
            wheel(x0 + 24f, ink, glass); wheel(x0 + 76f, ink, glass); wheel(cabX + 22f, ink, glass)
        }
    }
}

private fun DrawScope.cabShape(x: Float, cab: Color, glass: Color) {
    val p = Path().apply {
        moveTo(x, 70f); lineTo(x, 28f); quadraticTo(x, 24f, x + 4f, 24f)
        lineTo(x + 22f, 24f); lineTo(x + 38f, 44f); lineTo(x + 40f, 70f); close()
    }
    drawPath(p, cab)
    drawPath(Path().apply { moveTo(x + 8f, 30f); lineTo(x + 21f, 30f); lineTo(x + 31f, 44f); lineTo(x + 8f, 44f); close() }, glass)
}
