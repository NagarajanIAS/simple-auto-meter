package com.nammameter.app.fare

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

/** Itemised fare. Money values are exact rupees (scale 2) except [total], which is rounded. */
data class FareBreakdown(
    val minFare: BigDecimal,
    /** Distance actually charged beyond the minimum, in whole meter steps. */
    val chargedExtraKm: BigDecimal,
    val extraDistanceCharge: BigDecimal,
    val waitingBlocks: Long,
    val waitingCharge: BigDecimal,
    val isNight: Boolean,
    val nightSurcharge: BigDecimal,
    /** Sum of all components before rounding. */
    val subtotal: BigDecimal,
    /** Amount payable, in whole rupees. */
    val total: BigDecimal,
)

object FareCalculator {

    private val THOUSAND = BigDecimal(1000)

    fun calculate(
        config: FareConfig,
        distanceMeters: Double,
        waitingSeconds: Long,
        start: LocalDateTime,
        end: LocalDateTime,
    ): FareBreakdown = calculate(
        config, distanceMeters, waitingSeconds, NightWindow.applies(config, start, end),
    )

    fun calculate(
        config: FareConfig,
        distanceMeters: Double,
        waitingSeconds: Long,
        isNight: Boolean,
    ): FareBreakdown {
        require(config.isValid) { "Invalid fare config: ${config.problems()}" }

        val minFare = money(config.minFare)

        // Distance beyond the minimum, charged per completed step.
        val minMeters = BigDecimal.valueOf(config.minDistanceKm).multiply(THOUSAND)
        val extraMeters = BigDecimal.valueOf(distanceMeters.coerceAtLeast(0.0))
            .subtract(minMeters).max(BigDecimal.ZERO)
        val step = BigDecimal(config.distanceStepMeters)
        val steps = extraMeters.divide(step, 0, RoundingMode.FLOOR)
        val chargedExtraMeters = steps.multiply(step)
        val extraDistanceCharge = BigDecimal.valueOf(config.perKmRate)
            .multiply(chargedExtraMeters)
            .divide(THOUSAND, 2, RoundingMode.HALF_UP)

        // Waiting: only completed blocks.
        val blockSeconds = config.waitingBlockMinutes * 60L
        val waitingBlocks = waitingSeconds.coerceAtLeast(0) / blockSeconds
        val waitingCharge = money(config.waitingRate).multiply(BigDecimal(waitingBlocks))

        val nightSurcharge = if (isNight) {
            val extraFactor = BigDecimal.valueOf(config.nightMultiplier).subtract(BigDecimal.ONE)
            val base = minFare.add(extraDistanceCharge)
                .let { if (config.nightAppliesToWaiting) it.add(waitingCharge) else it }
            base.multiply(extraFactor).setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(2)
        }

        val subtotal = minFare.add(extraDistanceCharge).add(waitingCharge).add(nightSurcharge)
            .setScale(2, RoundingMode.HALF_UP)
        val total = when (config.rounding) {
            FareRounding.NEAREST -> subtotal.setScale(0, RoundingMode.HALF_UP)
            FareRounding.UP -> subtotal.setScale(0, RoundingMode.CEILING)
        }

        return FareBreakdown(
            minFare = minFare,
            chargedExtraKm = chargedExtraMeters.divide(THOUSAND, 3, RoundingMode.UNNECESSARY),
            extraDistanceCharge = extraDistanceCharge,
            waitingBlocks = waitingBlocks,
            waitingCharge = waitingCharge.setScale(2, RoundingMode.HALF_UP),
            isNight = isNight,
            nightSurcharge = nightSurcharge,
            subtotal = subtotal,
            total = total,
        )
    }

    private fun money(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
}

/** Converts an exact rupee amount to paise for storage. */
fun BigDecimal.toPaise(): Long = movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()

fun paiseToRupees(paise: Long): BigDecimal = BigDecimal.valueOf(paise, 2)
