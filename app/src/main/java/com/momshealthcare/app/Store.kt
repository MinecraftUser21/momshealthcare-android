package com.momshealthcare.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the app remembers, kept on the phone only: appointment requests,
 * saved patients, pregnancy dates and the chosen appearance.
 */
object Store {
    private const val PREFS = "moms"
    const val REQUESTED = "requested"
    const val CANCELLED = "cancelled"

    class Request(
        val id: Long,
        val name: String,
        val phone: String,
        val serviceLabel: String,
        val dateIso: String,
        val slotId: String,
        val status: String = REQUESTED,
    ) {
        val isUpcoming get() = status == REQUESTED && dateIso >= Clinic.todayIso()

        fun toJson(): JSONObject = JSONObject()
            .put("id", id).put("name", name).put("phone", phone).put("service", serviceLabel)
            .put("date", dateIso).put("slot", slotId).put("status", status)

        fun withStatus(s: String) = Request(id, name, phone, serviceLabel, dateIso, slotId, s)

        companion object {
            fun from(o: JSONObject) = Request(
                o.optLong("id"), o.optString("name"), o.optString("phone"), o.optString("service"),
                o.optString("date"), o.optString("slot"), o.optString("status", REQUESTED),
            )
        }
    }

    class Profile(val name: String, val phone: String) {
        fun sameAs(o: Profile) = phone == o.phone && name.equals(o.name, ignoreCase = true)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------
    // Appointment requests
    // ---------------------------------------------------------------

    @Synchronized
    fun requests(context: Context): List<Request> {
        migrate(context)
        val arr = JSONArray(prefs(context).getString("requests", "[]"))
        return (0 until arr.length()).map { Request.from(arr.getJSONObject(it)) }
    }

    @Synchronized
    private fun write(context: Context, list: List<Request>) {
        val arr = JSONArray()
        // Newest 50 is plenty for a phone.
        list.sortedByDescending { it.id }.take(50).forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString("requests", arr.toString()).apply()
    }

    @Synchronized
    fun addRequest(context: Context, r: Request) = write(context, requests(context) + r)

    @Synchronized
    fun setStatus(context: Context, id: Long, status: String) =
        write(context, requests(context).map { if (it.id == id) it.withStatus(status) else it })

    fun latest(context: Context): Request? = requests(context).maxByOrNull { it.id }

    /** The soonest request that is still waiting for its day. */
    fun next(context: Context): Request? =
        requests(context).filter { it.isUpcoming }.minWithOrNull(compareBy({ it.dateIso }, { it.id }))

    /** Version 1.0 kept a single request under separate keys. */
    private fun migrate(context: Context) {
        val p = prefs(context)
        val date = p.getString("req_date", null) ?: return
        val old = Request(
            id = 1L,
            name = p.getString("req_name", "") ?: "",
            phone = p.getString("req_phone", "") ?: "",
            serviceLabel = p.getString("req_service", "") ?: "",
            dateIso = date,
            slotId = p.getString("req_slot", "") ?: "",
        )
        p.edit()
            .putString("requests", JSONArray().put(old.toJson()).toString())
            .remove("req_name").remove("req_phone").remove("req_service").remove("req_date").remove("req_slot")
            .apply()
    }

    // ---------------------------------------------------------------
    // Saved patients
    // ---------------------------------------------------------------

    @Synchronized
    fun profiles(context: Context): List<Profile> {
        val arr = JSONArray(prefs(context).getString("profiles", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Profile(o.optString("name"), o.optString("phone")) } }
    }

    @Synchronized
    private fun writeProfiles(context: Context, list: List<Profile>) {
        val arr = JSONArray()
        list.take(8).forEach { arr.put(JSONObject().put("name", it.name).put("phone", it.phone)) }
        prefs(context).edit().putString("profiles", arr.toString()).apply()
    }

    /** Saves (or moves to the front) a patient, most recent first. */
    @Synchronized
    fun saveProfile(context: Context, p: Profile) =
        writeProfiles(context, listOf(p) + profiles(context).filterNot { it.sameAs(p) })

    @Synchronized
    fun removeProfile(context: Context, p: Profile) =
        writeProfiles(context, profiles(context).filterNot { it.sameAs(p) })

    // ---------------------------------------------------------------
    // Pregnancy
    // ---------------------------------------------------------------

    fun lmp(context: Context): String? = prefs(context).getString("lmp", null)

    fun setLmp(context: Context, iso: String?) {
        prefs(context).edit().apply { if (iso == null) remove("lmp") else putString("lmp", iso) }.apply()
    }

    // ---------------------------------------------------------------
    // Appearance
    // ---------------------------------------------------------------

    /** Dark by default, like the website. */
    fun nightMode(context: Context): Int =
        prefs(context).getInt("night_mode", AppCompatDelegate.MODE_NIGHT_YES)

    fun setNightMode(context: Context, mode: Int) {
        prefs(context).edit().putInt("night_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
