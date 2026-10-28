package com.nammameter.app.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripAccumulatorTest {

    private val origin = LatLng(13.0827, 80.2707)
    private fun north(m: Double) = LatLng(origin.lat + m / 111_195.0, origin.lng)
    private fun fix(p: LatLng, sec: Long, speedKmh: Double) =
        LocationFix(p.lat, p.lng, 5f, sec * 1000, (speedKmh / 3.6).toFloat())

    private val params = MeterParams(gps = GpsFilterParams(smoothing = false))

    @Test
    fun `stopped for more than 60 s starts accruing waiting`() {
        val acc = TripAccumulator(params, nowMs = 0)
        for (s in 0L..120L) {
            acc.onFix(fix(origin, s, speedKmh = 0.0))
            acc.tick(s * 1000)
        }
        assertTrue(acc.isAutoWaiting(120_000))
        // The first 60 s count as moving time, the remainder as waiting.
        assertEquals(60_000L, acc.movingMs)
        assertEquals(60_000L, acc.waitingMs)
    }

    @Test
    fun `moving resets the auto wait timer`() {
        val acc = TripAccumulator(params, nowMs = 0)
        for (s in 0L..50L) { acc.onFix(fix(origin, s, 0.0)); acc.tick(s * 1000) }
        acc.onFix(fix(north(10.0), 51, 30.0)); acc.tick(51_000)
        for (s in 52L..100L) { acc.onFix(fix(north(10.0), s, 0.0)); acc.tick(s * 1000) }
        assertFalse(acc.isAutoWaiting(100_000))
        assertEquals(0L, acc.waitingMs)
    }

    @Test
    fun `manual wait overrides`() {
        val acc = TripAccumulator(params, nowMs = 0)
        acc.manualWait = true
        acc.onFix(fix(origin, 0, 40.0))
        acc.tick(30_000)
        assertEquals(30_000L, acc.waitingMs)
        acc.manualWait = false
        acc.tick(40_000)
        assertEquals(10_000L, acc.movingMs)
    }

    @Test
    fun `restored state continues from snapshot`() {
        val acc = TripAccumulator(params, nowMs = 1_000, distanceMeters = 1234.0, movingMs = 5000, waitingMs = 7000)
        acc.tick(2_000)
        assertEquals(1234.0, acc.distanceMeters, 0.0)
        assertEquals(6000L, acc.movingMs)
        assertEquals(7000L, acc.waitingMs)
    }

    @Test
    fun `distance accumulates from accepted fixes`() {
        val acc = TripAccumulator(params, nowMs = 0)
        for (s in 0L..10L) acc.onFix(fix(north(s * 10.0), s, 36.0))
        assertEquals(100.0, acc.distanceMeters, 0.5)
    }
}
