package com.nammameter.app.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nammameter.app.AppContainer
import com.nammameter.app.R
import com.nammameter.app.data.AppSettings
import com.nammameter.app.fare.FareConfig
import com.nammameter.app.fare.FarePresets
import com.nammameter.app.fare.FareRounding
import com.nammameter.app.fare.NightRule
import com.nammameter.app.routing.RoutingProviderId
import com.nammameter.app.tracking.MeterParams
import com.nammameter.app.ui.appViewModel
import com.nammameter.app.ui.components.BackTopBar
import com.nammameter.app.ui.components.NumberField
import com.nammameter.app.ui.components.SectionTitle
import com.nammameter.app.ui.components.SwitchRow
import com.nammameter.app.upi.PinGate
import com.nammameter.app.ui.hhMmToMinutes
import com.nammameter.app.ui.minutesToHhMm
import com.nammameter.app.ui.tariffEffectiveText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Guards the tariff section while driver mode is on. */
    val gate = PinGate(container.settings)

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun selectPreset(id: String) = update { s ->
        // Switching to Custom starts from whatever tariff was active.
        if (id == FarePresets.CUSTOM_ID) s.copy(presetId = id, customFare = s.fareConfig) else s.copy(presetId = id)
    }

    /** Editing any fare field turns the active tariff into Custom. */
    fun editFare(transform: (FareConfig) -> FareConfig) = update { s ->
        val edited = transform(s.fareConfig)
        if (edited.isValid) s.copy(presetId = FarePresets.CUSTOM_ID, customFare = edited) else s
    }

    fun editMeter(transform: (MeterParams) -> MeterParams) = update { s ->
        val edited = transform(s.meter)
        if (edited.isValid) s.copy(meter = edited) else s
    }

    /** Only https templates with all three tile placeholders are accepted. */
    fun setTileUrl(url: String): Boolean {
        val trimmed = url.trim()
        val ok = trimmed.isEmpty() || isValidTileUrl(trimmed)
        if (ok) update { it.copy(tileUrl = trimmed) }
        return ok
    }

    companion object {
        // java.net.URI rejects the {z}/{x}/{y} braces, so check the shape directly.
        private val TILE_URL = Regex("^https://[A-Za-z0-9.-]+(:\\d{1,5})?/\\S+$")

        fun isValidTileUrl(url: String): Boolean =
            TILE_URL.matches(url) && listOf("{z}", "{x}", "{y}").all { it in url }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onDriverMode: () -> Unit, onAbout: () -> Unit, onPermissions: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val s = vm.settings.collectAsState().value

    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        if (s == null) return@Scaffold
        val fare = s.fareConfig
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        ) {
            // Language
            SectionTitle(stringResource(R.string.language))
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            listOf(
                "" to stringResource(R.string.language_system),
                "en" to "English",
                "ta" to "தமிழ்",
            ).forEach { (tag, label) ->
                RadioRow(label, selected = current.startsWith(tag) && (tag.isNotEmpty() || current.isEmpty())) {
                    AppCompatDelegate.setApplicationLocales(
                        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                    )
                }
            }

            // Tariff — read-only behind the driver PIN while driver mode is on.
            SectionTitle(stringResource(R.string.tariff))
            val tariffLocked = s.driver.enabled && s.driver.hasPin && !vm.gate.unlocked
            var showPin by remember { mutableStateOf(false) }
            if (tariffLocked) {
                val eff = tariffEffectiveText(s.activePreset?.effectiveDate?.toString())
                val name = s.activePreset?.name ?: stringResource(R.string.tariff_custom)
                Text(if (eff != null) stringResource(R.string.tariff_label, name, eff) else name, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.tariff_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showPin = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp)) {
                    Text(stringResource(R.string.tariff_unlock))
                }
                if (showPin) {
                    AlertDialog(
                        onDismissRequest = { showPin = false },
                        confirmButton = { TextButton(onClick = { showPin = false }) { Text(stringResource(R.string.cancel)) } },
                        text = { Column { EnterPin(gate = vm.gate) } },
                    )
                }
                LaunchedEffect(vm.gate.unlocked) { if (vm.gate.unlocked) showPin = false }
            }
            if (!tariffLocked) FarePresets.all.forEach { p ->
                val eff = tariffEffectiveText(p.effectiveDate?.toString())
                RadioRow(
                    p.name + (eff?.let { " · $it" } ?: ""),
                    selected = s.presetId == p.id,
                ) { vm.selectPreset(p.id) }
            }
            if (!tariffLocked) RadioRow(stringResource(R.string.tariff_custom), selected = s.activePreset == null) {
                vm.selectPreset(FarePresets.CUSTOM_ID)
            }
            if (!tariffLocked) Text(
                stringResource(R.string.tariff_edit_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val rupees: (Double) -> Boolean = { it in 0.0..FareConfig.MAX_RUPEES }
            if (!tariffLocked) TariffFields(fare, rupees, vm)

            // Meter / GPS
            SectionTitle(stringResource(R.string.meter_settings))
            MeterFields(s, vm)
            MapsAndOtherSections(s, vm, onDriverMode, onAbout, onPermissions)
        }
    }
}

