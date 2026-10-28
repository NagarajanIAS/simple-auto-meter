package com.nammameter.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nammameter.app.fare.paiseToRupees
import java.math.BigDecimal

/** A completed trip. Money is stored in paise to avoid floating point. */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val distanceMeters: Double,
    val movingSeconds: Long,
    val waitingSeconds: Long,
    val tariffName: String,
    val tariffEffective: String?,
    val isNight: Boolean,
    val chargedExtraKm: Double,
    val waitingBlocks: Long,
    val minFarePaise: Long,
    val extraDistancePaise: Long,
    val waitingPaise: Long,
    val nightSurchargePaise: Long,
    val subtotalPaise: Long,
    val totalPaise: Long,
) {
    val total: BigDecimal get() = paiseToRupees(totalPaise)
}
