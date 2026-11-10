package pe.logyx.core.designsystem.components

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pe.logyx.core.model.GeoPoint

const val POSITRON_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

/**
 * Mapa MapLibre (OpenFreeMap positron) con la ruta origen → destino en petrol.
 * Origen = círculo hueco, destino = punto sólido. Sin API key.
 */
@SuppressLint("MissingPermission")
@Composable
fun RouteMap(
    origin: GeoPoint?,
    destination: GeoPoint?,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    styleUrl: String = POSITRON_STYLE_URL,
    fillParent: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).also { it.onCreate(Bundle()) }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        // El observer solo recibe eventos futuros; sincroniza el estado actual.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    val base = if (fillParent) modifier else modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(10.dp))
    AndroidView(
        modifier = base,
        factory = {
            mapView.getMapAsync { map -> map.setStyle(Style.Builder().fromUri(styleUrl)) { style -> drawRoute(map, style, origin, destination) } }
            mapView
        },
        update = {
            it.getMapAsync { map -> map.getStyle { style -> drawRoute(map, style, origin, destination) } }
        },
    )
}

private const val SOURCE_ID = "logyx-route"
private const val POINTS_ID = "logyx-route-points"

private fun drawRoute(map: MapLibreMap, style: Style, origin: GeoPoint?, destination: GeoPoint?) {
    if (origin == null || destination == null) return
    val o = Point.fromLngLat(origin.lng, origin.lat)
    val d = Point.fromLngLat(destination.lng, destination.lat)
    val line = Feature.fromGeometry(LineString.fromLngLats(listOf(o, d)))
    val pts = FeatureCollection.fromFeatures(
        listOf(Feature.fromGeometry(o).apply { addStringProperty("kind", "origin") }, Feature.fromGeometry(d).apply { addStringProperty("kind", "destination") }),
    )
    (style.getSourceAs<GeoJsonSource>(SOURCE_ID))?.setGeoJson(line)
        ?: style.addSource(GeoJsonSource(SOURCE_ID, line))
    (style.getSourceAs<GeoJsonSource>(POINTS_ID))?.setGeoJson(pts)
        ?: style.addSource(GeoJsonSource(POINTS_ID, pts))
    val petrol = AColor.parseColor("#0B4F6C")
    if (style.getLayer("$SOURCE_ID-line") == null) {
        style.addLayer(
            LineLayer("$SOURCE_ID-line", SOURCE_ID).withProperties(
                PropertyFactory.lineColor(petrol), PropertyFactory.lineWidth(4f),
                PropertyFactory.lineCap("round"), PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
            ),
        )
        style.addLayer(
            CircleLayer("$POINTS_ID-circles", POINTS_ID).withProperties(
                PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(AColor.WHITE),
                PropertyFactory.circleStrokeColor(petrol), PropertyFactory.circleStrokeWidth(3f),
            ),
        )
    }
    val bounds = LatLngBounds.Builder().include(LatLng(origin.lat, origin.lng)).include(LatLng(destination.lat, destination.lng)).build()
    map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 600)
}
