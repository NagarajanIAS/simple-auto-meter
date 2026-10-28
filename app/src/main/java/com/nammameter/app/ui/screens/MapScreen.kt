package com.nammameter.app.ui.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nammameter.app.BuildConfig
import com.nammameter.app.R
import com.nammameter.app.appContainer
import com.nammameter.app.data.AppSettings
import com.nammameter.app.tracking.LatLng
import com.nammameter.app.tracking.LocationHelper
import com.nammameter.app.ui.components.BackTopBar
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Tile source from a user-configurable "{z}/{x}/{y}" URL template.
 * The policy flags follow the OpenStreetMap tile usage policy: no bulk download,
 * identifying User-Agent, and on-device caching.
 */
private class TemplateTileSource(private val template: String) : OnlineTileSourceBase(
    "tiles-" + Integer.toHexString(template.hashCode()),
    0, 19, 256, ".png",
    arrayOf(template),
    "© OpenStreetMap contributors",
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE or
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
    ),
) {
    override fun getTileURLString(pMapTileIndex: Long): String = template
        .replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
        .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
        .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
}

private fun configureOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
        val base = File(context.cacheDir, "osmdroid")
        osmdroidBasePath = base
        osmdroidTileCache = File(base, "tiles")
    }
}

@Composable
fun MapScreen(pickMode: Boolean, onBack: () -> Unit, onPicked: (destination: LatLng, from: LatLng?) -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val settings by container.settings.settings.collectAsState(initial = AppSettings())
    val live by container.liveMeter.state.collectAsState()
    var here by remember { mutableStateOf<LatLng?>(null) }
    var picked by remember { mutableStateOf<LatLng?>(null) }

    LaunchedEffect(Unit) { here = LocationHelper.currentLocation(context) }

    val map = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(13.0827, 80.2707)) // Chennai until we have a fix
        }
    }
    val track = remember { Polyline(map).apply { outlinePaint.color = AndroidColor.rgb(0xE6, 0x9F, 0x00); outlinePaint.strokeWidth = 12f } }
    val positionMarker = remember {
        Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_my_position)
        }
    }
    val pickMarker = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) } }
    var centered by remember { mutableStateOf(false) }
    var appliedTileUrl by remember { mutableStateOf<String?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    Scaffold(topBar = { BackTopBar(stringResource(if (pickMode) R.string.pick_destination else R.string.map_title), onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = {
                    map.overlays.add(CopyrightOverlay(context))
                    map.overlays.add(track)
                    if (pickMode) {
                        map.overlays.add(
                            MapEventsOverlay(object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    picked = LatLng(p.latitude, p.longitude)
                                    return true
                                }
                                override fun longPressHelper(p: GeoPoint): Boolean = false
                            }),
                        )
                    }
                    map
                },
                update = { view ->
                    val tileUrl = settings.tileUrl.ifBlank { AppSettings.DEFAULT_TILE_URL }
                    if (tileUrl != appliedTileUrl) {
                        view.setTileSource(TemplateTileSource(tileUrl))
                        appliedTileUrl = tileUrl
                    }
                    track.setPoints(live.track.map { GeoPoint(it.lat, it.lng) })
                    val pos = live.position ?: here
                    if (pos != null) {
                        positionMarker.position = GeoPoint(pos.lat, pos.lng)
                        positionMarker.title = context.getString(R.string.you_are_here)
                        if (!view.overlays.contains(positionMarker)) view.overlays.add(positionMarker)
                        if (!centered) {
                            view.controller.setCenter(positionMarker.position)
                            centered = true
                        }
                    }
                    picked?.let {
                        pickMarker.position = GeoPoint(it.lat, it.lng)
                        if (!view.overlays.contains(pickMarker)) view.overlays.add(pickMarker)
                    }
                    view.invalidate()
                },
                modifier = Modifier.fillMaxSize(),
            )
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                if (pickMode) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = MaterialTheme.shapes.medium) {
                        Text(stringResource(R.string.pick_hint), Modifier.padding(12.dp))
                    }
                    Button(
                        onClick = { picked?.let { onPicked(it, live.position ?: here) } },
                        enabled = picked != null,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
                    ) { Text(stringResource(R.string.use_destination)) }
                }
            }
        }
    }
}
