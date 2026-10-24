package com.momshealthcare.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.momshealthcare.app.databinding.ActivityMainBinding
import com.momshealthcare.app.databinding.ItemServiceBinding
import com.momshealthcare.app.databinding.ItemTileBinding
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var revealer: Revealer
    private lateinit var toaster: Toaster
    private var hoursTile: ItemTileBinding? = null

    private val floaters = ArrayList<ValueAnimator>()
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateStatus()
            ticker.postDelayed(this, 60_000)
        }
    }

    private var barHidden = false
    private var lineOn = false
    private var actionShown = false
    private var lastY = 0
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        edgeToEdge()

        toaster = Toaster(b.toast)
        revealer = Revealer(b.scroll, b.content)

        applyInsets()
        bindHero()
        bindServices()
        bindVisit()
        bindBooking()
        bindLocation()
        bindFooter()
        bindScroll()

        if (savedInstanceState == null && Motion.enabled(this)) {
            Motion.riseIn(
                listOf(b.tiles, b.heroEyebrow, b.heroTitle, b.heroSub, b.heroCtas, b.ecg, b.heroFacts, b.heroArea),
                startDelay = 80,
            )
        }
        b.scroll.post { revealer.check() }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        ticker.post(tick)
        refreshUpcoming()
        if (isEcgOnScreen(b.scroll.scrollY)) b.ecg.start()
        floaters.forEach { if (it.isPaused) it.resume() }
    }

    override fun onPause() {
        resumed = false
        ticker.removeCallbacks(tick)
        b.ecg.pause()
        floaters.forEach { it.pause() }
        super.onPause()
    }

    // ------------------------------------------------------------------
    // Layout around the system bars
    // ------------------------------------------------------------------

    private fun applyInsets() {
        val barHeight = resources.getDimensionPixelSize(R.dimen.bar_height)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            b.root.setPadding(bars.left, 0, bars.right, 0)
            b.topBar.setPadding(0, bars.top, 0, 0)
            b.content.setPadding(0, bars.top + barHeight, 0, 0)
            b.footer.setPadding(b.footer.paddingLeft, b.footer.paddingTop, b.footer.paddingRight, bars.bottom + dp(112))
            (b.actionBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bars.bottom + dp(12)
            (b.toast.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bars.bottom + dp(96)
            b.actionBar.requestLayout()
            WindowInsetsCompat.CONSUMED
        }
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private fun bindHero() {
        b.heroTitle.text = SpannableStringBuilder(getString(R.string.hero_title)).apply {
            append(" ")
            val start = length
            append(getString(R.string.hero_title_soft))
            setSpan(ForegroundColorSpan(color(R.color.text_2)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        b.heroBook.setOnClickListener { openBooking() }
        b.heroCall.setOnClickListener { dial() }
        b.barCall.setOnClickListener { dial() }
        b.barBook.setOnClickListener { openBooking() }
        b.brand.setOnClickListener { b.scroll.smoothScrollTo(0, 0) }
        b.actionCall.setOnClickListener { dial() }
        b.actionBook.setOnClickListener { openBooking() }

        b.ecg.ambient = !Motion.lowEnd(this)
        if (Motion.ambient(this)) startFloating()
        updateStatus()
    }

    /** The instrument tiles drift gently, each on its own rhythm. Skipped on low-end phones. */
    private fun startFloating() {
        val tiles = listOf(b.tileSteth, b.tileProbe, b.tileHeart)
        tiles.forEachIndexed { i, tile ->
            val drift = Motion.dp(this, 4 + i * 1.5f)
            val r = tile.rotation
            tile.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val anim = ObjectAnimator.ofPropertyValuesHolder(
                tile,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -drift, drift),
                PropertyValuesHolder.ofFloat(View.ROTATION, r - 1.5f, r + 1.5f),
            ).apply {
                duration = 4200L + i * 900L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
            anim.start()
            anim.currentPlayTime = i * 1300L
            floaters.add(anim)
        }
    }

    private fun bindServices() {
        revealer.add(b.services.getChildAt(0), b.servicesTitle)
        val medium = ResourcesCompat.getFont(this, R.font.inter_medium)
        for (svc in Clinic.services) {
            val item = ItemServiceBinding.inflate(layoutInflater, b.serviceList, true)
            item.name.text = SpannableStringBuilder(getString(svc.name)).apply {
                if (svc.tag != null) {
                    append("  ")
                    val start = length
                    append(svc.tag)
                    setSpan(RelativeSizeSpan(0.62f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(color(R.color.text_2)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (medium != null) setSpan(FontSpan(medium), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            item.desc.setText(svc.desc)
            item.row.contentDescription = getString(R.string.cd_book_service, svc.label)
            item.row.setOnClickListener { openBooking(svc.id) }
            revealer.add(item.root)
        }
        // Closing hairline under the last service.
        b.serviceList.addView(View(this).apply { setBackgroundColor(color(R.color.line)) }, ViewGroup.LayoutParams.MATCH_PARENT, dp(1).coerceAtLeast(1))
    }

    private fun bindVisit() {
        val tiles = listOf(
            Triple(R.drawable.ic_clock, R.string.tile_hours_title, R.string.tile_hours_body),
            Triple(R.drawable.ic_shield, R.string.tile_safe_title, R.string.tile_safe_body),
            Triple(R.drawable.ic_doc, R.string.tile_prep_title, R.string.tile_prep_body),
            Triple(R.drawable.ic_activity, R.string.tile_doc_title, R.string.tile_doc_body),
        )
        revealer.add(b.visit.getChildAt(0), b.visitTitle)
        tiles.forEachIndexed { i, (icon, title, body) ->
            val t = ItemTileBinding.inflate(layoutInflater, b.tileList, true)
            t.icon.setImageResource(icon)
            t.title.setText(title)
            t.body.setText(body)
            if (i == 0) {
                t.statusRow.visibility = View.VISIBLE
                hoursTile = t
            }
            revealer.add(t.root)
        }
        updateStatus()
    }

    private fun bindBooking() {
        b.bookCall.text = leadWithLink(getString(R.string.prefer_to_talk), getString(R.string.call_link))
        b.bookCall.setOnClickListener { dial() }
        b.bookButton.setOnClickListener { openBooking() }
        revealer.add(b.bookSection.getChildAt(0), b.bookTitle, b.bookLead, b.bookButton, b.bookCall)
    }

    private fun bindLocation() {
        b.directions.setOnClickListener { openUri(Clinic.DIRECTIONS_URL) }
        b.copyAddress.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Address", "${getString(R.string.brand)}, ${getString(R.string.address)}"))
            // Android 13+ confirms copies itself.
            if (Build.VERSION.SDK_INT < 33) toaster.show(getString(R.string.address_copied))
        }
        revealer.add(b.location.getChildAt(0), b.locationTitle, b.locationCard)
    }

    private fun bindFooter() {
        b.footerCopy.text = getString(R.string.footer_copy, Calendar.getInstance().get(Calendar.YEAR))
        val modes = intArrayOf(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val labels = arrayOf(getString(R.string.appearance_dark), getString(R.string.appearance_light), getString(R.string.appearance_system))
        val current = modes.indexOf(Store.nightMode(this)).coerceAtLeast(0)
        b.appearance.text = "${getString(R.string.appearance)} · ${labels[current]}"
        b.appearance.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.appearance)
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    dialog.dismiss()
                    if (which != current) Store.setNightMode(this, modes[which])
                }
                .show()
        }
        revealer.add(b.footer)
    }

    private fun refreshUpcoming() {
        val r = Store.lastRequest(this)
        if (r == null || r.dateIso < Clinic.todayIso()) {
            b.upcoming.visibility = View.GONE
            return
        }
        val slot = Clinic.slot(r.slotId)
        b.upcomingService.text = r.serviceLabel
        b.upcomingWhen.text = buildString {
            append(Clinic.shortDate(r.dateIso))
            if (slot != null) append(" · ").append(getString(slot.title)).append(", ").append(getString(slot.range))
        }
        b.upcomingNote.text = getString(R.string.upcoming_note, Clinic.prettyPhone(r.phone))
        b.upcoming.visibility = View.VISIBLE
    }

    private fun updateStatus() {
        val hour = Clinic.hourNow()
        val text = Clinic.statusText(this, hour)
        val dot = if (Clinic.isOpen(hour)) R.drawable.dot_open else R.drawable.dot_closed
        b.heroStatus.text = text
        b.heroDot.setImageResource(dot)
        hoursTile?.let {
            it.statusText.text = text
            it.statusDot.setImageResource(dot)
        }
    }

    // ------------------------------------------------------------------
    // Scrolling: bar tucks away while reading down, the action bar
    // appears once the hero's own buttons are gone (as on the website).
    // ------------------------------------------------------------------

    private fun bindScroll() {
        b.scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, _ -> onScrolled(y) })
        b.actionBar.post {
            if (!actionShown) b.actionBar.translationY = (b.actionBar.height + dp(24)).toFloat()
        }
    }

    private fun onScrolled(y: Int) {
        val wantLine = y > dp(8)
        if (wantLine != lineOn) {
            lineOn = wantLine
            b.topBarLine.animate().alpha(if (wantLine) 1f else 0f).setDuration(250).start()
        }

        if (Math.abs(y - lastY) > dp(6)) {
            setBarHidden(y > lastY && y > dp(400))
            lastY = y
        }

        val ctaBottom = b.hero.top + b.heroCtas.bottom
        val heroGone = ctaBottom < y + b.topBar.height
        val bookTop = b.bookSection.top
        val bookVisible = bookTop < y + b.scroll.height && b.bookSection.bottom > y
        setActionBar(heroGone && !bookVisible)

        if (resumed) {
            if (isEcgOnScreen(y)) b.ecg.start() else b.ecg.pause()
        }
        revealer.check()
    }

    private fun isEcgOnScreen(y: Int) = b.hero.top + b.ecg.bottom > y

    private fun setBarHidden(hide: Boolean) {
        if (hide == barHidden) return
        barHidden = hide
        b.topBar.animate()
            .translationY(if (hide) -b.topBar.height.toFloat() else 0f)
            .setDuration(450)
            .setInterpolator(Motion.ease)
            .start()
    }

    private fun setActionBar(show: Boolean) {
        if (show == actionShown) return
        actionShown = show
        val bar = b.actionBar
        val away = (bar.height + dp(24)).toFloat()
        bar.animate().cancel()
        if (show) {
            bar.visibility = View.VISIBLE
            bar.animate().translationY(0f).alpha(1f).setDuration(450).setInterpolator(Motion.ease).withEndAction(null).start()
        } else {
            bar.animate().translationY(away).alpha(0f).setDuration(300).setInterpolator(Motion.easeInOut)
                .withEndAction { if (!actionShown) bar.visibility = View.INVISIBLE }
                .start()
        }
    }

    // ------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun openBooking(serviceId: String? = null) {
        startActivity(Intent(this, BookingActivity::class.java).putExtra(BookingActivity.EXTRA_SERVICE, serviceId))
        overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit)
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Int) = Motion.dp(this, v.toFloat()).toInt()
}
