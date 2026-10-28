package com.nammameter.app.tracking

import com.nammameter.app.fare.FareBreakdown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveMeter(
    val running: Boolean = false,
    val startEpochMs: Long = 0,
    val distanceMeters: Double = 0.0,
    val movingMs: Long = 0,
    val waitingMs: Long = 0,
    val manualWait: Boolean = false,
    val autoWaiting: Boolean = false,
    val fare: FareBreakdown? = null,
    val tariffName: String = "",
    val tariffEffective: String? = null,
    /** False when there has been no usable GPS fix recently. */
    val gpsOk: Boolean = false,
    val accuracyM: Float? = null,
    val position: LatLng? = null,
    val track: List<LatLng> = emptyList(),
)

/** In-memory bridge between [MeterService] and the UI. */
class LiveMeterRepository {
    private val _state = MutableStateFlow(LiveMeter())
    val state: StateFlow<LiveMeter> = _state.asStateFlow()

    /** Id of a trip that just finished and whose summary the UI should show. */
    private val _finishedTripId = MutableStateFlow<Long?>(null)
    val finishedTripId: StateFlow<Long?> = _finishedTripId.asStateFlow()

    fun publish(meter: LiveMeter) {
        _state.value = meter
    }

    fun finished(tripId: Long) {
        _state.value = LiveMeter()
        _finishedTripId.value = tripId
    }

    fun consumeFinished() {
        _finishedTripId.value = null
    }
}
