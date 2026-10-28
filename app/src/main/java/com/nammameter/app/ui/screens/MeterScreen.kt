package com.nammameter.app.ui.screens

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nammameter.app.AppContainer
import com.nammameter.app.R
import com.nammameter.app.data.AppSettings
import com.nammameter.app.fare.FareCalculator
import com.nammameter.app.tracking.LocationHelper
import com.nammameter.app.tracking.MeterService
import com.nammameter.app.tracking.TripSnapshot
import com.nammameter.app.ui.appViewModel
import com.nammameter.app.ui.formatDateTime
import com.nammameter.app.ui.formatDuration
import com.nammameter.app.ui.formatKm
import com.nammameter.app.ui.formatRupees
import com.nammameter.app.ui.tariffEffectiveText
import com.nammameter.app.ui.theme.FareTextStyle
import com.nammameter.app.ui.theme.GoGreen
import com.nammameter.app.ui.theme.StopRed
import com.nammameter.app.ui.theme.WaitAmber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeterViewModel(private val container: AppContainer) : ViewModel() {
    val live = container.liveMeter.state
    val finishedTripId = container.liveMeter.finishedTripId
    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** A trip interrupted by a process kill, offered for resumption while the meter is idle. */
    val interrupted: StateFlow<TripSnapshot?> =
        combine(container.liveTripStore.snapshot, container.liveMeter.state) { snap, meter ->
            if (meter.running) null else snap
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun consumeFinished() = container.liveMeter.consumeFinished()
    fun discardInterrupted() = viewModelScope.launch { container.liveTripStore.clear() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterScreen(
    onNeedPermission: () -> Unit,
    onTripFinished: (tripId: Long, showQr: Boolean) -> Unit,
    onEstimate: () -> Unit,
    onHistory: () -> Unit,
    onMap: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm = appViewModel { MeterViewModel(it) }
    val context = LocalContext.current
    val live by vm.live.collectAsState()
    val settings by vm.settings.collectAsState()
    val interrupted by vm.interrupted.collectAsState()
    val finishedId by vm.finishedTripId.collectAsState()
    var confirmStop by remember { mutableStateOf(false) }
    var needPrecise by remember { mutableStateOf(false) }

    LockPortrait()

    LaunchedEffect(finishedId) {
        finishedId?.let {
            vm.consumeFinished()
            onTripFinished(it, settings.driver.canShowQr)
        }
    }

    fun startOrAsk(resume: Boolean) {
        when {
            LocationHelper.hasFineLocation(context) ->
                if (resume) MeterService.resume(context) else MeterService.start(context)
            LocationHelper.hasAnyLocation(context) -> needPrecise = true
            else -> onNeedPermission()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = onEstimate) { Icon(Icons.Default.Calculate, stringResource(R.string.estimate_title)) }
                    IconButton(onClick = onMap) { Icon(Icons.Default.Map, stringResource(R.string.map_title)) }
                    IconButton(onClick = onHistory) { Icon(Icons.Default.History, stringResource(R.string.history_title)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings_title)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tariffName = if (live.running) live.tariffName else settings.activePreset?.name ?: stringResource(R.string.tariff_custom)
            val effective = tariffEffectiveText(if (live.running) live.tariffEffective else settings.activePreset?.effectiveDate?.toString())
            Text(
                if (effective != null) stringResource(R.string.tariff_label, tariffName, effective) else tariffName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val fare = live.fare ?: FareCalculator.calculate(settings.fareConfig, 0.0, 0, isNight = false)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.current_fare), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        formatRupees(fare.total),
                        style = FareTextStyle,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                    )
                    if (live.running && fare.isNight) {
                        Text(stringResource(R.string.night_rate_applied), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(stringResource(R.string.distance), formatKm(live.distanceMeters), Modifier.weight(1f))
                Tile(stringResource(R.string.moving_time), formatDuration(live.movingMs), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val waitStatus = when {
                    live.manualWait -> stringResource(R.string.waiting_manual)
                    live.autoWaiting -> stringResource(R.string.waiting_auto)
                    else -> null
                }
                Tile(stringResource(R.string.waiting_time), formatDuration(live.waitingMs), Modifier.weight(1f), waitStatus)
                Tile(
                    stringResource(R.string.gps),
                    when {
                        !live.running -> "—"
                        live.gpsOk -> stringResource(R.string.gps_ok)
                        else -> stringResource(R.string.gps_searching)
                    },
                    Modifier.weight(1f),
                    live.accuracyM?.takeIf { live.running }?.let { stringResource(R.string.gps_accuracy, it.toInt()) },
                )
            }

            Button(
                onClick = { MeterService.toggleWait(context) },
                enabled = live.running,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (live.manualWait) WaitAmber else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (live.manualWait) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(if (live.manualWait) stringResource(R.string.wait_on) else stringResource(R.string.wait), fontSize = 22.sp)
            }

            Button(
                onClick = { if (live.running) confirmStop = true else startOrAsk(resume = false) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (live.running) StopRed else GoGreen,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(96.dp),
            ) {
                Text(
                    if (live.running) stringResource(R.string.stop) else stringResource(R.string.start),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            Text(
                stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.stop_confirm_title)) },
            text = { Text(stringResource(R.string.stop_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = { confirmStop = false; MeterService.stop(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = StopRed, contentColor = Color.White),
                ) { Text(stringResource(R.string.stop)) }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (needPrecise) {
        AlertDialog(
            onDismissRequest = { needPrecise = false },
            title = { Text(stringResource(R.string.precise_needed_title)) },
            text = { Text(stringResource(R.string.precise_needed_text)) },
            confirmButton = { Button(onClick = { needPrecise = false; onNeedPermission() }) { Text(stringResource(R.string.ok)) } },
        )
    }

    interrupted?.let { snap ->
        if (!live.running) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.resume_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.resume_text,
                            formatDateTime(snap.startEpochMs),
                            formatKm(snap.distanceMeters),
                        ),
                    )
                },
                confirmButton = { Button(onClick = { startOrAsk(resume = true) }) { Text(stringResource(R.string.resume)) } },
                dismissButton = { OutlinedButton(onClick = { vm.discardInterrupted() }) { Text(stringResource(R.string.discard)) } },
            )
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun LockPortrait() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}
