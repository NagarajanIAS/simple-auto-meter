package com.nammameter.app.fare

import java.time.LocalDateTime
import java.time.LocalTime

object NightWindow {

    /** True when [time] is inside [start, end); the window may wrap past midnight. */
    fun contains(config: FareConfig, time: LocalTime): Boolean {
        val m = time.hour * 60 + time.minute
        val s = config.nightStartMinutes
        val e = config.nightEndMinutes
        return when {
            s == e -> false
            s < e -> m in s until e
            else -> m >= s || m < e
        }
    }

    /** Whether the night multiplier applies to a trip, according to [FareConfig.nightRule]. */
    fun applies(config: FareConfig, start: LocalDateTime, end: LocalDateTime): Boolean =
        when (config.nightRule) {
            NightRule.TRIP_START -> contains(config, start.toLocalTime())
            NightRule.TRIP_END -> contains(config, end.toLocalTime())
            NightRule.ANY_PART -> overlaps(config, start, end)
        }

    private fun overlaps(config: FareConfig, start: LocalDateTime, end: LocalDateTime): Boolean {
        if (contains(config, start.toLocalTime()) || contains(config, end.toLocalTime())) return true
        if (config.nightStartMinutes == config.nightEndMinutes) return false
        // Neither endpoint is at night, so the trip overlaps only if a window opens inside it.
        val windowStart = LocalTime.of(config.nightStartMinutes / 60, config.nightStartMinutes % 60)
        var day = start.toLocalDate()
        while (!day.isAfter(end.toLocalDate())) {
            val opening = day.atTime(windowStart)
            if (!opening.isBefore(start) && !opening.isAfter(end)) return true
            day = day.plusDays(1)
        }
        return false
    }
}
