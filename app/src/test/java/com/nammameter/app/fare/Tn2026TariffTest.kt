package com.nammameter.app.fare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** TN tariff notified 27 Oct 2026: ₹50 / 1.8 km, ₹25/km, ₹1/min waiting, night ×1.5 (22:00–05:00). */
class Tn2026TariffTest {
    private val cfg = FarePresets.TN_2026.config
    private val day = LocalDateTime.of(2026, 10, 28, 14, 0)
    private val night = LocalDateTime.of(2026, 10, 28, 23, 0)
    private fun bd(s: String) = BigDecimal(s)
    private fun fare(km: Double, waitMin: Long, start: LocalDateTime, c: FareConfig = cfg) =
        FareCalculator.calculate(c, km * 1000, waitMin * 60, start, start.plusMinutes(20))

    @Test
    fun `is the default preset`() {
        assertEquals(FarePresets.TN_2026, FarePresets.default)
        assertTrue(cfg.isValid)
    }

    @Test
    fun `5 km with 10 min waiting by day is 140`() {
        val f = fare(5.0, 10, day)
        assertEquals(bd("50.00"), f.minFare)
        assertEquals(bd("80.00"), f.extraDistanceCharge) // 3.2 km × 25
        assertEquals(10L, f.waitingBlocks)
        assertEquals(bd("10.00"), f.waitingCharge)
        assertEquals(bd("140.00"), f.subtotal)
        assertEquals(bd("140"), f.total)
    }

    @Test
    fun `5 km with 10 min waiting at night is 205`() {
        val f = fare(5.0, 10, night)
        assertEquals(bd("65.00"), f.nightSurcharge) // (50 + 80) × 0.5
        assertEquals(bd("205"), f.total)
    }

    @Test
    fun `minimum distance and steps`() {
        assertEquals(bd("50"), fare(1.8, 0, day).total)
        assertEquals(bd("52.50"), fare(1.9, 0, day).subtotal) // one 100 m step = ₹2.50
        assertEquals(bd("53"), fare(1.9, 0, day).total)
    }

    @Test
    fun `waiting is charged per completed minute`() {
        val f = FareCalculator.calculate(cfg, 1000.0, waitingSeconds = 4 * 60 + 59, isNight = false)
        assertEquals(4L, f.waitingBlocks)
        assertEquals(bd("54"), f.total)
    }

    @Test
    fun `ghat road preset`() {
        val f = fare(5.0, 0, day, FarePresets.TN_2026_GHAT.config)
        assertEquals(bd("156"), f.total) // 60 + 3.2 × 30
    }
}
