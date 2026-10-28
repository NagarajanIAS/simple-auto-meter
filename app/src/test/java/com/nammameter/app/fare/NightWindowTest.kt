package com.nammameter.app.fare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class NightWindowTest {
    private val cfg = FarePresets.TN_2013.config

    @Test
    fun `window wraps past midnight with inclusive start and exclusive end`() {
        assertFalse(NightWindow.contains(cfg, LocalTime.of(21, 59)))
        assertTrue(NightWindow.contains(cfg, LocalTime.of(22, 0)))
        assertTrue(NightWindow.contains(cfg, LocalTime.of(0, 0)))
        assertTrue(NightWindow.contains(cfg, LocalTime.of(4, 59)))
        assertFalse(NightWindow.contains(cfg, LocalTime.of(5, 0)))
        assertFalse(NightWindow.contains(cfg, LocalTime.of(12, 0)))
    }

    @Test
    fun `non wrapping window`() {
        val c = cfg.copy(nightStartMinutes = 1 * 60, nightEndMinutes = 4 * 60)
        assertTrue(NightWindow.contains(c, LocalTime.of(2, 0)))
        assertFalse(NightWindow.contains(c, LocalTime.of(23, 0)))
    }

    @Test
    fun `equal start and end means no night window`() {
        val c = cfg.copy(nightStartMinutes = 0, nightEndMinutes = 0, nightRule = NightRule.ANY_PART)
        assertFalse(NightWindow.contains(c, LocalTime.MIDNIGHT))
        assertFalse(NightWindow.applies(c, LocalDateTime.of(2026, 1, 1, 21, 0), LocalDateTime.of(2026, 1, 2, 6, 0)))
    }

    @Test
    fun `any part detects a window fully inside a long trip`() {
        val c = cfg.copy(nightStartMinutes = 22 * 60, nightEndMinutes = 22 * 60 + 30, nightRule = NightRule.ANY_PART)
        val start = LocalDateTime.of(2026, 1, 1, 21, 50)
        val end = LocalDateTime.of(2026, 1, 1, 22, 40)
        assertTrue(NightWindow.applies(c, start, end))
        assertFalse(NightWindow.applies(c, start, start.plusMinutes(5)))
    }
}
