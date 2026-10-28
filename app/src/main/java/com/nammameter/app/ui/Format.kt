package com.nammameter.app.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatRupees(amount: BigDecimal): String =
    if (amount.scale() <= 0 || amount.stripTrailingZeros().scale() <= 0) {
        "₹" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString()
    } else {
        "₹" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

/** Always two decimals, e.g. ₹38.40 — used in breakdowns. */
fun formatRupeesExact(amount: BigDecimal): String = "₹" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()

fun formatKm(meters: Double): String = String.format(Locale.ROOT, "%.2f km", meters / 1000.0)

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

fun formatDateTime(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun formatDate(date: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)

/** ISO date string from a preset → localized display, or null. */
fun tariffEffectiveText(iso: String?): String? =
    iso?.let { runCatching { formatDate(LocalDate.parse(it)) }.getOrNull() }

fun minutesToHhMm(minutes: Int): String = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

fun hhMmToMinutes(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}
