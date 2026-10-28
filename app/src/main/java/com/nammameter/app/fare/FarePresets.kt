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

    val TN_2026 = FarePreset(
        id = "tn_2026",
        name = "TN 2026 notified tariff",
        effectiveDate = LocalDate.of(2026, 10, 27),
        source = "Govt. of Tamil Nadu Home Dept. notification under S.67 MV Act, 1988 " +
            "(Gazette 27 Oct 2026; supersedes notifications of 25 Aug 2013 and 16 Oct 2014)",
        config = FareConfig(
            minFare = 50.0,
            minDistanceKm = 1.8,
            perKmRate = 25.0,
            distanceStepMeters = 100,
            waitingRate = 1.0,
            waitingBlockMinutes = 1,
            nightMultiplier = 1.5,
            nightStartMinutes = 22 * 60,
            nightEndMinutes = 5 * 60,
            nightAppliesToWaiting = false,
            nightRule = NightRule.TRIP_START,
            rounding = FareRounding.NEAREST,
        ),
    )

    /** Same notification: ghat roads, minimum ₹60 (1.2 × regular) and ₹30/km. */
    val TN_2026_GHAT = FarePreset(
        id = "tn_2026_ghat",
        name = "TN 2026 notified tariff — ghat roads",
        effectiveDate = TN_2026.effectiveDate,
        source = TN_2026.source,
        config = TN_2026.config.copy(minFare = 60.0, perKmRate = 30.0),
    )

    val TN_2013 = FarePreset(
        id = "tn_2013",
        name = "TN 2013 tariff (superseded)",
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
    //     config = TN_2026.config.copy(minFare = …, perKmRate = …))

    /** Shown in Settings, newest first. Old tariffs stay so past trips and comparisons still make sense. */
    val all: List<FarePreset> = listOf(TN_2026, TN_2026_GHAT, TN_2013)

    val default: FarePreset = TN_2026

    fun byId(id: String): FarePreset? = all.firstOrNull { it.id == id }
}
