package com.nammameter.app.tracking

import com.nammameter.app.fare.FareConfig
import kotlinx.serialization.Serializable

/** Live trip state persisted every few seconds so a killed process can resume the trip. */
@Serializable
data class TripSnapshot(
    val startEpochMs: Long,
    val distanceMeters: Double,
    val movingMs: Long,
    val waitingMs: Long,
    val manualWait: Boolean,
    val tariffName: String,
    val tariffEffective: String?,
    /** Fare parameters are frozen at trip start. */
    val fareConfig: FareConfig,
    val savedAtEpochMs: Long,
)
