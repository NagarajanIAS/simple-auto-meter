package com.nammameter.app.routing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nammameter.app.tracking.LatLng
import java.net.URLEncoder
import java.util.Locale

/** Hands navigation off to Google Maps (or the browser) — no Maps SDK or server needed. */
object MapsIntents {
    private const val MAPS_PACKAGE = "com.google.android.apps.maps"

    fun searchUrl(at: LatLng): String =
        "https://www.google.com/maps/search/?api=1&query=${coord(at.lat)},${coord(at.lng)}"

    fun directionsUrl(destination: String): String =
        "https://www.google.com/maps/dir/?api=1&destination=${encode(destination)}&travelmode=driving"

    fun showLocation(context: Context, at: LatLng) {
        val geo = Uri.parse("geo:${coord(at.lat)},${coord(at.lng)}?q=${coord(at.lat)},${coord(at.lng)}")
        open(context, geo, Uri.parse(searchUrl(at)))
    }

    fun directions(context: Context, destination: String) {
        val uri = Uri.parse(directionsUrl(destination))
        open(context, uri, uri)
    }

    /** Tries Google Maps first, then any app (usually the browser). */
    private fun open(context: Context, mapsUri: Uri, fallback: Uri) {
        val maps = Intent(Intent.ACTION_VIEW, mapsUri).setPackage(MAPS_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(maps)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                // Nothing can open a URL; ignore.
            }
        }
    }

    private fun coord(v: Double) = String.format(Locale.ROOT, "%.6f", v)
    private fun encode(s: String) = URLEncoder.encode(s.trim(), "UTF-8")
}
