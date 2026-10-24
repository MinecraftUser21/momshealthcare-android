package com.momshealthcare.app

import android.content.Context
import androidx.annotation.StringRes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Everything about the clinic that the website also knows: contacts, hours, services. */
object Clinic {
    const val EMAIL = "drpubalisarkar@gmail.com"
    const val PHONE_URI = "tel:+918420698958"
    const val PHONE_DISPLAY = "084206 98958"
    const val EMERGENCY_URI = "tel:112"

    /** The website's own FormSubmit form; already activated for the inbox above. */
    const val FORM_ENDPOINT = "https://formsubmit.co/ajax/$EMAIL"
    const val WEBSITE = "https://momshealthcare.site.je/"
    const val WEBSITE_ORIGIN = "https://momshealthcare.site.je"

    const val DIRECTIONS_URL =
        "https://www.google.com/maps/dir/?api=1&destination=F886%2BQC%2C%20Kolkata%2C%20West%20Bengal"

    const val OPENS_HOUR = 8
    const val CLOSES_HOUR = 21

    /** Bookable window for the date picker. */
    const val MAX_DAYS_AHEAD = 90

    val TZ: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    class Service(
        val id: String,
        @StringRes val name: Int,
        val tag: String?,
        @StringRes val desc: Int,
        /** Short label for the form and summaries. */
        val label: String,
        /** Value sent to the clinic inbox (same as the website's <option value>). */
        val emailValue: String,
    )

    val services = listOf(
        Service("usg", R.string.svc_usg_name, "USG", R.string.svc_usg_desc, "Ultrasound (USG)", "USG (Ultrasound)"),
        Service("echo", R.string.svc_echo_name, "ECHO", R.string.svc_echo_desc, "Echocardiogram (ECHO)", "ECHO (Echocardiogram)"),
        Service("gyn", R.string.svc_gyn_name, null, R.string.svc_gyn_desc, "Gynecology & maternal care", "Gynecology & Maternal Care"),
        Service("cardio", R.string.svc_card_name, null, R.string.svc_card_desc, "Cardiology", "Cardiology Consultation"),
        Service("opd", R.string.svc_opd_name, null, R.string.svc_opd_desc, "General OPD", "General OPD"),
    )

    fun service(id: String?): Service? = services.firstOrNull { it.id == id }

    class Slot(
        val id: String,
        @StringRes val title: Int,
        @StringRes val range: Int,
        val emailValue: String,
        /** Hour (Kolkata) at which this slot is over for today. */
        val endsHour: Int,
    )

    val slots = listOf(
        Slot("morning", R.string.time_morning, R.string.time_morning_range, "Morning (8 AM – 12 PM)", 12),
        Slot("afternoon", R.string.time_afternoon, R.string.time_afternoon_range, "Afternoon (12 PM – 4 PM)", 16),
        Slot("evening", R.string.time_evening, R.string.time_evening_range, "Evening (4 PM – 9 PM)", 21),
    )

    fun slot(id: String?): Slot? = slots.firstOrNull { it.id == id }

    // ---------------------------------------------------------------
    // Kolkata time. The clinic's day decides what can be booked,
    // whatever time zone the phone is set to.
    // ---------------------------------------------------------------

    fun now(): Calendar = Calendar.getInstance(TZ, Locale.ENGLISH)

    fun hourNow(): Int = now().get(Calendar.HOUR_OF_DAY)

    fun isOpen(hour: Int = hourNow()) = hour in OPENS_HOUR until CLOSES_HOUR

    fun statusText(context: Context, hour: Int = hourNow()): String = context.getString(
        when {
            isOpen(hour) -> R.string.status_open
            hour < OPENS_HOUR -> R.string.status_closed_morning
            else -> R.string.status_closed_night
        }
    )

    private fun fmt(pattern: String, tz: TimeZone = TZ) =
        SimpleDateFormat(pattern, Locale.ENGLISH).apply { timeZone = tz }

    /** yyyy-MM-dd for a Kolkata calendar day. */
    fun iso(cal: Calendar): String = fmt("yyyy-MM-dd").format(cal.time)

    fun todayIso(): String = iso(now())

    /** Kolkata calendar day [offset] days from today. */
    fun day(offset: Int): Calendar = now().apply { add(Calendar.DAY_OF_YEAR, offset) }

    fun parseIso(iso: String): Calendar? = try {
        val date = fmt("yyyy-MM-dd").parse(iso) ?: return null
        now().apply { time = date }
    } catch (e: java.text.ParseException) {
        null
    }

    /** "Saturday, 25 October" */
    fun longDate(iso: String): String = parseIso(iso)?.let { fmt("EEEE, d MMMM").format(it.time) } ?: iso

    /** "Sat, 25 Oct 2026" — the wording the clinic sees in the email. */
    fun emailDate(iso: String): String = parseIso(iso)?.let { fmt("EEE, d MMM yyyy").format(it.time) } ?: iso

    /** "Sat, 25 Oct" */
    fun shortDate(iso: String): String = parseIso(iso)?.let { fmt("EEE, d MMM").format(it.time) } ?: iso

    fun weekday(cal: Calendar): String = fmt("EEE").format(cal.time)
    fun month(cal: Calendar): String = fmt("MMM").format(cal.time)

    /** Midnight UTC of a Kolkata day, the unit MaterialDatePicker works in. */
    fun utcMillis(iso: String): Long = fmt("yyyy-MM-dd", TimeZone.getTimeZone("UTC")).parse(iso)!!.time

    fun isoFromUtcMillis(millis: Long): String =
        fmt("yyyy-MM-dd", TimeZone.getTimeZone("UTC")).format(java.util.Date(millis))

    // ---------------------------------------------------------------
    // Phone numbers: same rules as the website.
    // ---------------------------------------------------------------

    fun normalisePhone(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.length == 12 && digits.startsWith("91")) digits = digits.substring(2)
        if (digits.length == 11 && digits.startsWith("0")) digits = digits.substring(1)
        return digits
    }

    fun isValidPhone(raw: String) = Regex("^[6-9]\\d{9}$").matches(normalisePhone(raw))

    /** "98765 43210" */
    fun prettyPhone(tenDigits: String) =
        if (tenDigits.length == 10) "${tenDigits.substring(0, 5)} ${tenDigits.substring(5)}" else tenDigits
}
