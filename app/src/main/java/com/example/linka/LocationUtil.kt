package com.example.linka

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Best-effort country lookup for the activity log (see ActivityLogEntry.kt) and the
 * session-time location exchange (see LocationInfo in :core). Blocking - callers must
 * call this off the main thread. Returns null on any failure (no permission, no last-
 * known fix, geocoding failure) - callers treat null as "unknown," never as a reason
 * to block anything.
 */
fun bestEffortCountry(context: Context): String? {
    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return null

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null
    val providers = locationManager.getProviders(true)
    val location = providers
        .mapNotNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
        }
        .maxByOrNull { it.time }
        ?: return null

    return try {
        @Suppress("DEPRECATION")
        val addresses = Geocoder(context, Locale.getDefault())
            .getFromLocation(location.latitude, location.longitude, 1)
        addresses?.firstOrNull()?.countryName
    } catch (_: Exception) {
        null
    }
}
