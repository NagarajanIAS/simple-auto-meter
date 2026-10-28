package com.nammameter.app.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

object LocationHelper {
    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasAnyLocation(context: Context): Boolean = hasFineLocation(context) ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    /** Permissions requested after the in-app disclosure. Never background location. */
    fun requestedPermissions(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    /** One-shot current position, or null without permission / fix. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): LatLng? {
        if (!hasAnyLocation(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc = try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: try {
            client.lastLocation.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return loc?.let { LatLng(it.latitude, it.longitude) }
    }
}
