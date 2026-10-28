package com.nammameter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nammameter.app.AppContainer
import com.nammameter.app.R
import com.nammameter.app.data.AppSettings
import com.nammameter.app.fare.FareConfig
import com.nammameter.app.fare.FareEstimator
import com.nammameter.app.fare.FareRange
import com.nammameter.app.routing.MapsIntents
import com.nammameter.app.routing.RouteEstimate
import com.nammameter.app.routing.RouteRequest
import com.nammameter.app.routing.RoutingProviderId
import com.nammameter.app.routing.RoutingProviders
import com.nammameter.app.tracking.LatLng
import com.nammameter.app.tracking.LocationHelper
import com.nammameter.app.ui.appViewModel
import com.nammameter.app.ui.components.BackTopBar
import com.nammameter.app.ui.components.SectionTitle
import com.nammameter.app.ui.formatRupees
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface RouteState {
    data object Idle : RouteState
    data object Loading : RouteState
    data class Done(val estimate: RouteEstimate) : RouteState
    data class Failed(val message: String?) : RouteState
}

class EstimatorViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _route = MutableStateFlow<RouteState>(RouteState.Idle)
    val route: StateFlow<RouteState> = _route.asStateFlow()

    fun routeTo(from: LatLng?, to: LatLng) {
        val s = settings.value
        _route.value = RouteState.Loading
        viewModelScope.launch {
            val provider = RoutingProviders.forMapPick(s.routingProvider, s.roadFactor, s.orsApiKey)
            val result = provider.route(RouteRequest(from = from, to = to))
            _route.value = result.fold({ RouteState.Done(it) }, { RouteState.Failed(it.message) })
        }
    }
}

@Composable
fun EstimatorScreen(
    onBack: () -> Unit,
    onNeedPermission: () -> Unit,
    onPickOnMap: () -> Unit,
    picked: LatLng?,
    /** Position the map already had when the destination was picked, if any. */
    pickedFrom: LatLng?,
    onPickedConsumed: () -> Unit,
) {
    val vm = appViewModel { EstimatorViewModel(it) }
    val settings by vm.settings.collectAsState()
    val route by vm.route.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var destination by rememberSaveable { mutableStateOf("") }
    var kmText by rememberSaveable { mutableStateOf("") }
    var waitText by rememberSaveable { mutableStateOf("0") }
    var locating by rememberSaveable { mutableStateOf(false) }
    var locationError by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(picked) {
        val destination = picked ?: return@LaunchedEffect
        val from = pickedFrom ?: LocationHelper.currentLocation(context)
        vm.routeTo(from, destination)
        // Clear only after routing has started: clearing restarts this effect.
        onPickedConsumed()
    }
    LaunchedEffect(route) {
        (route as? RouteState.Done)?.let { kmText = String.format(Locale.ROOT, "%.1f", it.estimate.distanceKm) }
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.estimate_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.plan_trip))
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text(stringResource(R.string.destination_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (destination.isNotBlank()) MapsIntents.directions(context, destination) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { MapsIntents.directions(context, destination) },
                enabled = destination.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.Directions, null)
                Text("  " + stringResource(R.string.open_directions))
            }
            OutlinedButton(
                onClick = {
                    if (!LocationHelper.hasAnyLocation(context)) {
                        onNeedPermission()
                    } else {
                        locating = true
                        locationError = false
                        scope.launch {
                            val here = LocationHelper.currentLocation(context)
                            locating = false
                            if (here != null) MapsIntents.showLocation(context, here) else locationError = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (locating) CircularProgressIndicator(Modifier.height(24.dp)) else Icon(Icons.Default.MyLocation, null)
                Text("  " + stringResource(R.string.show_my_location))
            }
            if (locationError) Text(stringResource(R.string.location_unavailable), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.estimate_hint), style = MaterialTheme.typography.bodySmall)

            SectionTitle(stringResource(R.string.fare_estimate))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = kmText,
                    onValueChange = { kmText = it },
                    label = { Text(stringResource(R.string.distance_km)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = waitText,
                    onValueChange = { waitText = it },
                    label = { Text(stringResource(R.string.waiting_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(onClick = onPickOnMap, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Place, null)
                Text("  " + stringResource(R.string.pick_on_map))
            }
            when (val r = route) {
                RouteState.Loading -> CircularProgressIndicator()
                is RouteState.Done -> Text(
                    if (r.estimate.approximate) {
                        stringResource(R.string.route_approx, r.estimate.distanceKm, settings.roadFactor)
                    } else if (r.estimate.providerId == RoutingProviderId.OPENROUTESERVICE) {
                        stringResource(R.string.route_ors, r.estimate.distanceKm)
                    } else {
                        ""
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                is RouteState.Failed -> Text(stringResource(R.string.route_failed, r.message ?: ""), color = MaterialTheme.colorScheme.error)
                RouteState.Idle -> Unit
            }

            val km = kmText.replace(',', '.').toDoubleOrNull()
            val wait = waitText.toDoubleOrNull() ?: 0.0
            if (km != null && km.isFinite() && km in 0.0..FareConfig.MAX_KM && wait.isFinite() && wait in 0.0..1440.0) {
                val estimate = FareEstimator.estimate(settings.fareConfig, km, wait)
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        RangeRow(stringResource(R.string.day), estimate.day)
                        RangeRow(stringResource(R.string.night), estimate.night)
                        Text(
                            stringResource(R.string.estimate_range_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Text(
                (settings.activePreset?.name ?: stringResource(R.string.tariff_custom)) + " · " + stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RangeRow(label: String, range: FareRange) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            if (range.low == range.high) formatRupees(range.low) else formatRupees(range.low) + " – " + formatRupees(range.high),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
