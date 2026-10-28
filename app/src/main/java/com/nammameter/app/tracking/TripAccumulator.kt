package com.nammameter.app.tracking

import kotlinx.serialization.Serializable

@Serializable
data class MeterParams(
    /** Below this speed the auto is considered stopped. */
    val autoWaitSpeedKmh: Double = 5.0,
    /** Waiting accrues once the auto has been stopped continuously for longer than this. */
    val autoWaitAfterSeconds: Int = 60,
    val gps: GpsFilterParams = GpsFilterParams(),
) {
    val isValid: Boolean
        get() = autoWaitSpeedKmh in 0.0..1_000.0 && autoWaitAfterSeconds in 0..86_400 && gps.isValid
}

/**
 * Pure trip state machine: distance from the GPS filter, and moving vs waiting time.
 * All times are monotonic milliseconds supplied by the caller.
 */
class TripAccumulator(
    private val params: MeterParams,
    nowMs: Long,
    distanceMeters: Double = 0.0,
    movingMs: Long = 0,
    waitingMs: Long = 0,
    manualWait: Boolean = false,
) {
    private val filter = GpsDistanceFilter(params.gps)
    private var lastTickMs = nowMs
    private var slowSinceMs: Long? = null

    var distanceMeters = distanceMeters
        private set
    var movingMs = movingMs
        private set
    var waitingMs = waitingMs
        private set
    var manualWait = manualWait

    /** Last position accepted by the filter. */
    var lastPoint: LatLng? = null
        private set
    var lastAccuracyM: Float? = null
        private set
    var lastFixMs: Long? = null
        private set

    fun onFix(fix: LocationFix): FilterResult {
        val result = filter.process(fix)
        lastAccuracyM = fix.accuracyM
        if (!result.accepted) return result
        lastFixMs = fix.timeMs
        distanceMeters += result.addedMeters
        result.point?.let { lastPoint = it }
        result.speedMps?.let { speed ->
            if (speed * 3.6 < params.autoWaitSpeedKmh) {
                if (slowSinceMs == null) slowSinceMs = fix.timeMs
            } else {
                slowSinceMs = null
            }
        }
        return result
    }

    fun isAutoWaiting(nowMs: Long): Boolean {
        val since = slowSinceMs ?: return false
        return nowMs - since > params.autoWaitAfterSeconds * 1000L
    }

    /** Manual WAIT overrides auto detection. */
    fun isWaiting(nowMs: Long): Boolean = manualWait || isAutoWaiting(nowMs)

    /** Attributes the time since the previous tick to moving or waiting. */
    fun tick(nowMs: Long) {
        val dt = nowMs - lastTickMs
        if (dt <= 0) return
        if (isWaiting(nowMs)) waitingMs += dt else movingMs += dt
        lastTickMs = nowMs
    }
}
