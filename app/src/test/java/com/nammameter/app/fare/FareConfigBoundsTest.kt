package com.nammameter.app.fare

import com.nammameter.app.tracking.GpsFilterParams
import com.nammameter.app.tracking.MeterParams
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FareConfigBoundsTest {
    private val base = FarePresets.default.config

    @Test
    fun `non finite and oversized values are invalid`() {
        assertFalse(base.copy(minFare = Double.POSITIVE_INFINITY).isValid)
        assertFalse(base.copy(minFare = Double.NaN).isValid)
        assertFalse(base.copy(minFare = 1e20).isValid)
        assertFalse(base.copy(perKmRate = 1e400).isValid)
        assertFalse(base.copy(minDistanceKm = 5_000.0).isValid)
        assertFalse(base.copy(nightMultiplier = 100.0).isValid)
        assertTrue(base.isValid)
        assertTrue(base.copy(minFare = FareConfig.MAX_RUPEES).isValid)
    }

    @Test
    fun `largest allowed fare still fits in paise`() {
        val f = FareCalculator.calculate(
            base.copy(minFare = FareConfig.MAX_RUPEES, perKmRate = FareConfig.MAX_RUPEES, waitingRate = FareConfig.MAX_RUPEES, waitingBlockMinutes = 1, nightMultiplier = FareConfig.MAX_MULTIPLIER),
            FareConfig.MAX_KM * 1000, 1440 * 60, isNight = true,
        )
        f.total.toPaise() // must not throw
    }

    @Test
    fun `meter params bounds`() {
        assertTrue(MeterParams().isValid)
        assertFalse(MeterParams(autoWaitSpeedKmh = Double.NaN).isValid)
        assertFalse(MeterParams(gps = GpsFilterParams(maxAccuracyM = Float.POSITIVE_INFINITY)).isValid)
    }
}
