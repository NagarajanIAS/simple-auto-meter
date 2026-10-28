package com.nammameter.app.upi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nammameter.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Checks the driver PIN with a persisted lockout. One instance per screen: the unlocked
 * state lasts only while that screen is on the back stack.
 */
class PinGate(private val settings: SettingsRepository) {

    var unlocked by mutableStateOf(false)
        private set

    /** Milliseconds until another attempt is allowed, or 0. */
    suspend fun lockRemainingMs(): Long =
        (settings.settings.first().driver.pinLockedUntilEpochMs - System.currentTimeMillis()).coerceAtLeast(0)

    /** Wrong attempts beyond [FREE_ATTEMPTS] lock the gate for a doubling delay. The counter survives restarts. */
    suspend fun tryUnlock(pin: String): Boolean {
        val d = settings.settings.first().driver
        if (!d.hasPin || lockRemainingMs() > 0) return false
        val ok = withContext(Dispatchers.Default) { PinHasher.verify(pin, d.pinSalt, d.pinHash) }
        settings.update { s ->
            val attempts = if (ok) 0 else s.driver.failedPinAttempts + 1
            val lockMs = if (attempts > FREE_ATTEMPTS) BASE_LOCK_MS shl (attempts - FREE_ATTEMPTS - 1).coerceAtMost(8) else 0L
            s.copy(driver = s.driver.copy(failedPinAttempts = attempts, pinLockedUntilEpochMs = System.currentTimeMillis() + lockMs))
        }
        unlocked = ok
        return ok
    }

    fun markUnlocked() {
        unlocked = true
    }

    private companion object {
        const val FREE_ATTEMPTS = 4
        const val BASE_LOCK_MS = 30_000L
    }
}
