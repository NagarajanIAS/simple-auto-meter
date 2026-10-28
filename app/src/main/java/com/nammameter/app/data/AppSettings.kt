package com.nammameter.app.data

import com.nammameter.app.fare.FareConfig
import com.nammameter.app.fare.FarePreset
import com.nammameter.app.fare.FarePresets
import com.nammameter.app.routing.RoutingProviderId
import com.nammameter.app.tracking.MeterParams
import kotlinx.serialization.Serializable

@Serializable
data class DriverSettings(
    val enabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val vpa: String = "",
    val payeeName: String = "",
) {
    val hasPin: Boolean get() = pinHash.isNotEmpty()
    val canShowQr: Boolean get() = enabled && vpa.isNotEmpty()
}

data class AppSettings(
    val presetId: String = FarePresets.default.id,
    /** Values used when [presetId] is [FarePresets.CUSTOM_ID]. */
    val customFare: FareConfig = FarePresets.default.config,
    val meter: MeterParams = MeterParams(),
    val roadFactor: Double = DEFAULT_ROAD_FACTOR,
    val routingProvider: RoutingProviderId = RoutingProviderId.MANUAL,
    val orsApiKey: String = "",
    val tileUrl: String = DEFAULT_TILE_URL,
    val driver: DriverSettings = DriverSettings(),
    val disclaimerAccepted: Boolean = false,
) {
    /** Null when the custom tariff is active. */
    val activePreset: FarePreset? get() = FarePresets.byId(presetId)

    val fareConfig: FareConfig get() = activePreset?.config ?: customFare

    companion object {
        const val DEFAULT_ROAD_FACTOR = 1.35
        const val DEFAULT_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    }
}