@Composable
private fun TariffFields(fare: FareConfig, rupees: (Double) -> Boolean, vm: SettingsViewModel) {
            NumberField(stringResource(R.string.f_min_fare), fare.minFare, { v -> vm.editFare { it.copy(minFare = v) } }, valid = rupees)
            NumberField(
                stringResource(R.string.f_min_distance), fare.minDistanceKm,
                { v -> vm.editFare { it.copy(minDistanceKm = v) } }, valid = { it in 0.0..FareConfig.MAX_KM },
            )
            NumberField(stringResource(R.string.f_per_km), fare.perKmRate, { v -> vm.editFare { it.copy(perKmRate = v) } }, valid = rupees)
            NumberField(
                stringResource(R.string.f_step),
                fare.distanceStepMeters.toDouble(),
                { v -> vm.editFare { it.copy(distanceStepMeters = v.toInt()) } },
                integer = true, valid = { it in 1.0..100_000.0 },
            )
            NumberField(stringResource(R.string.f_waiting_rate), fare.waitingRate, { v -> vm.editFare { it.copy(waitingRate = v) } }, valid = rupees)
            NumberField(
                stringResource(R.string.f_waiting_block),
                fare.waitingBlockMinutes.toDouble(),
                { v -> vm.editFare { it.copy(waitingBlockMinutes = v.toInt()) } },
                integer = true, valid = { it in 1.0..1440.0 },
            )
            NumberField(
                stringResource(R.string.f_night_multiplier),
                fare.nightMultiplier,
                { v -> vm.editFare { it.copy(nightMultiplier = v) } },
                valid = { it in 1.0..FareConfig.MAX_MULTIPLIER },
            )
            Row {
                TimeField(stringResource(R.string.f_night_start), fare.nightStartMinutes, Modifier.weight(1f)) { m ->
                    vm.editFare { it.copy(nightStartMinutes = m) }
                }
                Spacer(Modifier.padding(4.dp))
                TimeField(stringResource(R.string.f_night_end), fare.nightEndMinutes, Modifier.weight(1f)) { m ->
                    vm.editFare { it.copy(nightEndMinutes = m) }
                }
            }
            SwitchRow(stringResource(R.string.f_night_waiting), fare.nightAppliesToWaiting, { v -> vm.editFare { it.copy(nightAppliesToWaiting = v) } })
            Text(stringResource(R.string.f_night_rule), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            listOf(
                NightRule.TRIP_START to R.string.night_rule_start,
                NightRule.TRIP_END to R.string.night_rule_end,
                NightRule.ANY_PART to R.string.night_rule_any,
            ).forEach { (rule, label) ->
                RadioRow(stringResource(label), fare.nightRule == rule) { vm.editFare { it.copy(nightRule = rule) } }
            }
            Text(stringResource(R.string.f_rounding), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            RadioRow(stringResource(R.string.rounding_nearest), fare.rounding == FareRounding.NEAREST) {
                vm.editFare { it.copy(rounding = FareRounding.NEAREST) }
            }
            RadioRow(stringResource(R.string.rounding_up), fare.rounding == FareRounding.UP) {
                vm.editFare { it.copy(rounding = FareRounding.UP) }
            }
}

@Composable
private fun MeterFields(s: AppSettings, vm: SettingsViewModel) {
            NumberField(
                stringResource(R.string.m_wait_speed), s.meter.autoWaitSpeedKmh,
                { v -> vm.editMeter { it.copy(autoWaitSpeedKmh = v) } }, valid = { it in 0.0..1_000.0 },
            )
            NumberField(
                stringResource(R.string.m_wait_after),
                s.meter.autoWaitAfterSeconds.toDouble(),
                { v -> vm.editMeter { it.copy(autoWaitAfterSeconds = v.toInt()) } },
                integer = true, valid = { it in 0.0..86_400.0 },
            )
            NumberField(
                stringResource(R.string.m_max_accuracy),
                s.meter.gps.maxAccuracyM.toDouble(),
                { v -> vm.editMeter { it.copy(gps = it.gps.copy(maxAccuracyM = v.toFloat())) } },
                valid = { it in 5.0..10_000.0 },
            )
            NumberField(
                stringResource(R.string.m_max_speed),
                s.meter.gps.maxSpeedKmh,
                { v -> vm.editMeter { it.copy(gps = it.gps.copy(maxSpeedKmh = v)) } },
                valid = { it in 20.0..1_000.0 },
            )
            NumberField(
                stringResource(R.string.m_min_segment),
                s.meter.gps.minSegmentM,
                { v -> vm.editMeter { it.copy(gps = it.gps.copy(minSegmentM = v)) } },
                valid = { it in 1.0..10_000.0 },
            )
            SwitchRow(stringResource(R.string.m_smoothing), s.meter.gps.smoothing, { v -> vm.editMeter { it.copy(gps = it.gps.copy(smoothing = v)) } })
            Text(stringResource(R.string.meter_settings_note), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MapsAndOtherSections(
    s: AppSettings,
    vm: SettingsViewModel,
    onDriverMode: () -> Unit,
    onAbout: () -> Unit,
    onPermissions: () -> Unit,
) {
            // Maps & routing
            SectionTitle(stringResource(R.string.maps_routing))
            NumberField(stringResource(R.string.road_factor), s.roadFactor, { v -> vm.update { it.copy(roadFactor = v) } }, valid = { it in 1.0..3.0 })
            Text(stringResource(R.string.routing_provider), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            listOf(
                RoutingProviderId.MANUAL to R.string.routing_manual,
                RoutingProviderId.HAVERSINE to R.string.routing_haversine,
                RoutingProviderId.OPENROUTESERVICE to R.string.routing_ors,
            ).forEach { (id, label) ->
                val enabled = id != RoutingProviderId.OPENROUTESERVICE || s.orsApiKey.isNotBlank()
                RadioRow(stringResource(label), s.routingProvider == id, enabled) { vm.update { it.copy(routingProvider = id) } }
            }
            TextSetting(stringResource(R.string.ors_key), s.orsApiKey, secret = true) { v ->
                vm.update {
                    it.copy(
                        orsApiKey = v.trim(),
                        routingProvider = if (v.isBlank() && it.routingProvider == RoutingProviderId.OPENROUTESERVICE) {
                            RoutingProviderId.MANUAL
                        } else {
                            it.routingProvider
                        },
                    )
                }
            }
            Text(stringResource(R.string.ors_note), style = MaterialTheme.typography.bodySmall)
            var tileUrlError by remember { mutableStateOf(false) }
            TextSetting(stringResource(R.string.tile_url), s.tileUrl, isError = tileUrlError) { v -> tileUrlError = !vm.setTileUrl(v) }
            if (tileUrlError) Text(stringResource(R.string.tile_url_invalid), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.tile_note), style = MaterialTheme.typography.bodySmall)

            // Other
            SectionTitle(stringResource(R.string.other))
            OutlinedButton(onClick = onDriverMode, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.driver_mode) + if (s.driver.enabled) " ✓" else "")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPermissions, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.location_permission))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onAbout, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.about_title))
            }
            Spacer(Modifier.height(24.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            label,
            Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeField(label: String, minutes: Int, modifier: Modifier, onValue: (Int) -> Unit) {
    val shown = minutesToHhMm(minutes)
    var text by remember(shown) { mutableStateOf(shown) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; hhMmToMinutes(t)?.let(onValue) },
        label = { Text(label) },
        isError = hhMmToMinutes(text) == null,
        singleLine = true,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun TextSetting(label: String, value: String, secret: Boolean = false, isError: Boolean = false, onValue: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onValue(it) },
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
