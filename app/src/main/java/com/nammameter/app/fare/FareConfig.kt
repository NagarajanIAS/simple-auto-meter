package com.nammameter.app.fare

import kotlinx.serialization.Serializable

/** How the final amount is rounded to whole rupees. */
@Serializable
enum class FareRounding { NEAREST, UP }

/** Which moment(s) of the trip decide whether the night multiplier applies. */
@Serializable
enum class NightRule {
    /** Surcharge if the trip starts inside the night window (default). */
    TRIP_START,

    /** Surcharge if the trip ends inside the night window. */
    TRIP_END,

    /** Surcharge if any part of the trip overlaps the night window. */
    ANY_PART,
}

/**
 * Every fare parameter. Nothing about fares is hard-coded in the calculator;
 * presets in [FarePresets] are just named instances of this class.
 */
@Serializable
data class FareConfig(
    /** Fixed charge covering the first [minDistanceKm]. */
    val minFare: Double,
    val minDistanceKm: Double,
    /** Rate per km after the minimum distance. */
    val perKmRate: Double,
    /** Meter tick after the minimum distance; each completed step costs perKmRate × step / 1000. */
    val distanceStepMeters: Int = 100,
    /** Charge per completed waiting block. */
    val waitingRate: Double,
    val waitingBlockMinutes: Int = 5,
    val nightMultiplier: Double = 1.5,
    /** Minutes after midnight; the window may wrap past midnight (22:00 → 05:00). */
    val nightStartMinutes: Int = 22 * 60,
    val nightEndMinutes: Int = 5 * 60,
    val nightAppliesToWaiting: Boolean = false,
    val nightRule: NightRule = NightRule.TRIP_START,
    val rounding: FareRounding = FareRounding.NEAREST,
) {
    /** Returns a list of human-readable problems; empty when the config is usable. */
    fun problems(): List<String> = buildList {
        if (minFare !in 0.0..MAX_RUPEES) add("minFare must be 0–$MAX_RUPEES")
        if (minDistanceKm !in 0.0..MAX_KM) add("minDistanceKm must be 0–$MAX_KM")
        if (perKmRate !in 0.0..MAX_RUPEES) add("perKmRate must be 0–$MAX_RUPEES")
        if (distanceStepMeters !in 1..100_000) add("distanceStepMeters must be 1–100000")
        if (waitingRate !in 0.0..MAX_RUPEES) add("waitingRate must be 0–$MAX_RUPEES")
        if (waitingBlockMinutes !in 1..1440) add("waitingBlockMinutes must be 1–1440")
        if (nightMultiplier !in 1.0..MAX_MULTIPLIER) add("nightMultiplier must be 1–$MAX_MULTIPLIER")
        if (nightStartMinutes !in 0 until MINUTES_PER_DAY) add("nightStart out of range")
        if (nightEndMinutes !in 0 until MINUTES_PER_DAY) add("nightEnd out of range")
    }

    val isValid: Boolean get() = problems().isEmpty()

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        // Upper bounds keep every intermediate amount far inside Long paise and reject NaN/Infinity
        // (a range check on a NaN is false, so NaN fails too).
        const val MAX_RUPEES = 100_000.0
        const val MAX_KM = 1_000.0
        const val MAX_MULTIPLIER = 10.0
    }
}
