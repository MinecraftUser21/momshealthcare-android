package com.momshealthcare.app

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.momshealthcare.app.databinding.ActivityMainBinding

/** The app shell: four tabs, a floating Book button and the shared toast. */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var toaster: Toaster

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        edgeToEdge()
        toaster = Toaster(b.toast)

        applyInsets()
        b.nav.setOnItemSelectedListener { show(it.itemId, animate = true); true }
        b.nav.setOnItemReselectedListener { (current() as? Tab)?.scrollToTop() }
        b.fab.setOnClickListener { openBooking() }

        if (savedInstanceState == null) show(R.id.nav_home, animate = false)
    }

    // The tab bar restores its selection only now, after onCreate.
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        syncFab(b.nav.selectedItemId)
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            b.root.setPadding(bars.left, 0, bars.right, 0)
            b.container.setPadding(0, bars.top, 0, 0)
            b.nav.setPadding(0, 0, 0, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        // Keep the Book button and toasts just above the tab bar, whatever its height.
        b.nav.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop) return@addOnLayoutChangeListener
            val above = bottom - top + dp(17)
            (b.fab.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = above
            (b.toast.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = above
            b.fab.requestLayout()
        }
    }

    // ------------------------------------------------------------------
    // Tabs: each is created once, then shown and hidden so it keeps its place.
    // ------------------------------------------------------------------

    interface Tab {
        fun scrollToTop()
    }

    private fun create(id: Int): Fragment = when (id) {
        R.id.nav_appointments -> AppointmentsFragment()
        R.id.nav_pregnancy -> PregnancyFragment()
        R.id.nav_clinic -> ClinicFragment()
        else -> HomeFragment()
    }

    private fun current(): Fragment? = supportFragmentManager.fragments.firstOrNull { it.tag?.startsWith("tab_") == true && !it.isHidden }

    private fun show(id: Int, animate: Boolean) {
        val fm = supportFragmentManager
        val tag = "tab_$id"
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        if (animate && Motion.enabled(this)) tx.setCustomAnimations(R.anim.tab_in, R.anim.tab_out)
        fm.fragments.forEach { if (it.tag?.startsWith("tab_") == true && it.tag != tag && !it.isHidden) tx.hide(it) }
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) tx.add(R.id.container, create(id), tag) else tx.show(existing)
        tx.commit()
        syncFab(id)
    }

    fun selectTab(id: Int) {
        b.nav.selectedItemId = id
    }

    private fun syncFab(id: Int) {
        if (id == R.id.nav_home || id == R.id.nav_appointments) {
            b.fab.extend()
            b.fab.show()
        } else {
            b.fab.hide()
        }
    }

    /** Tabs report scrolling so the Book button can tuck into an icon while reading. */
    fun onContentScrolled(dy: Int) {
        if (dy > dp(4) && b.fab.isExtended) b.fab.shrink()
        else if (dy < -dp(4) && !b.fab.isExtended) b.fab.extend()
    }

    // ------------------------------------------------------------------

    @Suppress("DEPRECATION")
    fun openBooking(serviceId: String? = null, notes: String? = null, dateIso: String? = null) {
        startActivity(
            Intent(this, BookingActivity::class.java)
                .putExtra(BookingActivity.EXTRA_SERVICE, serviceId)
                .putExtra(BookingActivity.EXTRA_NOTES, notes)
                .putExtra(BookingActivity.EXTRA_DATE, dateIso)
        )
        overridePendingTransition(R.anim.screen_enter, R.anim.screen_exit)
    }

    fun showService(id: String) {
        if (supportFragmentManager.findFragmentByTag("service") == null) {
            ServiceSheet.newInstance(id).show(supportFragmentManager, "service")
        }
    }

    fun toast(text: CharSequence, error: Boolean = false) = toaster.show(text, error)

    private fun dp(v: Int) = Motion.dp(this, v.toFloat()).toInt()
}

/** The activity that hosts every tab. */
val Fragment.main get() = requireActivity() as MainActivity
