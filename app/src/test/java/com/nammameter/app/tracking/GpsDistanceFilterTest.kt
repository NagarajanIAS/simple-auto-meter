package com.nammameter.app.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GpsDistanceFilterTest {

    private val origin = LatLng(13.0827, 80.2707) // Chennai
    private val metersPerDegLat = 111_195.0

    private fun north(m: Double) = LatLng(origin.lat + m / metersPerDegLat, origin.lng)

    private fun fix(p: LatLng, t: Long, acc: Float = 5f, speed: Float? = null) =
        LocationFix(p.lat, p.lng, acc, t * 1000, speed)

    @Test
    fun `haversine matches known distance`() {
        // Chennai Central → Chennai Egmore is roughly 2.0 km straight line.
        val central = LatLng(13.0827, 80.2750)
        val egmore = LatLng(13.0780, 80.2595)
        val d = Geo.haversineMeters(central, egmore)
        assertTrue("d=$d", d in 1700.0..1800.0)
        assertEquals(1000.0, Geo.haversineMeters(origin, north(1000.0)), 1.0)
    }

    @Test
    fun `inaccurate fixes are discarded`() {
        val f = GpsDistanceFilter(GpsFilterParams(smoothing = false))
        f.process(fix(origin, 0))
        val r = f.process(fix(north(200.0), 20, acc = 40f))
        assertFalse(r.accepted)
        assertEquals(RejectReason.INACCURATE, r.rejectReason)
    }

    @Test
    fun `impossible jumps are discarded`() {
        val f = GpsDistanceFilter(GpsFilterParams(smoothing = false))
        f.process(fix(origin, 0))
        // 100 m in 1 s = 360 km/h
        val r = f.process(fix(north(100.0), 1))
        assertEquals(RejectReason.JUMP, r.rejectReason)
        // A sane fix afterwards is measured from the last good fix.
        val ok = f.process(fix(north(20.0), 2))
        assertTrue(ok.accepted)
        assertEquals(20.0, ok.addedMeters, 0.5)
    }

    @Test
    fun `persistent jumps reseed instead of rejecting forever`() {
        val f = GpsDistanceFilter(GpsFilterParams(smoothing = false))
        f.process(fix(LatLng(origin.lat + 1, origin.lng), 0)) // bad first fix ~111 km away
        var total = 0.0
        for (i in 1..10) total += f.process(fix(north(i * 10.0), i.toLong())).addedMeters
        assertTrue("should count the real movement after reseed, total=$total", total in 40.0..60.0)
    }

    @Test
    fun `stationary drift adds no distance`() {
        val f = GpsDistanceFilter()
        val rnd = Random(7)
        var total = 0.0
        for (t in 0L until 600L) {
            // ±4 m jitter with 8–15 m reported accuracy, for 10 minutes.
            val p = LatLng(
                origin.lat + (rnd.nextDouble() - 0.5) * 8 / metersPerDegLat,
                origin.lng + (rnd.nextDouble() - 0.5) * 8 / metersPerDegLat,
            )
            total += f.process(fix(p, t, acc = 8f + rnd.nextFloat() * 7f, speed = 0.3f)).addedMeters
        }
        assertEquals(0.0, total, 0.001)
    }

    @Test
    fun `straight drive is measured within 3 percent`() {
        val f = GpsDistanceFilter()
        val rnd = Random(3)
        var total = 0.0
        // 2 km at 8.33 m/s (30 km/h), one fix per second, ±3 m noise.
        val seconds = (2000 / 8.333).toInt()
        for (t in 0..seconds) {
            val along = t * 8.333
            val p = LatLng(
                origin.lat + (along + (rnd.nextDouble() - 0.5) * 6) / metersPerDegLat,
                origin.lng + (rnd.nextDouble() - 0.5) * 6 / metersPerDegLat,
            )
            total += f.process(fix(p, t.toLong(), acc = 6f, speed = 8.3f)).addedMeters
        }
        assertEquals(2000.0, total, 60.0)
    }

    @Test
    fun `small movements below threshold accumulate once threshold passed`() {
        val f = GpsDistanceFilter(GpsFilterParams(smoothing = false, minSegmentM = 5.0))
        f.process(fix(origin, 0, acc = 3f))
        assertEquals(0.0, f.process(fix(north(2.0), 1, acc = 3f)).addedMeters, 0.0)
        assertEquals(0.0, f.process(fix(north(4.0), 2, acc = 3f)).addedMeters, 0.0)
        assertEquals(6.0, f.process(fix(north(6.0), 3, acc = 3f)).addedMeters, 0.05)
    }

    @Test
    fun `threshold scales with accuracy`() {
        val f = GpsDistanceFilter(GpsFilterParams(smoothing = false))
        f.process(fix(origin, 0, acc = 20f))
        assertEquals(0.0, f.process(fix(north(15.0), 5, acc = 20f)).addedMeters, 0.0)
        assertEquals(25.0, f.process(fix(north(25.0), 10, acc = 20f)).addedMeters, 0.1)
    }

    @Test
    fun `out of order fixes are rejected`() {
        val f = GpsDistanceFilter()
        f.process(fix(origin, 10))
        assertEquals(RejectReason.OUT_OF_ORDER, f.process(fix(north(10.0), 10)).rejectReason)
    }
}
