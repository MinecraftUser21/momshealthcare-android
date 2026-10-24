package com.momshealthcare.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.momshealthcare.app.databinding.ActivityBookingBinding
import com.momshealthcare.app.databinding.ItemDateBinding
import com.momshealthcare.app.databinding.ItemSummaryRowBinding
import com.momshealthcare.app.databinding.ItemTimeBinding

class BookingActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVICE = "service"
        private const val QUICK_DAYS = 14
        private const val TAG_PICKER = "date_picker"
    }

    private lateinit var b: ActivityBookingBinding
    private lateinit var toaster: Toaster

    private var serviceId: String? = null
    private var dateIso: String? = null
    private var slotId: String? = null
    private var showingSent = false

    private val serviceViews = LinkedHashMap<String, TextView>()
    private val datePills = LinkedHashMap<String, ItemDateBinding>()
    private lateinit var otherPill: ItemDateBinding
    private val slotViews = LinkedHashMap<String, ItemTimeBinding>()

    private var titleInBar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBookingBinding.inflate(layoutInflater)
        setContentView(b.root)
        edgeToEdge()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.screen_return, R.anim.screen_close)
        }
        toaster = Toaster(b.toast)

        applyInsets()
        bindChrome()
        buildServices()
        buildDates()
        buildSlots()
        bindFields()

        if (savedInstanceState == null) {
            serviceId = Clinic.service(intent.getStringExtra(EXTRA_SERVICE))?.id
            if (Motion.enabled(this)) {
                Motion.riseIn(listOf(b.eyebrow, b.title, b.lead, b.preferCall, b.card), startDelay = 60, stagger = 60, duration = 640)
            }
        } else {
            serviceId = savedInstanceState.getString("service")
            dateIso = savedInstanceState.getString("date")
            slotId = savedInstanceState.getString("slot")
            if (savedInstanceState.getBoolean("sent")) Store.lastRequest(this)?.let { showSent(it, animate = false) }
        }
        refreshServices()
        dateIso?.let { selectDate(it) }
        refreshSlots()

        // A date picker restored after rotation needs its listener back.
        @Suppress("UNCHECKED_CAST")
        (supportFragmentManager.findFragmentByTag(TAG_PICKER) as? MaterialDatePicker<Long>)?.let { listenTo(it) }
    }

    override fun onStart() {
        super.onStart()
        setSending(BookingSender.inFlight)
        BookingSender.attach(::onResult)
    }

    override fun onStop() {
        BookingSender.detach()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("service", serviceId)
        outState.putString("date", dateIso)
        outState.putString("slot", slotId)
        outState.putBoolean("sent", showingSent)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < 34) overridePendingTransition(R.anim.screen_return, R.anim.screen_close)
    }

    // ------------------------------------------------------------------
    // Chrome
    // ------------------------------------------------------------------

    private fun applyInsets() {
        val barHeight = resources.getDimensionPixelSize(R.dimen.bar_height)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboardUp = ime.bottom > bars.bottom
            b.root.setPadding(bars.left, 0, bars.right, 0)
            b.topBar.setPadding(0, bars.top, 0, 0)
            // Shrinking the scroller (rather than padding it) lets it keep the focused field in view.
            (b.scroll.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = if (keyboardUp) ime.bottom else 0
            b.scroll.requestLayout()
            b.content.setPadding(0, bars.top + barHeight, 0, (if (keyboardUp) 0 else bars.bottom) + dp(40))
            (b.toast.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = maxOf(bars.bottom, ime.bottom) + dp(20)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun bindChrome() {
        b.back.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        b.barCall.setOnClickListener { dial() }
        b.preferCall.text = leadWithLink(getString(R.string.prefer_to_talk), getString(R.string.call_link))
        b.preferCall.setOnClickListener { dial() }
        b.emergency.text = SpannableStringBuilder(leadWithLink(getString(R.string.emergency_lead), "112")).append(".")
        b.emergency.setOnClickListener { dial(Clinic.EMERGENCY_URI) }
        b.notesLabel.text = SpannableStringBuilder(getString(R.string.f_notes)).apply {
            append(" ")
            val start = length
            append(getString(R.string.f_optional))
            setSpan(ForegroundColorSpan(color(R.color.text_3)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        b.done.setOnClickListener { finish() }
        b.sentCall.setOnClickListener { dial() }

        b.scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, _ ->
            val show = y > dp(72)
            if (show != titleInBar) {
                titleInBar = show
                b.barTitle.animate().alpha(if (show && !showingSent) 1f else 0f).setDuration(200).start()
                b.topBarLine.animate().alpha(if (show) 1f else 0f).setDuration(200).start()
            }
        })
    }

    // ------------------------------------------------------------------
    // Choices
    // ------------------------------------------------------------------

    private fun buildServices() {
        for (svc in Clinic.services) {
            val pill = layoutInflater.inflate(R.layout.item_choice, b.serviceGroup, false) as TextView
            pill.text = svc.label
            pill.setOnClickListener {
                serviceId = svc.id
                refreshServices()
                showError(b.serviceError, null)
            }
            b.serviceGroup.addView(pill)
            serviceViews[svc.id] = pill
        }
    }

    private fun refreshServices() {
        serviceViews.forEach { (id, v) -> v.isSelected = id == serviceId }
    }

    private fun buildDates() {
        val lateTonight = Clinic.hourNow() >= Clinic.CLOSES_HOUR
        for (i in 0 until QUICK_DAYS) {
            val cal = Clinic.day(i)
            val iso = Clinic.iso(cal)
            val pill = ItemDateBinding.inflate(layoutInflater, b.dateRow, true)
            pill.top.text = if (i == 0) getString(R.string.today) else Clinic.weekday(cal)
            pill.middle.text = cal.get(java.util.Calendar.DAY_OF_MONTH).toString()
            pill.bottom.text = Clinic.month(cal)
            pill.root.contentDescription = Clinic.longDate(iso)
            if (i == 0 && lateTonight) {
                // Nothing left to book today once the clinic has closed.
                pill.root.isEnabled = false
                pill.root.alpha = 0.35f
            }
            pill.root.setOnClickListener { selectDate(iso) }
            datePills[iso] = pill
        }
        otherPill = ItemDateBinding.inflate(layoutInflater, b.dateRow, true)
        (otherPill.root.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = 0
        otherPill.root.contentDescription = getString(R.string.pick_date)
        resetOtherPill()
        otherPill.root.setOnClickListener { openPicker() }
    }

    private fun resetOtherPill() {
        otherPill.top.text = getString(R.string.other_date)
        otherPill.middle.text = ""
        val icon = ContextCompat.getDrawable(this, R.drawable.ic_calendar)!!.mutate()
        val size = dp(22)
        icon.setBounds(0, 0, size, size)
        otherPill.middle.setCompoundDrawablesRelative(icon, null, null, null)
        TextViewCompat.setCompoundDrawableTintList(otherPill.middle, ContextCompat.getColorStateList(this, R.color.choice_text))
        otherPill.bottom.setText(R.string.other_date_bottom)
    }

    private fun selectDate(iso: String) {
        dateIso = iso
        showError(b.dateError, null)
        datePills.forEach { (d, pill) -> pill.root.isSelected = d == iso }
        val far = !datePills.containsKey(iso)
        otherPill.root.isSelected = far
        if (far) {
            val cal = Clinic.parseIso(iso)
            if (cal != null) {
                otherPill.middle.setCompoundDrawablesRelative(null, null, null, null)
                otherPill.top.text = Clinic.weekday(cal)
                otherPill.middle.text = cal.get(java.util.Calendar.DAY_OF_MONTH).toString()
                otherPill.bottom.text = Clinic.month(cal)
            }
        } else {
            resetOtherPill()
        }
        b.dateChosen.text = Clinic.longDate(iso)
        b.dateChosen.visibility = View.VISIBLE
        refreshSlots()
    }

    private fun openPicker() {
        if (supportFragmentManager.findFragmentByTag(TAG_PICKER) != null) return
        val today = Clinic.utcMillis(Clinic.todayIso())
        val last = Clinic.utcMillis(Clinic.iso(Clinic.day(Clinic.MAX_DAYS_AHEAD)))
        val constraints = CalendarConstraints.Builder()
            .setStart(today)
            .setEnd(last)
            .setOpenAt(dateIso?.let { Clinic.utcMillis(it) } ?: today)
            .setValidator(
                CompositeDateValidator.allOf(
                    listOf(DateValidatorPointForward.from(today), DateValidatorPointBackward.before(last + 1))
                )
            )
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pick_date)
            .setCalendarConstraints(constraints)
            .setSelection(dateIso?.let { Clinic.utcMillis(it) } ?: today)
            .build()
        listenTo(picker)
        picker.show(supportFragmentManager, TAG_PICKER)
    }

    private fun listenTo(picker: MaterialDatePicker<Long>) {
        picker.addOnPositiveButtonClickListener { millis -> selectDate(Clinic.isoFromUtcMillis(millis)) }
    }

    private fun buildSlots() {
        val bindings = listOf(b.timeMorning, b.timeAfternoon, b.timeEvening)
        Clinic.slots.forEachIndexed { i, slot ->
            val v = bindings[i]
            v.title.setText(slot.title)
            v.range.setText(slot.range)
            v.root.contentDescription = "${getString(slot.title)}, ${getString(slot.range)}"
            v.root.setOnClickListener {
                slotId = slot.id
                refreshSlots()
                showError(b.timeError, null)
            }
            slotViews[slot.id] = v
        }
    }

    /** Today's slots that have already ended can't be picked (same rule as the website). */
    private fun refreshSlots() {
        val isToday = dateIso == Clinic.todayIso()
        val hour = Clinic.hourNow()
        for (slot in Clinic.slots) {
            val v = slotViews[slot.id] ?: continue
            val open = !(isToday && hour >= slot.endsHour)
            if (!open && slotId == slot.id) slotId = null
            v.root.isEnabled = open
            v.root.alpha = if (open) 1f else 0.35f
            v.root.isSelected = slotId == slot.id
        }
    }

    // ------------------------------------------------------------------
    // Fields & validation
    // ------------------------------------------------------------------

    private fun bindFields() {
        b.phoneInput.setOnFocusChangeListener { _, hasFocus -> b.phoneBox.isSelected = hasFocus }
        b.phoneBox.setOnClickListener { b.phoneInput.requestFocus() }
        // Once a field has shown an error, re-check it as the person corrects it.
        b.nameInput.doAfterTextChanged { if (b.nameError.visibility == View.VISIBLE) checkName() }
        b.phoneInput.doAfterTextChanged { if (b.phoneError.visibility == View.VISIBLE) checkPhone() }
        b.submit.setOnClickListener { submit() }
    }

    private fun checkName(): Boolean {
        val ok = b.nameInput.text.toString().trim().length >= 2
        b.nameInput.isActivated = !ok
        showError(b.nameError, if (ok) null else getString(R.string.err_name))
        return ok
    }

    private fun checkPhone(): Boolean {
        val ok = Clinic.isValidPhone(b.phoneInput.text.toString())
        b.phoneBox.isActivated = !ok
        showError(b.phoneError, if (ok) null else getString(R.string.err_phone))
        return ok
    }

    /** Returns the first view that needs attention, or null when the form is complete. */
    private fun validate(): View? {
        refreshSlots()
        var first: View? = null
        if (!checkName()) first = first ?: b.nameInput
        if (!checkPhone()) first = first ?: b.phoneBox

        val serviceOk = Clinic.service(serviceId) != null
        showError(b.serviceError, if (serviceOk) null else getString(R.string.err_service))
        if (!serviceOk) first = first ?: b.serviceGroup

        val date = dateIso
        val dateMsg = when {
            date == null -> getString(R.string.err_date)
            date < Clinic.todayIso() -> getString(R.string.err_date_past)
            else -> null
        }
        showError(b.dateError, dateMsg)
        if (dateMsg != null) first = first ?: b.dateScroll

        val slotOk = Clinic.slot(slotId) != null
        showError(b.timeError, if (slotOk) null else getString(R.string.err_time))
        if (!slotOk) first = first ?: b.timeGroup
        return first
    }

    private fun showError(view: TextView, message: String?) {
        if (message == null) {
            view.visibility = View.GONE
            return
        }
        val wasHidden = view.visibility != View.VISIBLE
        view.text = message
        view.visibility = View.VISIBLE
        if (wasHidden && Motion.enabled(this)) {
            view.alpha = 0f
            view.translationY = -Motion.dp(this, 4f)
            view.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(Motion.ease).start()
        }
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    private fun submit() {
        if (BookingSender.inFlight) return
        val invalid = validate()
        if (invalid != null) {
            val top = topInContent(invalid) - dp(140)
            b.scroll.smoothScrollTo(0, maxOf(0, top))
            Motion.nudge(invalid)
            if (invalid === b.nameInput) b.nameInput.requestFocus()
            if (invalid === b.phoneBox) b.phoneInput.requestFocus()
            toaster.show(getString(R.string.check_fields), error = true)
            return
        }

        val name = b.nameInput.text.toString().trim()
        val phone = Clinic.normalisePhone(b.phoneInput.text.toString())
        val service = Clinic.service(serviceId)!!
        val slot = Clinic.slot(slotId)!!
        val date = dateIso!!
        val notes = b.notesInput.text.toString().trim()

        // Field names match the website's form, so emails look the same either way.
        val fields = linkedMapOf(
            "_subject" to getString(R.string.email_subject),
            "_template" to "table",
            "_captcha" to "false",
            "Patient Name" to name,
            "Phone" to phone,
            "Service" to service.emailValue,
            "Preferred Date" to Clinic.emailDate(date),
            "Preferred Time" to slot.emailValue,
        )
        if (notes.isNotEmpty()) fields["Notes"] = notes
        fields["Booked via"] = "Android app"

        hideKeyboard()
        setSending(true)
        BookingSender.send(this, fields, Store.Request(name, phone, service.label, date, slot.id))
    }

    private fun onResult(result: BookingSender.Result) {
        setSending(false)
        when (result) {
            is BookingSender.Result.Sent -> Store.lastRequest(this)?.let { showSent(it, animate = true) }
            is BookingSender.Result.NotActivated -> showFailure(inactive = true)
            is BookingSender.Result.Failed -> showFailure(inactive = false)
        }
    }

    private fun setSending(sending: Boolean) {
        b.submit.isEnabled = !sending
        b.submit.setText(if (sending) R.string.sending else R.string.request_appointment)
    }

    private fun showFailure(inactive: Boolean) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.fail_title)
            .setMessage(if (inactive) R.string.fail_inactive else R.string.fail_body)
        if (inactive) {
            dialog.setPositiveButton(R.string.use_email) { _, _ -> sendByEmail() }
            dialog.setNegativeButton(R.string.call) { _, _ -> dial() }
        } else {
            dialog.setPositiveButton(R.string.try_again) { _, _ -> submit() }
            dialog.setNegativeButton(R.string.use_email) { _, _ -> sendByEmail() }
            dialog.setNeutralButton(R.string.call) { _, _ -> dial() }
        }
        dialog.show()
    }

    /** Fallback: hand the same request to the phone's email app, addressed to the doctor. */
    private fun sendByEmail() {
        val service = Clinic.service(serviceId)
        val slot = Clinic.slot(slotId)
        val subject = getString(R.string.email_subject)
        val body = buildString {
            appendLine(subject)
            appendLine()
            appendLine("Patient Name: ${b.nameInput.text.toString().trim()}")
            appendLine("Phone: ${Clinic.normalisePhone(b.phoneInput.text.toString())}")
            appendLine("Service: ${service?.emailValue.orEmpty()}")
            appendLine("Preferred Date: ${dateIso?.let { Clinic.emailDate(it) }.orEmpty()}")
            appendLine("Preferred Time: ${slot?.emailValue.orEmpty()}")
            val notes = b.notesInput.text.toString().trim()
            if (notes.isNotEmpty()) appendLine("Notes: $notes")
            appendLine()
            append("Sent from the Mom’s Health Care Android app")
        }
        val uri = Uri.parse("mailto:${Clinic.EMAIL}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        launch(
            Intent(Intent.ACTION_SENDTO, uri)
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(Clinic.EMAIL))
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, body)
        )
    }

    // ------------------------------------------------------------------
    // Sent
    // ------------------------------------------------------------------

    private fun showSent(r: Store.Request, animate: Boolean) {
        showingSent = true
        b.barTitle.alpha = 0f
        b.sentBody.text = getString(R.string.sent_body, Clinic.prettyPhone(r.phone))

        b.summary.removeAllViews()
        val slot = Clinic.slot(r.slotId)
        val rows = listOf(
            getString(R.string.sent_patient) to r.name,
            getString(R.string.f_service) to r.serviceLabel,
            getString(R.string.f_date) to Clinic.longDate(r.dateIso),
            getString(R.string.f_time) to (slot?.let { "${getString(it.title)}, ${getString(it.range)}" } ?: ""),
        )
        rows.forEachIndexed { i, (label, value) ->
            if (i > 0) b.summary.addView(View(this).apply { setBackgroundColor(color(R.color.line)) }, ViewGroup.LayoutParams.MATCH_PARENT, dp(1).coerceAtLeast(1))
            val row = ItemSummaryRowBinding.inflate(layoutInflater, b.summary, true)
            row.label.text = label
            row.value.text = value
        }

        val reveal = {
            b.form.visibility = View.GONE
            b.success.visibility = View.VISIBLE
            b.scroll.scrollTo(0, 0)
            val check = AnimatedVectorDrawableCompat.create(this, R.drawable.avd_check)
            b.check.setImageDrawable(check)
            check?.start()
            if (animate && Motion.enabled(this)) {
                Motion.riseIn(listOf(b.sentTitle, b.sentBody, b.summary, b.done, b.sentCall), startDelay = 260, stagger = 70)
            }
        }
        if (animate && Motion.enabled(this)) {
            b.form.animate()
                .alpha(0f)
                .translationY(-Motion.dp(this, 16f))
                .setDuration(220)
                .setInterpolator(Motion.easeInOut)
                .withEndAction {
                    b.form.alpha = 1f
                    b.form.translationY = 0f
                    reveal()
                }
                .start()
        } else {
            reveal()
        }
    }

    // ------------------------------------------------------------------

    private fun hideKeyboard() {
        currentFocus?.clearFocus()
        WindowCompat.getInsetsController(window, b.root).hide(WindowInsetsCompat.Type.ime())
    }

    private fun topInContent(v: View): Int {
        var y = 0
        var cur: View = v
        while (cur !== b.content) {
            y += cur.top
            cur = cur.parent as? View ?: break
        }
        return y
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Int) = Motion.dp(this, v.toFloat()).toInt()
}
