package com.momshealthcare.app

import androidx.annotation.StringRes

/** Pregnancy dating from the first day of the last period (LMP), using the standard 280-day term. */
object Pregnancy {
    const val TERM_DAYS = 280

    class Scan(@StringRes val name: Int, val startWeek: Int, val endWeek: Int)

    /** The scans most pregnancies are offered, and the weeks they're usually done in. */
    val scans = listOf(
        Scan(R.string.scan_dating, 6, 10),
        Scan(R.string.scan_nt, 11, 14),
        Scan(R.string.scan_anomaly, 18, 22),
        Scan(R.string.scan_growth, 28, 32),
        Scan(R.string.scan_late, 34, 37),
    )

    class Progress(val lmpIso: String) {
        val days = Clinic.daysBetween(lmpIso, Clinic.todayIso())
        val weeks = days / 7
        val dayOfWeek = days % 7
        val dueIso = Clinic.addDays(lmpIso, TERM_DAYS)
        val daysToGo = TERM_DAYS - days
        val overdue = days > TERM_DAYS + 14
        val trimester = when {
            weeks < 14 -> 1
            weeks < 28 -> 2
            else -> 3
        }
        val fraction = (days.toFloat() / TERM_DAYS).coerceIn(0f, 1f)

        fun windowStart(s: Scan) = Clinic.addDays(lmpIso, s.startWeek * 7)
        fun windowEnd(s: Scan) = Clinic.addDays(lmpIso, s.endWeek * 7 - 1)

        fun state(s: Scan) = when {
            weeks >= s.endWeek -> State.DONE
            weeks >= s.startWeek -> State.NOW
            else -> State.LATER
        }
    }

    enum class State { DONE, NOW, LATER }

    fun lmpFromDue(dueIso: String) = Clinic.addDays(dueIso, -TERM_DAYS)
}
