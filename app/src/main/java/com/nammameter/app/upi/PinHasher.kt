package com.nammameter.app.upi

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2 for the 4-digit driver-mode PIN. The PIN is never stored in clear. */
object PinHasher {
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let(::b64)

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return b64(key)
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
}
