package com.nammameter.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nammameter.app.AppContainer
import com.nammameter.app.R
import com.nammameter.app.data.AppSettings
import com.nammameter.app.data.TripEntity
import com.nammameter.app.fare.paiseToRupees
import com.nammameter.app.ui.appViewModel
import com.nammameter.app.ui.components.BackTopBar
import com.nammameter.app.ui.components.KeyValueRow
import com.nammameter.app.ui.formatDate
import com.nammameter.app.ui.formatDateTime
import com.nammameter.app.ui.formatDuration
import com.nammameter.app.ui.formatKm
import com.nammameter.app.ui.formatRupees
import com.nammameter.app.ui.formatRupeesExact
import com.nammameter.app.ui.tariffEffectiveText
import com.nammameter.app.ui.theme.FareTextStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ---------- Summary ----------

class SummaryViewModel(private val container: AppContainer, tripId: Long) : ViewModel() {
    val trip: StateFlow<TripEntity?> = container.trips.observe(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun delete(id: Long, then: () -> Unit) = viewModelScope.launch {
        container.trips.delete(id)
        then()
    }
}

@Composable
fun SummaryScreen(tripId: Long, onBack: () -> Unit, onShowQr: () -> Unit) {
    val vm = appViewModel(key = "summary-$tripId") { SummaryViewModel(it, tripId) }
    val trip by vm.trip.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopBar(stringResource(R.string.summary_title), onBack) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
            }
        },
    ) { padding ->
        val t = trip ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.total_payable), style = MaterialTheme.typography.titleMedium)
            Text(formatRupees(t.total), style = FareTextStyle)
            Spacer(Modifier.height(8.dp))
            Text(formatDateTime(t.startEpochMs) + " → " + formatDateTime(t.endEpochMs))
            Text(
                tariffEffectiveText(t.tariffEffective)?.let { stringResource(R.string.tariff_label, t.tariffName, it) } ?: t.tariffName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            KeyValueRow(stringResource(R.string.distance), formatKm(t.distanceMeters))
            KeyValueRow(stringResource(R.string.moving_time), formatDuration(t.movingSeconds * 1000))
            KeyValueRow(stringResource(R.string.waiting_time), formatDuration(t.waitingSeconds * 1000))
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            KeyValueRow(stringResource(R.string.bd_min_fare), formatRupeesExact(paiseToRupees(t.minFarePaise)))
            KeyValueRow(
                stringResource(R.string.bd_extra_distance, String.format(java.util.Locale.ROOT, "%.1f", t.chargedExtraKm)),
                formatRupeesExact(paiseToRupees(t.extraDistancePaise)),
            )
            KeyValueRow(
                stringResource(R.string.bd_waiting, t.waitingBlocks.toInt()),
                formatRupeesExact(paiseToRupees(t.waitingPaise)),
            )
            KeyValueRow(
                if (t.isNight) stringResource(R.string.bd_night) else stringResource(R.string.bd_night_none),
                formatRupeesExact(paiseToRupees(t.nightSurchargePaise)),
            )
            KeyValueRow(stringResource(R.string.bd_subtotal), formatRupeesExact(paiseToRupees(t.subtotalPaise)))
            KeyValueRow(stringResource(R.string.bd_total), formatRupees(t.total), emphasize = true)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { shareTrip(context, t) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.Share, null)
                Text("  " + stringResource(R.string.share))
            }
            if (settings.driver.canShowQr) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onShowQr, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Default.QrCode2, null)
                    Text("  " + stringResource(R.string.show_upi_qr))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_trip_title)) },
            confirmButton = {
                Button(onClick = { confirmDelete = false; vm.delete(tripId, onBack) }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun shareTrip(context: Context, t: TripEntity) {
    val r = context.resources
    val text = buildString {
        appendLine(r.getString(R.string.share_header))
        appendLine(formatDateTime(t.startEpochMs) + " → " + formatDateTime(t.endEpochMs))
        appendLine(r.getString(R.string.distance) + ": " + formatKm(t.distanceMeters))
        appendLine(r.getString(R.string.waiting_time) + ": " + formatDuration(t.waitingSeconds * 1000))
        appendLine(r.getString(R.string.bd_min_fare) + ": " + formatRupeesExact(paiseToRupees(t.minFarePaise)))
        appendLine(
            r.getString(R.string.bd_extra_distance, String.format(java.util.Locale.ROOT, "%.1f", t.chargedExtraKm)) +
                ": " + formatRupeesExact(paiseToRupees(t.extraDistancePaise)),
        )
        appendLine(r.getString(R.string.bd_waiting, t.waitingBlocks.toInt()) + ": " + formatRupeesExact(paiseToRupees(t.waitingPaise)))
        if (t.isNight) appendLine(r.getString(R.string.bd_night) + ": " + formatRupeesExact(paiseToRupees(t.nightSurchargePaise)))
        appendLine(r.getString(R.string.bd_total) + ": " + formatRupees(t.total))
        appendLine(r.getString(R.string.tariff) + ": " + t.tariffName)
        append(r.getString(R.string.disclaimer_short))
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, r.getString(R.string.share)))
}

// ---------- History ----------

class HistoryViewModel(container: AppContainer) : ViewModel() {
    val trips: StateFlow<List<TripEntity>> = container.trips.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val driverMode: StateFlow<Boolean> = container.settings.settings.map { it.driver.enabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, onEarnings: () -> Unit) {
    val vm = appViewModel { HistoryViewModel(it) }
    val trips by vm.trips.collectAsState()
    val driverMode by vm.driverMode.collectAsState()
    Scaffold(
        topBar = {
            BackTopBar(stringResource(R.string.history_title), onBack) {
                if (driverMode) IconButton(onClick = onEarnings) { Icon(Icons.Default.Payments, stringResource(R.string.earnings_title)) }
            }
        },
    ) { padding ->
        if (trips.isEmpty()) {
            Text(stringResource(R.string.history_empty), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(trips, key = { it.id }) { t ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onOpen(t.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatDateTime(t.startEpochMs), fontWeight = FontWeight.Bold)
                            Text(
                                formatKm(t.distanceMeters) + " · " + formatDuration((t.movingSeconds + t.waitingSeconds) * 1000) +
                                    if (t.isNight) " · " + stringResource(R.string.night) else "",
                            )
                        }
                        Text(formatRupees(t.total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------- Earnings (driver mode, local only) ----------

data class DayEarnings(val date: LocalDate, val trips: Int, val distanceMeters: Double, val total: BigDecimal)

class EarningsViewModel(container: AppContainer) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val since = LocalDate.now().minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()

    val days: StateFlow<List<DayEarnings>> = container.trips.observeSince(since).map { trips ->
        trips.groupBy { Instant.ofEpochMilli(it.endEpochMs).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                DayEarnings(
                    date = date,
                    trips = list.size,
                    distanceMeters = list.sumOf { it.distanceMeters },
                    total = paiseToRupees(list.sumOf { it.totalPaise }),
                )
            }
            .sortedByDescending { it.date }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun EarningsScreen(onBack: () -> Unit) {
    val vm = appViewModel { EarningsViewModel(it) }
    val days by vm.days.collectAsState()
    val today = LocalDate.now()
    Scaffold(topBar = { BackTopBar(stringResource(R.string.earnings_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            val todays = days.firstOrNull { it.date == today }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.earnings_today), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        formatRupees(todays?.total ?: BigDecimal.ZERO),
                        style = FareTextStyle,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        pluralStringResource(R.plurals.earnings_trips, todays?.trips ?: 0, todays?.trips ?: 0) + " · " + formatKm(todays?.distanceMeters ?: 0.0),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.earnings_last_30), style = MaterialTheme.typography.titleMedium)
            days.forEach { d ->
                KeyValueRow(
                    formatDate(d.date) + " (" + pluralStringResource(R.plurals.earnings_trips, d.trips, d.trips) + ")",
                    formatRupees(d.total),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.earnings_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
