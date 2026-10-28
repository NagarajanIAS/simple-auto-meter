package com.nammameter.app.routing

import com.nammameter.app.tracking.Geo
import com.nammameter.app.tracking.LatLng

enum class RoutingProviderId { MANUAL, HAVERSINE, OPENROUTESERVICE }

data class RouteRequest(
    val from: LatLng? = null,
    val to: LatLng? = null,
    /** Distance typed by the user (e.g. read from Google Maps). */
    val manualKm: Double? = null,
)

data class RouteEstimate(
    val distanceKm: Double,
    /** True for straight-line based guesses; the UI labels these "approximate". */
    val approximate: Boolean,
    val providerId: RoutingProviderId,
)

/** Source of a trip distance for the fare estimator. */
interface RoutingProvider {
    val id: RoutingProviderId

    /** False when the provider cannot be used (e.g. no API key). */
    val isAvailable: Boolean get() = true

    suspend fun route(request: RouteRequest): Result<RouteEstimate>
}

/** Default: the user types the km they read from Google Maps. */
class ManualEntryProvider : RoutingProvider {
    override val id = RoutingProviderId.MANUAL

    override suspend fun route(request: RouteRequest): Result<RouteEstimate> {
        val km = request.manualKm ?: return Result.failure(IllegalArgumentException("Enter a distance"))
        if (km < 0 || km.isNaN()) return Result.failure(IllegalArgumentException("Invalid distance"))
        return Result.success(RouteEstimate(km, approximate = false, providerId = id))
    }
}

/** Offline: straight-line distance × road factor. Always approximate. */
class HaversineProvider(private val roadFactor: Double) : RoutingProvider {
    override val id = RoutingProviderId.HAVERSINE

    override suspend fun route(request: RouteRequest): Result<RouteEstimate> {
        val from = request.from ?: return Result.failure(IllegalArgumentException("Start point unknown"))
        val to = request.to ?: return Result.failure(IllegalArgumentException("Destination not chosen"))
        val km = Geo.haversineMeters(from, to) / 1000.0 * roadFactor
        return Result.success(RouteEstimate(km, approximate = true, providerId = id))
    }
}

object RoutingProviders {
    fun create(id: RoutingProviderId, roadFactor: Double, orsApiKey: String): RoutingProvider = when (id) {
        RoutingProviderId.MANUAL -> ManualEntryProvider()
        RoutingProviderId.HAVERSINE -> HaversineProvider(roadFactor)
        RoutingProviderId.OPENROUTESERVICE -> OpenRouteServiceProvider(orsApiKey)
    }

    /** Provider used when the user picks a destination on the map. */
    fun forMapPick(id: RoutingProviderId, roadFactor: Double, orsApiKey: String): RoutingProvider {
        val ors = OpenRouteServiceProvider(orsApiKey)
        return if (id == RoutingProviderId.OPENROUTESERVICE && ors.isAvailable) ors else HaversineProvider(roadFactor)
    }
}
