package com.nammameter.app.upi

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder

object Upi {
    // name@handle — handle is letters only (e.g. okaxis, ybl, paytm).
    private val VPA_REGEX = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}$")

    fun isValidVpa(vpa: String): Boolean = VPA_REGEX.matches(vpa.trim())

    /**
     * Builds a UPI deep link. With [amount] null the QR is "static" and the payer types the amount.
     */
    fun payUri(vpa: String, payeeName: String, amount: BigDecimal?, note: String?): String = buildString {
        // A valid VPA contains only [A-Za-z0-9._-@], so it needs no encoding and cannot add parameters.
        require(isValidVpa(vpa)) { "Invalid VPA" }
        append("upi://pay?pa=").append(vpa.trim())
        append("&pn=").append(encode(payeeName.trim()))
        if (amount != null) append("&am=").append(amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
        append("&cu=INR")
        if (!note.isNullOrBlank()) append("&tn=").append(encode(note))
    }

    /** RFC 3986 style: spaces as %20 rather than '+', which some UPI apps show literally. */
    fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
