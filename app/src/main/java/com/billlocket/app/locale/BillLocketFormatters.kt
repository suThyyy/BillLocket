package com.billlocket.app.locale

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * BL-045 — Centralized, testable presentation formatters.
 *
 * All functions accept an explicit [Locale] so they are unit-testable without any Android context.
 * Callers obtain the active locale from [activeAppLocale] at the point of use, not at startup,
 * to avoid stale-locale issues after a language change.
 *
 * ## Design decisions
 *
 * ### Vietnamese (vi-VN)
 * - Money: "1.250.000 ₫" — uses vi-VN NumberFormat grouping (period as thousands separator).
 * - Date:  "09/10/2026" — dd/MM/yyyy pattern.
 * - Time:  "14:30"       — HH:mm (24-hour).
 * - DateTime: "14:30, 09/10/2026"
 *
 * ### English (en-US)
 * - Money: "₫1,250,000" — currency symbol before amount, comma as thousands separator.
 * - Date:  "10/09/2026" — MM/dd/yyyy (US convention).
 * - Time:  "2:30 PM"    — h:mm a (12-hour AM/PM).
 * - DateTime: "2:30 PM, 10/09/2026"
 *
 * English locale is consistently en-US throughout the app (see AppLocaleControllerImpl.TAG_ENGLISH).
 *
 * ### Formatting rules
 * - Domain Money.minorUnits is NEVER altered; only the display string changes.
 * - Currency code remains VND; no conversion occurs.
 * - Hidden/obscured amounts bypass locale-aware formatting entirely.
 *
 * ### Distance
 * - Distances < 1000 m: "NNN m".
 * - Distances ≥ 1000 m: "N.N km".
 * Format is numeric; no locale-specific text is added.
 */
object BillLocketFormatters {

    // ─── Locale selectors ──────────────────────────────────────────────────────

    /** The Vietnamese locale used throughout this app. */
    val LOCALE_VI: Locale = Locale("vi", "VN")

    /** The English locale used throughout this app. */
    val LOCALE_EN: Locale = Locale("en", "US")

    // ─── Money ─────────────────────────────────────────────────────────────────

    /**
     * Format [minorUnits] as a VND money string for the given [locale].
     *
     * Vietnamese: "1.250.000 ₫"
     * English:    "₫1,250,000"
     *
     * Domain value [minorUnits] is unchanged; only the display string differs.
     */
    fun formatMoney(minorUnits: Long, locale: Locale): String {
        val absValue = kotlin.math.abs(minorUnits)
        val formatted = NumberFormat.getNumberInstance(locale).format(absValue)
        return when {
            minorUnits < 0 && locale.language == "vi" -> "-$formatted ₫"
            minorUnits < 0 -> "-₫$formatted"
            locale.language == "vi" -> "$formatted ₫"
            else -> "₫$formatted"
        }
    }

    /**
     * Convenience overload: format money with a visibility guard.
     * HIDDEN → "••••", OBSCURED → "*** ₫", SHOWN → [formatMoney].
     */
    fun formatMoneyWithVisibility(
        minorUnits: Long,
        locale: Locale,
        hidden: Boolean = false,
        obscured: Boolean = false,
    ): String = when {
        hidden -> "••••"
        obscured -> "*** ₫"
        else -> formatMoney(minorUnits, locale)
    }

    // ─── Date ──────────────────────────────────────────────────────────────────

