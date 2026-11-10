package pe.logyx.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R

/** Barrido de brillo para placeholders de carga. */
@Composable
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    return this.clip(shape).background(
        Brush.horizontalGradient(
            0f to base, (x - 0.2f).coerceIn(0f, 1f) to base, x.coerceIn(0f, 1f) to highlight,
            (x + 0.2f).coerceIn(0f, 1f) to base, 1f to base,
        ),
    )
}

@Composable
fun SkeletonBox(width: Dp?, height: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).height(height).shimmer(shape))
}

/** Esqueleto con la forma de una `LoadCard`. */
@Composable
fun SkeletonLoadCard(modifier: Modifier = Modifier) {
    LogyxCard(modifier.fillMaxWidth()) {
        SkeletonBox(140.dp, 28.dp)
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBox(null, 16.dp)
            SkeletonBox(200.dp, 12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(80.dp, 24.dp, shape = CircleShape)
                SkeletonBox(60.dp, 24.dp, shape = CircleShape)
                SkeletonBox(90.dp, 24.dp, shape = CircleShape)
            }
            SkeletonBox(null, 48.dp, shape = RoundedCornerShape(14.dp))
        }
    }
}

/** Lista de esqueletos para el primer render de un feed. */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, count: Int = 3) {
    val desc = stringResource(R.string.ds_loading)
    Column(
        modifier.padding(16.dp).semantics { contentDescription = desc },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { repeat(count) { SkeletonLoadCard() } }
}

@Preview(showBackground = true)
@Composable
private fun SkeletonPreview() {
    LogyxTheme { SkeletonList() }
}
