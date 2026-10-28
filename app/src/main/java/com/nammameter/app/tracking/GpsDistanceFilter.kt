package com.nammameter.app.tracking

import kotlinx.serialization.Serializable
import kotlin.math.max

/** A single GPS fix, decoupled from android.location.Location so it can be unit-tested. */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    /** Monotonic timestamp (elapsed realtime) in milliseconds. */
    val timeMs: Long,
    /** Speed reported by the receiver, if any. */
    val speedMps: Float? = null,
) {
    val latLng: LatLng get() = LatLng(lat, lng)
}

@Serializable
data class GpsFilterParams(
    val maxAccuracyM: Float = 25f,
    val maxSpeedKmh: Double = 90.0,
    val minSegmentM: Double = 5.0,
    /** Segment threshold = max(minSegmentM, accuracyFactor × fix accuracy). */
    val accuracyFactor: Double = 1.0,
    val smoothing: Boolean = true,
)

enum class RejectReason { INACCURATE, OUT_OF_ORDER, JUMP }

data class FilterResult(
    val accepted: Boolean,
    val addedMeters: Double = 0.0,
    /** Position used for distance (smoothed when enabled). */
    val point: LatLng? = null,
    val speedMps: Double? = null,
    val rejectReason: RejectReason? = null,
)

/**
 * Turns noisy GPS fixes into a distance that does not creep while stationary.
 *
 * Distance is measured from an anchor point; the anchor only moves once the vehicle
 * has moved further than the noise threshold, so drift around a stopped auto adds nothing
 * while slow genuine movement is still counted once it exceeds the threshold.
 */
class GpsDistanceFilter(private val params: GpsFilterParams = GpsFilterParams()) {

    private var lastGood: LocationFix? = null
    private var anchor: LatLng? = null
    private var consecutiveJumps = 0
    private val smoother = PositionSmoother()

    fun process(fix: LocationFix): FilterResult {
        if (fix.accuracyM > params.maxAccuracyM) return reject(RejectReason.INACCURATE)

        val prev = lastGood
        var derivedSpeed: Double? = null
        if (prev != null) {
            val dtSec = (fix.timeMs - prev.timeMs) / 1000.0
            if (dtSec <= 0) return reject(RejectReason.OUT_OF_ORDER)
            derivedSpeed = Geo.haversineMeters(prev.latLng, fix.latLng) / dtSec
            if (derivedSpeed * 3.6 > params.maxSpeedKmh) {
                consecutiveJumps++
                if (consecutiveJumps < RESEED_AFTER_JUMPS) return reject(RejectReason.JUMP)
                // The earlier fix was probably the outlier: start again from here, counting nothing.
                reseed(fix)
                return FilterResult(accepted = true, point = anchor, speedMps = fix.speedMps?.toDouble())
            }
        }
        consecutiveJumps = 0
        lastGood = fix

        val point = if (params.smoothing) smoother.process(fix) else fix.latLng
        val speed = fix.speedMps?.toDouble() ?: derivedSpeed

        val from = anchor
        if (from == null) {
            anchor = point
            return FilterResult(accepted = true, point = point, speedMps = speed)
        }
        val segment = Geo.haversineMeters(from, point)
        val threshold = max(params.minSegmentM, params.accuracyFactor * fix.accuracyM)
        if (segment < threshold) return FilterResult(accepted = true, point = point, speedMps = speed)

        anchor = point
        return FilterResult(accepted = true, addedMeters = segment, point = point, speedMps = speed)
    }

    fun reset() {
        lastGood = null
        anchor = null
        consecutiveJumps = 0
        smoother.reset()
    }

    private fun reseed(fix: LocationFix) {
        reset()
        lastGood = fix
        anchor = if (params.smoothing) smoother.process(fix) else fix.latLng
    }

    private fun reject(reason: RejectReason) = FilterResult(accepted = false, rejectReason = reason)

    private companion object {
        const val RESEED_AFTER_JUMPS = 5
    }
}

/**
 * Minimal 1-D Kalman filter on latitude/longitude, weighting each fix by its reported accuracy.
 * Process noise [qMetresPerSecond] is the expected speed uncertainty.
 */
class PositionSmoother(private val qMetresPerSecond: Double = 3.0) {
    private var lat = 0.0
    private var lng = 0.0
    private var timeMs = 0L
    private var variance = -1.0

    fun process(fix: LocationFix): LatLng {
        val accuracy = max(fix.accuracyM.toDouble(), 1.0)
        if (variance < 0) {
            lat = fix.lat
            lng = fix.lng
            timeMs = fix.timeMs
            variance = accuracy * accuracy
        } else {
            val dt = fix.timeMs - timeMs
            if (dt > 0) {
                variance += dt * qMetresPerSecond * qMetresPerSecond / 1000.0
                timeMs = fix.timeMs
            }
            val k = variance / (variance + accuracy * accuracy)
            lat += k * (fix.lat - lat)
            lng += k * (fix.lng - lng)
            variance *= (1 - k)
        }
        return LatLng(lat, lng)
    }

    fun reset() {
        variance = -1.0
    }
}
