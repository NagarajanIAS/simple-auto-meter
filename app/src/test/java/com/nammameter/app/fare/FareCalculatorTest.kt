package com.nammameter.app.fare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class FareCalculatorTest {

    private val tn2013 = FarePresets.TN_2013.config
    private val day = LocalDateTime.of(2026, 10, 28, 14, 0)
    private val night = LocalDateTime.of(2026, 10, 28, 23, 0)

    private fun bd(s: String) = BigDecimal(s)

    private fun fare(
        km: Double,
        waitMin: Long = 0,
        start: LocalDateTime = day,
        end: LocalDateTime = start.plusMinutes(20),
        config: FareConfig = tn2013,
    ) = FareCalculator.calculate(config, km * 1000, waitMin * 60, start, end)

    @Test
    fun `5 km with 10 min waiting by day is 70`() {
        val f = fare(5.0, waitMin = 10)
        assertEquals(bd("25.00"), f.minFare)
        assertEquals(bd("3.200"), f.chargedExtraKm)
        assertEquals(bd("38.40"), f.extraDistanceCharge)
        assertEquals(2L, f.waitingBlocks)
        assertEquals(bd("7.00"), f.waitingCharge)
        assertFalse(f.isNight)
        assertEquals(bd("0.00"), f.nightSurcharge)
        assertEquals(bd("70.40"), f.subtotal)
        assertEquals(bd("70"), f.total)
    }

    @Test
    fun `5 km with 10 min waiting at night multiplies distance only, 102`() {
        val f = fare(5.0, waitMin = 10, start = night)
        assertTrue(f.isNight)
        // (25 + 38.40) × 0.5 = 31.70 surcharge; waiting not multiplied.
        assertEquals(bd("31.70"), f.nightSurcharge)
        assertEquals(bd("102.10"), f.subtotal)
        assertEquals(bd("102"), f.total)
    }

    @Test
    fun `night multiplier on waiting when enabled`() {
        val f = fare(5.0, waitMin = 10, start = night, config = tn2013.copy(nightAppliesToWaiting = true))
        assertEquals(bd("35.20"), f.nightSurcharge) // (63.40 + 7.00) × 0.5
        assertEquals(bd("105.60"), f.subtotal)
        assertEquals(bd("106"), f.total)
    }

    @Test
    fun `trip shorter than minimum distance pays minimum fare`() {
        listOf(0.0, 0.3, 1.0, 1.79).forEach { km ->
            val f = fare(km)
            assertEquals("km=$km", bd("0.00"), f.extraDistanceCharge)
            assertEquals("km=$km", bd("25"), f.total)
        }
    }

    @Test
    fun `exactly the minimum distance pays minimum fare`() {
        val f = fare(1.8)
        assertEquals(bd("0.000"), f.chargedExtraKm)
        assertEquals(bd("25.00"), f.subtotal)
        assertEquals(bd("25"), f.total)
    }

    @Test
    fun `partial distance step is not charged`() {
        // 1.899 km → 99 m extra → no completed 100 m step.
        assertEquals(bd("25.00"), fare(1.899).subtotal)
        // 1.9 km → one step of 100 m = ₹1.20.
        assertEquals(bd("26.20"), fare(1.9).subtotal)
        assertEquals(bd("26"), fare(1.9).total)
    }

    @Test
    fun `step size is configurable`() {
        val cfg = tn2013.copy(distanceStepMeters = 1000)
        assertEquals(bd("25.00"), fare(2.7, config = cfg).subtotal) // 900 m extra, no full km
        assertEquals(bd("37.00"), fare(2.8, config = cfg).subtotal)
    }

    @Test
    fun `only completed waiting blocks are charged`() {
        val f = FareCalculator.calculate(tn2013, 1000.0, waitingSeconds = 4 * 60 + 59, isNight = false)
        assertEquals(0L, f.waitingBlocks)
        val g = FareCalculator.calculate(tn2013, 1000.0, waitingSeconds = 14 * 60 + 59, isNight = false)
        assertEquals(2L, g.waitingBlocks)
        assertEquals(bd("32.00"), g.subtotal)
    }

    @Test
    fun `rounding nearest and up`() {
        // 2.2 km → 4 steps × 1.20 = 4.80 → 29.80
        assertEquals(bd("30"), fare(2.2).total)
        // 2.0 km → 2.40 → 27.40
        assertEquals(bd("27"), fare(2.0).total)
        assertEquals(bd("28"), fare(2.0, config = tn2013.copy(rounding = FareRounding.UP)).total)
        // .50 rounds half up
        val half = FareCalculator.calculate(tn2013.copy(perKmRate = 5.0), 1900.0, 0, isNight = false)
        assertEquals(bd("25.50"), half.subtotal)
        assertEquals(bd("26"), half.total)
    }

    @Test
    fun `round up leaves whole rupees alone`() {
        assertEquals(bd("25"), fare(1.0, config = tn2013.copy(rounding = FareRounding.UP)).total)
    }

    @Test
    fun `trip starting at night and ending in day is night under TRIP_START`() {
        val start = LocalDateTime.of(2026, 10, 28, 4, 50)
        val end = LocalDateTime.of(2026, 10, 28, 5, 20)
        assertTrue(fare(5.0, start = start, end = end).isNight)
        assertFalse(fare(5.0, start = start, end = end, config = tn2013.copy(nightRule = NightRule.TRIP_END)).isNight)
    }

    @Test
    fun `trip starting in day and ending at night is day under TRIP_START`() {
        val start = LocalDateTime.of(2026, 10, 28, 21, 45)
        val end = LocalDateTime.of(2026, 10, 28, 22, 15)
        val f = fare(5.0, waitMin = 10, start = start, end = end)
        assertFalse(f.isNight)
        assertEquals(bd("70"), f.total)
        assertTrue(fare(5.0, start = start, end = end, config = tn2013.copy(nightRule = NightRule.TRIP_END)).isNight)
        assertTrue(fare(5.0, start = start, end = end, config = tn2013.copy(nightRule = NightRule.ANY_PART)).isNight)
    }

    @Test
    fun `trip crossing midnight inside night window`() {
        val start = LocalDateTime.of(2026, 10, 28, 23, 50)
        val end = LocalDateTime.of(2026, 10, 29, 0, 20)
        assertTrue(fare(5.0, start = start, end = end).isNight)
    }

    @Test
    fun `negative inputs are treated as zero`() {
        val f = FareCalculator.calculate(tn2013, -50.0, -10, isNight = false)
        assertEquals(bd("25"), f.total)
    }

    @Test
    fun `all components add up to subtotal`() {
        for (km in listOf(0.5, 1.8, 3.33, 7.77, 12.0, 25.4)) for (w in listOf(0L, 4L, 5L, 23L)) for (n in listOf(false, true)) {
            val f = FareCalculator.calculate(tn2013, km * 1000, w * 60, n)
            assertEquals(
                f.subtotal,
                f.minFare.add(f.extraDistanceCharge).add(f.waitingCharge).add(f.nightSurcharge),
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid config is rejected`() {
        FareCalculator.calculate(tn2013.copy(distanceStepMeters = 0), 1000.0, 0, isNight = false)
    }

    @Test
    fun `paise conversion round trips`() {
        assertEquals(7040L, bd("70.40").toPaise())
        assertEquals(bd("70.40"), paiseToRupees(7040))
    }
}
