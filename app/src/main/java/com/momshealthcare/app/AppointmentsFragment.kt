package com.momshealthcare.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.momshealthcare.app.databinding.FragmentAppointmentsBinding
import com.momshealthcare.app.databinding.ItemAppointmentBinding

/** Every request sent from this phone: upcoming ones first, then earlier and cancelled ones. */
class AppointmentsFragment : Fragment(), MainActivity.Tab {

    private var _b: FragmentAppointmentsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentAppointmentsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, oldY -> main.onContentScrolled(y - oldY) })
    }

    override fun onStart() {
        super.onStart()
        BookingSender.attach(BookingSender.CANCEL, ::onCancelResult)
    }

    override fun onStop() {
        BookingSender.detach(BookingSender.CANCEL)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) render()
    }

    override fun scrollToTop() = b.scroll.smoothScrollTo(0, 0)

    private fun render() {
        val all = Store.requests(requireContext())
        val upcoming = all.filter { it.isUpcoming }.sortedWith(compareBy({ it.dateIso }, { it.id }))
        val earlier = all.filterNot { it.isUpcoming }.sortedWith(compareByDescending<Store.Request> { it.dateIso }.thenByDescending { it.id })

        b.upcomingList.removeAllViews()
        b.earlierList.removeAllViews()
        upcoming.forEach { card(it, b.upcomingList, true) }
        earlier.forEach { card(it, b.earlierList, false) }

        b.empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        b.lead.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        b.earlierHeader.visibility = if (earlier.isEmpty()) View.GONE else View.VISIBLE
        // With nothing coming up, "Earlier" is the first list and needs no extra gap.
        (b.earlierHeader.layoutParams as ViewGroup.MarginLayoutParams).topMargin =
            Motion.dp(requireContext(), if (upcoming.isEmpty()) 4f else 20f).toInt()
    }

    private fun card(r: Store.Request, into: ViewGroup, upcoming: Boolean) {
        val ctx = requireContext()
        val c = ItemAppointmentBinding.inflate(layoutInflater, into, true)
        val slot = Clinic.slot(r.slotId)
        c.status.setText(
            when {
                r.status == Store.CANCELLED -> R.string.status_cancelled
                upcoming -> R.string.status_requested
                else -> R.string.status_past
            }
        )
        c.dateShort.text = Clinic.shortDate(r.dateIso)
        c.service.text = r.serviceLabel
        c.`when`.text = buildString {
            append(Clinic.longDate(r.dateIso))
            if (slot != null) append(" · ").append(getString(slot.title)).append(", ").append(getString(slot.range))
        }
        c.who.text = "${getString(R.string.for_patient, r.name)} · ${Clinic.prettyPhone(r.phone)}"

        if (upcoming) {
            c.primaryAction.setText(R.string.add_to_calendar)
            c.primaryAction.setOnClickListener { ctx.addToCalendar(r) }
            c.secondaryAction.setText(R.string.cancel_request)
            c.secondaryAction.isEnabled = !BookingSender.isInFlight(BookingSender.CANCEL)
            c.secondaryAction.setOnClickListener { confirmCancel(r) }
        } else {
            c.root.alpha = if (r.status == Store.CANCELLED) 0.6f else 1f
            c.primaryAction.setText(R.string.book_again)
            c.primaryAction.setOnClickListener {
                main.openBooking(Clinic.services.firstOrNull { it.label == r.serviceLabel }?.id)
            }
            c.secondaryAction.visibility = View.GONE
        }
    }

    private fun confirmCancel(r: Store.Request) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cancel_title)
            .setMessage(R.string.cancel_body)
            .setPositiveButton(R.string.cancel_confirm) { _, _ -> sendCancel(r) }
            .setNegativeButton(R.string.keep, null)
            .show()
    }

    private fun sendCancel(r: Store.Request) {
        val slot = Clinic.slot(r.slotId)
        val fields = linkedMapOf(
            "_subject" to getString(R.string.cancel_subject),
            "_template" to "table",
            "_captcha" to "false",
            "Request" to "CANCEL this appointment request",
            "Patient Name" to r.name,
            "Phone" to r.phone,
            "Service" to (Clinic.services.firstOrNull { it.label == r.serviceLabel }?.emailValue ?: r.serviceLabel),
            "Preferred Date" to Clinic.emailDate(r.dateIso),
            "Preferred Time" to (slot?.emailValue ?: ""),
            "Booked via" to "Android app",
        )
        BookingSender.send(requireContext(), BookingSender.CANCEL, fields) { ctx -> Store.setStatus(ctx, r.id, Store.CANCELLED) }
        render()
    }

    private fun onCancelResult(result: BookingSender.Result) {
        if (_b == null) return
        render()
        if (result is BookingSender.Result.Sent) {
            main.toast(getString(R.string.cancel_sent))
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.cancel_failed)
                .setPositiveButton(R.string.call) { _, _ -> requireContext().dial() }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
