package com.momshealthcare.app

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Sends an appointment request to the clinic inbox through the same FormSubmit form the
 * website uses, so the doctor gets an identical email whichever way the patient books.
 *
 * Survives the screen being rotated or left mid-send: the result waits in [pending]
 * until a screen [attach]es again.
 */
object BookingSender {

    sealed class Result {
        object Sent : Result()
        /** FormSubmit needs the inbox owner to click its activation link first. */
        object NotActivated : Result()
        class Failed(val reason: String) : Result()
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var inFlight = false
        private set
    private var pending: Result? = null
    private var listener: ((Result) -> Unit)? = null

    fun attach(l: (Result) -> Unit) {
        listener = l
        pending?.let { pending = null; l(it) }
    }

    fun detach() {
        listener = null
    }

    fun send(context: Context, fields: Map<String, String>, record: Store.Request) {
        if (inFlight) return
        inFlight = true
        val app = context.applicationContext
        worker.execute {
            var result = post(fields)
            // One quiet retry for flaky mobile connections.
            if (result is Result.Failed && result.reason.startsWith("io:")) {
                Thread.sleep(1200)
                result = post(fields)
            }
            if (result is Result.Sent) Store.saveRequest(app, record)
            main.post {
                inFlight = false
                val l = listener
                if (l != null) l(result) else pending = result
            }
        }
    }

    private fun post(fields: Map<String, String>): Result {
        var conn: HttpURLConnection? = null
        return try {
            val body = fields.entries.joinToString("&") { (k, v) ->
                URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
            }.toByteArray(Charsets.UTF_8)

            conn = (URL(Clinic.FORM_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                useCaches = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setFixedLengthStreamingMode(body.size)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                // FormSubmit ties the form to the page it lives on; this is the website's form.
                setRequestProperty("Referer", Clinic.WEBSITE)
                setRequestProperty("Origin", Clinic.WEBSITE_ORIGIN)
                setRequestProperty("User-Agent", "MomsHealthCare-Android/1.0 (Android ${Build.VERSION.RELEASE})")
            }
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            val message = json.optString("message")

            when {
                code in 200..299 && json.optString("success") == "true" -> Result.Sent
                message.contains("activat", ignoreCase = true) -> Result.NotActivated
                else -> Result.Failed("http:$code $message")
            }
        } catch (e: IOException) {
            Result.Failed("io:${e.javaClass.simpleName}")
        } catch (e: RuntimeException) {
            Result.Failed("err:${e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }
}
