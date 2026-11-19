package pe.logyx.feature.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Permisos de ubicación del botón «Emergencia» (se piden en tiempo de ejecución). */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

private const val FRESH_MS = 2 * 60_000L

/**
 * Ubicación para el reporte de emergencia, sin servicios de Google (funciona sin datos con GPS): la última conocida si es
 * de hace menos de 2 min; si no, una lectura nueva (el ViewModel corta la espera a los pocos segundos y usa la última).
 * Null sin permiso o sin proveedores activos.
 */
@SuppressLint("MissingPermission")
suspend fun Context.currentDeviceLocation(): DeviceLocation? {
    if (!hasLocationPermission()) return null
    val lm = getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    if (last != null && System.currentTimeMillis() - last.time < FRESH_MS) return last.toDevice()
    val provider = providers.firstOrNull() ?: return last?.toDevice()
    val fresh: Location? = runCatching {
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(this)) { loc ->
                if (cont.isActive) cont.resume(loc)
            }
        }
    }.getOrNull()
    return (fresh ?: last)?.toDevice()
}

private fun Location.toDevice() = DeviceLocation(latitude, longitude)
