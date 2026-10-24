package com.momshealthcare.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.momshealthcare.app.databinding.FragmentPregnancyBinding
import com.momshealthcare.app.databinding.ItemScanBinding

/** Weeks, due date and the usual scan timeline, worked out from the last period. */
class PregnancyFragment : Fragment(), MainActivity.Tab {

    private var _b: FragmentPregnancyBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentPregnancyBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.pickLmp.setOnClickListener { pick(byDueDate = false) }
        b.pickDue.setOnClickListener { pick(byDueDate = true) }
        b.change.setOnClickListener { pick(byDueDate = false) }
        b.clear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preg_clear_title)
                .setMessage(R.string.preg_clear_body)
                .setPositiveButton(R.string.remove) { _, _ ->
                    Store.setLmp(requireContext(), null)
                    render(animate = false)
                }
                .setNegativeButton(R.string.keep, null)
                .show()
        }
        b.bookCheckup.setOnClickListener { main.openBooking("gyn") }
        b.statTrimester.label.setText(R.string.preg_trimester)
        b.statDue.label.setText(R.string.preg_due)
        b.statToGo.label.setText(R.string.preg_to_go)
        b.scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, oldY -> main.onContentScrolled(y - oldY) })

        // A picker restored after rotation needs its listener back.
        @Suppress("UNCHECKED_CAST")
        (childFragmentManager.findFragmentByTag(TAG_LMP) as? MaterialDatePicker<Long>)?.let { listen(it, false) }
        @Suppress("UNCHECKED_CAST")
        (childFragmentManager.findFragmentByTag(TAG_DUE) as? MaterialDatePicker<Long>)?.let { listen(it, true) }

        render(animate = savedInstanceState == null)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) render(animate = false)
    }

    override fun scrollToTop() = b.scroll.smoothScrollTo(0, 0)

    private fun render(animate: Boolean) {
        val ctx = requireContext()
        val lmp = Store.lmp(ctx)
        b.setup.visibility = if (lmp == null) View.VISIBLE else View.GONE
        b.tracker.visibility = if (lmp == null) View.GONE else View.VISIBLE
        if (lmp == null) return

        val p = Pregnancy.Progress(lmp)
        b.weeks.text = p.weeks.coerceAtLeast(0).toString()
        b.weeksLabel.text = resources.getQuantityString(R.plurals.preg_plus_days, p.dayOfWeek, p.dayOfWeek)
        b.ring.setProgressCompat((p.fraction * 1000).toInt(), animate && Motion.enabled(ctx))
        b.statTrimester.value.setText(
            when (p.trimester) {
                1 -> R.string.preg_first
                2 -> R.string.preg_second
                else -> R.string.preg_third
            }
        )
        b.statDue.value.text = Clinic.dayMonth(p.dueIso)
        b.statToGo.value.text = if (p.daysToGo > 0) getString(R.string.preg_days, p.daysToGo) else "—"
        b.overdue.visibility = if (p.overdue) View.VISIBLE else View.GONE

        b.scanList.removeAllViews()
        val today = Clinic.todayIso()
        for (scan in Pregnancy.scans) {
            val row = ItemScanBinding.inflate(layoutInflater, b.scanList, true)
            val state = p.state(scan)
            val name = getString(scan.name)
            row.name.text = name
            row.state.setImageResource(
                when (state) {
                    Pregnancy.State.DONE -> R.drawable.scan_done
                    Pregnancy.State.NOW -> R.drawable.scan_now
                    Pregnancy.State.LATER -> R.drawable.scan_later
                }
            )
            val start = p.windowStart(scan)
            row.window.text = if (state == Pregnancy.State.DONE) {
                getString(R.string.scan_window_done, scan.startWeek, scan.endWeek)
            } else {
                getString(R.string.scan_window, scan.startWeek, scan.endWeek, Clinic.dayMonth(start), Clinic.dayMonth(p.windowEnd(scan)))
            }
            if (state == Pregnancy.State.DONE || p.overdue) {
                row.book.visibility = View.GONE
                row.name.setTextColor(ContextCompat.getColor(ctx, R.color.text_2))
            } else {
                if (state == Pregnancy.State.LATER) {
                    row.book.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.btn_secondary_bg)
                    row.book.setTextColor(ContextCompat.getColor(ctx, R.color.text))
                }
                row.book.contentDescription = getString(R.string.book_this, name)
                row.book.setOnClickListener {
                    val notes = getString(R.string.scan_note, name, scan.startWeek, scan.endWeek, p.weeks, p.dayOfWeek)
                    // Suggest the start of the window when it's still ahead and bookable.
                    val date = start.takeIf { it > today && Clinic.daysBetween(today, it) <= Clinic.MAX_DAYS_AHEAD }
                    main.openBooking("usg", notes, date)
                }
            }
        }
    }

    // ------------------------------------------------------------------

    private fun pick(byDueDate: Boolean) {
        val tag = if (byDueDate) TAG_DUE else TAG_LMP
        if (childFragmentManager.findFragmentByTag(tag) != null) return
        val today = Clinic.todayIso()
        val (first, last) = if (byDueDate) today to Clinic.addDays(today, 300) else Clinic.addDays(today, -300) to today
        val firstMs = Clinic.utcMillis(first)
        val lastMs = Clinic.utcMillis(last)
        val current = Store.lmp(requireContext())?.let { if (byDueDate) Clinic.addDays(it, Pregnancy.TERM_DAYS) else it }
        val selection = current?.let { Clinic.utcMillis(it) }?.coerceIn(firstMs, lastMs) ?: lastMs.takeIf { !byDueDate } ?: firstMs

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (byDueDate) R.string.preg_due_title else R.string.preg_lmp_title)
            .setSelection(selection)
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setStart(firstMs)
                    .setEnd(lastMs)
                    .setOpenAt(selection)
                    .setValidator(
                        CompositeDateValidator.allOf(
                            listOf(DateValidatorPointForward.from(firstMs), DateValidatorPointBackward.before(lastMs + 1))
                        )
                    )
                    .build()
            )
            .build()
        listen(picker, byDueDate)
        picker.show(childFragmentManager, tag)
    }

    private fun listen(picker: MaterialDatePicker<Long>, byDueDate: Boolean) {
        picker.addOnPositiveButtonClickListener { millis ->
            val iso = Clinic.isoFromUtcMillis(millis)
            Store.setLmp(requireContext(), if (byDueDate) Pregnancy.lmpFromDue(iso) else iso)
            render(animate = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private companion object {
        const val TAG_LMP = "lmp_picker"
        const val TAG_DUE = "due_picker"
    }
}
