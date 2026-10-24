package com.momshealthcare.app

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.momshealthcare.app.databinding.FragmentClinicBinding
import com.momshealthcare.app.databinding.ItemRowBinding
import com.momshealthcare.app.databinding.ItemSummaryRowBinding
import java.util.Calendar

/** Where the clinic is, when it's open, every service, and the app's settings. */
class ClinicFragment : Fragment(), MainActivity.Tab {

    private var _b: FragmentClinicBinding? = null
    private val b get() = _b!!

    private var appearanceRow: ItemRowBinding? = null
    private var patientsRow: ItemRowBinding? = null

    private val modes = intArrayOf(
        AppCompatDelegate.MODE_NIGHT_YES,
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    )
    private val modeLabels = intArrayOf(R.string.appearance_dark, R.string.appearance_light, R.string.appearance_system)

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateStatus()
            ticker.postDelayed(this, 60_000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentClinicBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        b.directions.setOnClickListener { ctx.openUri(Clinic.DIRECTIONS_URL) }
        b.call.setOnClickListener { ctx.dial() }
        b.copyRow.bindRow(R.drawable.ic_copy, getString(R.string.copy_address)) {
            ctx.copyAddress()
            // Android 13+ confirms copies itself.
            if (Build.VERSION.SDK_INT < 33) main.toast(getString(R.string.address_copied))
        }
        b.copyRow.chevron.visibility = View.GONE

        bindHours()
        for (svc in Clinic.services) {
            b.serviceList.addRow(svc.icon, getString(svc.name), value = svc.tag) { main.showService(svc.id) }
        }
        b.call112.setOnClickListener { ctx.dial(Clinic.EMERGENCY_URI) }
        bindSettings()
        b.version.text = getString(R.string.version, BuildConfig.VERSION_NAME)
        b.scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, oldY -> main.onContentScrolled(y - oldY) })
    }

    /** Monday to Sunday, with today picked out. */
    private fun bindHours() {
        val today = Clinic.now().get(Calendar.DAY_OF_WEEK)
        val days = listOf(
            Calendar.MONDAY to "Monday", Calendar.TUESDAY to "Tuesday", Calendar.WEDNESDAY to "Wednesday",
            Calendar.THURSDAY to "Thursday", Calendar.FRIDAY to "Friday", Calendar.SATURDAY to "Saturday",
            Calendar.SUNDAY to "Sunday",
        )
        val medium = ResourcesCompat.getFont(requireContext(), R.font.inter_medium)
        for ((day, name) in days) {
            val row = ItemSummaryRowBinding.inflate(layoutInflater, b.hours, true)
            row.value.setText(R.string.hours_value)
            if (day == today) {
                row.label.text = getString(R.string.today_suffix, name)
                row.label.setTextColor(ContextCompat.getColor(requireContext(), R.color.text))
                row.label.typeface = medium ?: Typeface.DEFAULT_BOLD
            } else {
                row.label.text = name
                row.value.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_2))
            }
        }
    }

    private fun bindSettings() {
        appearanceRow = b.settings.addRow(R.drawable.ic_moon, getString(R.string.appearance), value = "") { chooseAppearance() }
        patientsRow = b.settings.addRow(R.drawable.ic_users, getString(R.string.saved_patients), value = "") { managePatients() }
        b.settings.addRow(R.drawable.ic_code, getString(R.string.open_source), value = getString(R.string.open_source_value)) {
            requireContext().openUri(getString(R.string.repo_url))
        }
        b.settings.addRow(R.drawable.ic_info, getString(R.string.licenses)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.licenses)
                .setMessage(R.string.licenses_body)
                .setPositiveButton(R.string.close, null)
                .show()
        }
        refreshSettings()
    }

    private fun refreshSettings() {
        val ctx = requireContext()
        val mode = modes.indexOf(Store.nightMode(ctx)).coerceAtLeast(0)
        appearanceRow?.value?.setText(modeLabels[mode])
        val count = Store.profiles(ctx).size
        patientsRow?.value?.text = if (count == 0) getString(R.string.saved_none) else getString(R.string.saved_count, count)
    }

    private fun chooseAppearance() {
        val ctx = requireContext()
        val current = modes.indexOf(Store.nightMode(ctx)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.appearance)
            .setSingleChoiceItems(modeLabels.map { getString(it) }.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                if (which != current) Store.setNightMode(ctx, modes[which])
            }
            .show()
    }

    private fun managePatients() {
        val ctx = requireContext()
        val people = Store.profiles(ctx)
        val dialog = MaterialAlertDialogBuilder(ctx).setTitle(R.string.saved_patients).setPositiveButton(R.string.close, null)
        if (people.isEmpty()) {
            dialog.setMessage(R.string.saved_empty_body)
        } else {
            val labels = people.map { "${it.name} · ${Clinic.prettyPhone(it.phone)}" }.toTypedArray()
            dialog.setItems(labels) { _, which ->
                val p = people[which]
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(p.name)
                    .setMessage(Clinic.prettyPhone(p.phone))
                    .setPositiveButton(R.string.remove) { _, _ ->
                        Store.removeProfile(ctx, p)
                        refreshSettings()
                    }
                    .setNegativeButton(R.string.keep, null)
                    .show()
            }
        }
        dialog.show()
    }

    private fun updateStatus() {
        val hour = Clinic.hourNow()
        b.statusText.text = Clinic.statusText(requireContext(), hour)
        b.statusDot.setImageResource(if (Clinic.isOpen(hour)) R.drawable.dot_open else R.drawable.dot_closed)
    }

    override fun onResume() {
        super.onResume()
        refreshSettings()
        ticker.post(tick)
    }

    override fun onPause() {
        ticker.removeCallbacks(tick)
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) refreshSettings()
    }

    override fun scrollToTop() = b.scroll.smoothScrollTo(0, 0)

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
        appearanceRow = null
        patientsRow = null
    }
}
