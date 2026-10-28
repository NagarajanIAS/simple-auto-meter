package com.nammameter.app.routing

import com.nammameter.app.tracking.LatLng
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingTest {
    private val a = LatLng(13.0827, 80.2707)
    private val b = LatLng(13.0827 + 1000 / 111_195.0, 80.2707)

    @Test
    fun `haversine provider applies road factor and is approximate`() = runTest {
        val r = HaversineProvider(1.35).route(RouteRequest(a, b)).getOrThrow()
        assertEquals(1.35, r.distanceKm, 0.01)
        assertTrue(r.approximate)
    }

    @Test
    fun `manual provider returns typed km`() = runTest {
        val r = ManualEntryProvider().route(RouteRequest(manualKm = 4.2)).getOrThrow()
        assertEquals(4.2, r.distanceKm, 0.0)
        assertFalse(r.approximate)
        assertTrue(ManualEntryProvider().route(RouteRequest()).isFailure)
    }

    @Test
    fun `ors is unavailable without key and map pick falls back to haversine`() = runTest {
        assertFalse(OpenRouteServiceProvider("").isAvailable)
        assertTrue(OpenRouteServiceProvider("").route(RouteRequest(a, b)).isFailure)
        val p = RoutingProviders.forMapPick(RoutingProviderId.OPENROUTESERVICE, 1.35, "")
        assertEquals(RoutingProviderId.HAVERSINE, p.id)
    }

    @Test
    fun `parses ors response`() {
        val json = """{"routes":[{"summary":{"distance":5234.7,"duration":900.1}}]}"""
        assertEquals(5234.7, OpenRouteServiceProvider.parseDistanceMeters(json), 0.0)
    }

    @Test
    fun `google maps urls`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=13.082700,80.270700",
            MapsIntents.searchUrl(a),
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=T.+Nagar%2C+Chennai&travelmode=driving",
            MapsIntents.directionsUrl("T. Nagar, Chennai"),
        )
    }
}
