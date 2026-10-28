package com.nammameter.app.fare

import java.time.LocalDate

/**
 * A named, government-notified tariff.
 *
 * To add a revised tariff: add one `val` below and list it in [FarePresets.all].
 * Nothing else in the app needs to change.
 */
data class FarePreset(
    val id: String,
    val name: String,
    val effectiveDate: LocalDate?,
    /** Where the numbers come from (G.O. / notification reference). */
    val source: String,
    val config: FareConfig,
)

object FarePresets {
    const val CUSTOM_ID = "custom"

    val TN_2013 = FarePreset(
        id = "tn_2013",
        name = "TN 2013 notified tariff",
        effectiveDate = LocalDate.of(2013, 8, 25),
        source = "Government of Tamil Nadu auto-rickshaw fare revision, 2013",
        config = FareConfig(
            minFare = 25.0,
            minDistanceKm = 1.8,
            perKmRate = 12.0,
            distanceStepMeters = 100,
            waitingRate = 3.50,
            waitingBlockMinutes = 5,
            nightMultiplier = 1.5,
            nightStartMinutes = 22 * 60,
            nightEndMinutes = 5 * 60,
            nightAppliesToWaiting = false,
            nightRule = NightRule.TRIP_START,
            rounding = FareRounding.NEAREST,
        ),
    )

    // Example for the next notification:
    // val TN_20XX = FarePreset(id = "tn_20xx", name = "TN 20XX notified tariff",
    //     effectiveDate = LocalDate.of(20XX, M, D), source = "G.O. (Ms) No. …",
    //     config = TN_2013.config.copy(minFare = …, perKmRate = …))

    /** Shown in Settings, newest first. */
    val all: List<FarePreset> = listOf(TN_2013)

    val default: FarePreset = TN_2013

    fun byId(id: String): FarePreset? = all.firstOrNull { it.id == id }
}