    /**
     * Format a [LocalDate] using the locale-appropriate date pattern.
     *
     * Vietnamese: "dd/MM/yyyy"
     * English:    "MM/dd/yyyy"
     */
    fun formatDate(date: LocalDate, locale: Locale): String {
        val pattern = if (locale.language == "vi") "dd/MM/yyyy" else "MM/dd/yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /**
     * Format epoch day as a date string.
     */
    fun formatEpochDay(epochDay: Long, locale: Locale): String =
        formatDate(LocalDate.ofEpochDay(epochDay), locale)

    /**
     * Format epoch day as a short date (dd/MM or MM/dd).
     */
    fun formatEpochDayShort(epochDay: Long, locale: Locale): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val pattern = if (locale.language == "vi") "dd/MM" else "MM/dd"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    // ─── Time ──────────────────────────────────────────────────────────────────

    /**
     * Format a [LocalTime] using the locale-appropriate time pattern.
     *
     * Vietnamese: "HH:mm" (24-hour)
     * English:    "h:mm a" (12-hour AM/PM)
     */
    fun formatTime(time: LocalTime, locale: Locale): String {
        val pattern = if (locale.language == "vi") "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    // ─── DateTime from epoch millis ────────────────────────────────────────────

    /**
     * Format epoch milliseconds as a combined date-time string for the given [locale] and [zone].
     *
     * Vietnamese: "HH:mm, dd/MM/yyyy"
     * English:    "h:mm a, MM/dd/yyyy"
     */
    fun formatDateTime(epochMillis: Long, zone: ZoneId, locale: Locale): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val timePattern = if (locale.language == "vi") "HH:mm" else "h:mm a"
        val datePattern = if (locale.language == "vi") "dd/MM/yyyy" else "MM/dd/yyyy"
        val timePart = zdt.toLocalTime().format(DateTimeFormatter.ofPattern(timePattern, locale))
        val datePart = zdt.toLocalDate().format(DateTimeFormatter.ofPattern(datePattern, locale))
        return "$timePart, $datePart"
    }

    /**
     * Format epoch milliseconds as a short date-time (e.g. for widget/map labels).
     *
     * Vietnamese: "dd/MM HH:mm"
     * English:    "MM/dd h:mm a"
     */
    fun formatDateTimeShort(epochMillis: Long, zone: ZoneId, locale: Locale): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val pattern = if (locale.language == "vi") "dd/MM HH:mm" else "MM/dd h:mm a"
        return zdt.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /**
     * Format time-only from epoch millis.
     */
    fun formatTimeFromEpoch(epochMillis: Long, zone: ZoneId, locale: Locale): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val pattern = if (locale.language == "vi") "HH:mm" else "h:mm a"
        return zdt.toLocalTime().format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    // ─── Widget relative time ──────────────────────────────────────────────────

    /**
     * Format a widget timestamp as a relative/absolute label.
     *
     * Vietnamese: "Hôm nay HH:mm", "Hôm qua HH:mm", or "dd/MM HH:mm".
     * English:    "Today h:mm a",   "Yesterday h:mm a", or "MM/dd h:mm a".
     *
     * [todayLabel] and [yesterdayLabel] are supplied by the caller from string resources
     * so that this function stays locale-independent regarding text content (resource strings
     * are already locale-resolved by Android).
     */
    fun formatWidgetRelativeTime(
        occurredAtMillis: Long,
        zone: ZoneId,
        locale: Locale,
        todayLabel: String,
        yesterdayLabel: String,
        nowDate: LocalDate? = null,
    ): String {
        if (occurredAtMillis <= 0) return ""
        return runCatching {
            val dateTime = Instant.ofEpochMilli(occurredAtMillis).atZone(zone)
            val today = nowDate ?: LocalDate.now(zone)
            val txDate = dateTime.toLocalDate()
            val timePattern = if (locale.language == "vi") "HH:mm" else "h:mm a"
            val timeStr = dateTime.toLocalTime().format(DateTimeFormatter.ofPattern(timePattern, locale))
            when {
                txDate == today -> "$todayLabel $timeStr"
                txDate == today.minusDays(1) -> "$yesterdayLabel $timeStr"
                else -> formatDateTimeShort(occurredAtMillis, zone, locale)
            }
        }.getOrDefault("")
    }

    // ─── Percentage ────────────────────────────────────────────────────────────

    /**
     * Format [percent] as a percentage string, e.g. "75%".
     * The "%" symbol is locale-neutral in this context.
     */
    fun formatPercent(percent: Int): String = "$percent%"

    // ─── Distance ──────────────────────────────────────────────────────────────

    /**
     * Format a distance in metres.
     * Less than 1000 m: "NNN m".
     * 1000 m or more:   "N.N km".
     * Format is numeric and locale-neutral regarding the unit abbreviations.
     */
    fun formatDistance(metres: Double, locale: Locale): String =
        if (metres < 1000.0) {
            "${metres.toInt()} m"
        } else {
            String.format(locale, "%.1f km", metres / 1000.0)
        }

    // ─── Calendar & Month Formatting ──────────────────────────────────────────

    /**
     * Format a [YearMonth] as a header, e.g. "Tháng 10 2026" (VI) or "October 2026" (EN)
     * using locale-aware [DateTimeFormatter].
     */
    fun formatMonthYear(yearMonth: YearMonth, locale: Locale): String {
        val formatted = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /**
     * Return weekday column headers starting on Monday (ISO-8601).
     * Vietnamese: ["T2", "T3", "T4", "T5", "T6", "T7", "CN"] (narrow text style)
     * English:    ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"] (short text style)
     */
    fun weekdayAbbreviations(locale: Locale): List<String> =
        java.time.DayOfWeek.values().map { day ->
            if (locale.language == "vi") {
                day.getDisplayName(java.time.format.TextStyle.NARROW, locale)
            } else {
                day.getDisplayName(java.time.format.TextStyle.SHORT, locale)
            }
        }

    /**
     * Format date with full month name, e.g. "09 tháng 10, 2026" (VI) or "October 09, 2026" (EN)
     * using locale-aware [DateTimeFormatter].
     */
    fun formatDateWithMonthName(date: LocalDate, locale: Locale): String {
        val pattern = if (locale.language == "vi") "dd MMMM, yyyy" else "MMMM dd, yyyy"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /**
     * Format day and month, e.g. "9 tháng 10" (VI) or "October 9" (EN)
     * using locale-aware [DateTimeFormatter].
     */
    fun formatDayAndMonth(date: LocalDate, locale: Locale): String {
        val pattern = if (locale.language == "vi") "d MMMM" else "MMMM d"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /**
     * Format month title, e.g. "Tháng 10" (VI) or "October" (EN)
     * using locale-aware [DateTimeFormatter].
     */
    fun formatMonthTitle(yearMonth: YearMonth, locale: Locale): String {
        val formatted = yearMonth.format(DateTimeFormatter.ofPattern("MMMM", locale))
        return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}
