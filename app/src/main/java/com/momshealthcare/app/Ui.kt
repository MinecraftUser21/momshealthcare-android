package com.momshealthcare.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.widget.Toast
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat

fun Context.isNight() =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** Draw behind the system bars on every Android version, with icons that suit the theme. */
@Suppress("DEPRECATION")
fun Activity.edgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val light = !isNight()
    val bars = WindowCompat.getInsetsController(window, window.decorView)
    if (Build.VERSION.SDK_INT >= 23) {
        window.statusBarColor = Color.TRANSPARENT
        bars.isAppearanceLightStatusBars = light
    } else {
        // Android 5 can't draw dark status bar icons.
        window.statusBarColor = if (light) Color.BLACK else Color.TRANSPARENT
    }
    if (Build.VERSION.SDK_INT >= 27) {
        window.navigationBarColor = Color.TRANSPARENT
        bars.isAppearanceLightNavigationBars = light
    } else {
        window.navigationBarColor = Color.BLACK
    }
}

/** Opens a link or dial/map URI, without crashing on phones that have no app for it. */
fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
    }
}

fun Context.openUri(uri: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

fun Context.dial(uri: String = Clinic.PHONE_URI) = launch(Intent(Intent.ACTION_DIAL, Uri.parse(uri)))

/** A span that switches to one of the bundled Inter weights. */
class FontSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) = apply(tp)
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    private fun apply(p: Paint) {
        p.typeface = typeface
    }
}

/** "Prefer to talk? " + emphasised "Call 084206 98958 ›". */
fun Context.leadWithLink(lead: CharSequence, link: CharSequence, @FontRes font: Int = R.font.inter_medium): CharSequence {
    val sb = SpannableStringBuilder(lead)
    val start = sb.length
    sb.append(link)
    sb.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.text)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    ResourcesCompat.getFont(this, font)?.let {
        sb.setSpan(FontSpan(it), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return sb
}

/** Opens the phone's calendar with the request filled in; the person saves it there. */
fun Context.addToCalendar(r: Store.Request) {
    val slot = Clinic.slot(r.slotId)
    val start = Clinic.millisAt(r.dateIso, slot?.startHour ?: Clinic.OPENS_HOUR)
    val end = Clinic.millisAt(r.dateIso, slot?.endsHour ?: Clinic.CLOSES_HOUR)
    launch(
        Intent(Intent.ACTION_INSERT)
            .setData(android.provider.CalendarContract.Events.CONTENT_URI)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(android.provider.CalendarContract.Events.TITLE, getString(R.string.calendar_title, r.serviceLabel))
            .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, "${getString(R.string.brand)}, ${getString(R.string.address)}")
            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, getString(R.string.calendar_desc, r.name))
    )
}

fun Context.copyAddress() {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(
        android.content.ClipData.newPlainText("Address", "${getString(R.string.brand)}, ${getString(R.string.address)}")
    )
}
