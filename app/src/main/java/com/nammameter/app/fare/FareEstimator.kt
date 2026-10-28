package com.nammameter.app.fare

import java.math.BigDecimal

data class FareRange(val low: BigDecimal, val high: BigDecimal)

data class FareEstimate(val day: FareRange, val night: FareRange)

/** Pre-trip estimate; the high end allows for a route [variance] longer than entered. */
object FareEstimator {
    const val DEFAULT_VARIANCE = 0.10

    fun estimate(config: FareConfig, km: Double, waitingMinutes: Double, variance: Double = DEFAULT_VARIANCE): FareEstimate {
        val meters = km.coerceAtLeast(0.0) * 1000
        val waitSec = (waitingMinutes.coerceAtLeast(0.0) * 60).toLong()
        fun range(night: Boolean) = FareRange(
            low = FareCalculator.calculate(config, meters, waitSec, night).total,
            high = FareCalculator.calculate(config, meters * (1 + variance), waitSec, night).total,
        )
        return FareEstimate(day = range(false), night = range(true))
    }
}
