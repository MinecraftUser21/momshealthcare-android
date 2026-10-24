package com.momshealthcare.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.momshealthcare.app.databinding.ItemBulletBinding
import com.momshealthcare.app.databinding.SheetServiceBinding

/** What a service is, how to prepare for it, and a button to book it. */
class ServiceSheet : BottomSheetDialogFragment() {

    private var _b: SheetServiceBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = SheetServiceBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val svc = Clinic.service(arguments?.getString(ARG_ID))
        if (svc == null) {
            dismiss()
            return
        }
        b.icon.setImageResource(svc.icon)
        b.name.setText(svc.name)
        b.desc.setText(svc.desc)
        for (line in resources.getStringArray(svc.prep)) {
            ItemBulletBinding.inflate(layoutInflater, b.prep, true).text.text = line
        }
        b.book.text = getString(R.string.book_this, svc.label)
        b.book.setOnClickListener {
            (activity as? MainActivity)?.openBooking(svc.id)
            dismiss()
        }

        val basePadding = b.sheetContent.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.sheetContent) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePadding + nav)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_ID = "id"
        fun newInstance(id: String) = ServiceSheet().apply { arguments = bundleOf(ARG_ID to id) }
    }
}
