package com.momshealthcare.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** The few things the app remembers: the last request sent and the chosen appearance. */
object Store {
    private const val PREFS = "moms"

    class Request(
        val name: String,
        val phone: String,
        val serviceLabel: String,
        val dateIso: String,
        val slotId: String,
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveRequest(context: Context, r: Request) {
        prefs(context).edit()
            .putString("req_name", r.name)
            .putString("req_phone", r.phone)
            .putString("req_service", r.serviceLabel)
            .putString("req_date", r.dateIso)
            .putString("req_slot", r.slotId)
            .apply()
    }

    fun lastRequest(context: Context): Request? {
        val p = prefs(context)
        val date = p.getString("req_date", null) ?: return null
        return Request(
            name = p.getString("req_name", "") ?: "",
            phone = p.getString("req_phone", "") ?: "",
            serviceLabel = p.getString("req_service", "") ?: "",
            dateIso = date,
            slotId = p.getString("req_slot", "") ?: "",
        )
    }

    /** Dark by default, to match the website. */
    fun nightMode(context: Context): Int =
        prefs(context).getInt("night_mode", AppCompatDelegate.MODE_NIGHT_YES)

    fun setNightMode(context: Context, mode: Int) {
        prefs(context).edit().putInt("night_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
