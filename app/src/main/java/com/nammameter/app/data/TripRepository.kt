package com.nammameter.app.data

import com.nammameter.app.fare.FareBreakdown
import com.nammameter.app.fare.toPaise
import kotlinx.coroutines.flow.Flow

class TripRepository(private val dao: TripDao) {

    fun observe(id: Long): Flow<TripEntity?> = dao.observe(id)
    fun observeAll(): Flow<List<TripEntity>> = dao.observeAll()
    fun observeSince(fromEpochMs: Long): Flow<List<TripEntity>> = dao.observeSince(fromEpochMs)
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun save(
        startEpochMs: Long,
        endEpochMs: Long,
        distanceMeters: Double,
        movingSeconds: Long,
        waitingSeconds: Long,
        tariffName: String,
        tariffEffective: String?,
        fare: FareBreakdown,
    ): Long = dao.insert(
        TripEntity(
            startEpochMs = startEpochMs,
            endEpochMs = endEpochMs,
            distanceMeters = distanceMeters,
            movingSeconds = movingSeconds,
            waitingSeconds = waitingSeconds,
            tariffName = tariffName,
            tariffEffective = tariffEffective,
            isNight = fare.isNight,
            chargedExtraKm = fare.chargedExtraKm.toDouble(),
            waitingBlocks = fare.waitingBlocks,
            minFarePaise = fare.minFare.toPaise(),
            extraDistancePaise = fare.extraDistanceCharge.toPaise(),
            waitingPaise = fare.waitingCharge.toPaise(),
            nightSurchargePaise = fare.nightSurcharge.toPaise(),
            subtotalPaise = fare.subtotal.toPaise(),
            totalPaise = fare.total.toPaise(),
        ),
    )
}
