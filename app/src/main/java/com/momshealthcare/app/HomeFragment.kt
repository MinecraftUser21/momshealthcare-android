package com.momshealthcare.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.core.widget.NestedScrollView
import com.momshealthcare.app.databinding.FragmentHomeBinding
import com.momshealthcare.app.databinding.ItemQuickBinding
import com.momshealthcare.app.databinding.ItemServiceCardBinding

class HomeFragment : Fragment(), MainActivity.Tab {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!

    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateStatus()
            ticker.postDelayed(this, 60_000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        b.headerCall.setOnClickListener { ctx.dial() }
        b.statusCard.clipToOutline = true
        b.ecg.ambient = !Motion.lowEnd(ctx)

        bindQuickActions()
        bindServices()
        b.tips.addRow(R.drawable.ic_shield, getString(R.string.tile_safe_title), getString(R.string.tile_safe_body))
        b.tips.addRow(R.drawable.ic_doc, getString(R.string.tile_prep_title), getString(R.string.tile_prep_body))
        b.tips.addRow(R.drawable.ic_activity, getString(R.string.tile_doc_title), getString(R.string.tile_doc_body))

        b.upcoming.setOnClickListener { main.selectTab(R.id.nav_appointments) }
        b.scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, oldY ->
            main.onContentScrolled(y - oldY)
            if (y > b.statusCard.bottom) b.ecg.pause() else if (isResumed && !isHidden) b.ecg.start()
        })

        if (savedInstanceState == null && Motion.enabled(ctx)) {
            Motion.riseIn(
                listOf(b.header, b.statusCard, b.quick, b.servicesHeader, b.servicesScroll, b.tipsHeader, b.tips),
                startDelay = 60, stagger = 45, duration = 620,
            )
        }
    }

    private fun bindQuickActions() {
        val actions = listOf(
            Triple(R.drawable.ic_calendar_plus, R.string.q_book) { main.openBooking() },
            Triple(R.drawable.ic_phone, R.string.q_call) { requireContext().dial() },
            Triple(R.drawable.ic_navigation, R.string.q_directions) { requireContext().openUri(Clinic.DIRECTIONS_URL) },
            Triple(R.drawable.ic_baby, R.string.q_pregnancy) { main.selectTab(R.id.nav_pregnancy) },
        )
        actions.forEachIndexed { i, (icon, label, action) ->
            val q = ItemQuickBinding.inflate(layoutInflater, b.quick, true)
            q.icon.setImageResource(icon)
            q.label.setText(label)
            q.root.contentDescription = getString(label)
            q.root.setOnClickListener { action() }
            if (i > 0) (q.root.layoutParams as LinearLayout.LayoutParams).marginStart = Motion.dp(requireContext(), 10f).toInt()
        }
    }

    private fun bindServices() {
        Clinic.services.forEachIndexed { i, svc ->
            val card = ItemServiceCardBinding.inflate(layoutInflater, b.serviceRow, true)
            card.icon.setImageResource(svc.icon)
            card.name.setText(svc.name)
            card.desc.setText(svc.short)
            card.root.contentDescription = getString(svc.name)
            card.root.setOnClickListener { main.showService(svc.id) }
            if (i == Clinic.services.lastIndex) (card.root.layoutParams as LinearLayout.LayoutParams).marginEnd = 0
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        ticker.post(tick)
        if (!isHidden) b.ecg.start()
    }

    override fun onPause() {
        ticker.removeCallbacks(tick)
        b.ecg.pause()
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (hidden) {
            b.ecg.pause()
        } else {
            refresh()
            b.ecg.start()
        }
    }

    override fun scrollToTop() = b.scroll.smoothScrollTo(0, 0)

    private fun refresh() {
        val ctx = requireContext()
        val greeting = Clinic.greeting(ctx)
        val firstName = Store.profiles(ctx).firstOrNull()?.name?.trim()?.substringBefore(' ')
        b.greeting.text = if (firstName.isNullOrEmpty()) greeting else getString(R.string.greet_named, greeting, firstName)
        updateStatus()

        val next = Store.next(ctx)
        if (next == null) {
            b.upcoming.visibility = View.GONE
        } else {
            val slot = Clinic.slot(next.slotId)
            b.upcomingService.text = next.serviceLabel
            b.upcomingWhen.text = buildString {
                append(Clinic.shortDate(next.dateIso))
                if (slot != null) append(" · ").append(getString(slot.title)).append(", ").append(getString(slot.range))
            }
            b.upcomingWho.text = getString(R.string.for_patient, next.name)
            b.upcomingCalendar.setOnClickListener { ctx.addToCalendar(next) }
            b.upcoming.visibility = View.VISIBLE
        }
    }

    private fun updateStatus() {
        val hour = Clinic.hourNow()
        val open = Clinic.isOpen(hour)
        b.statusDot.setImageResource(if (open) R.drawable.dot_open else R.drawable.dot_closed)
        b.statusTitle.setText(if (open) R.string.status_open_title else R.string.status_closed_title)
        b.statusSub.setText(
            when {
                open -> R.string.status_open_sub
                hour < Clinic.OPENS_HOUR -> R.string.status_closed_morning_sub
                else -> R.string.status_closed_night_sub
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
