package com.momshealthcare.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import com.momshealthcare.app.databinding.ItemRowBinding

/** Adds an icon row (tips, services, settings) to a card. Pass [onClick] to make it tappable. */
fun ViewGroup.addRow(
    @DrawableRes icon: Int,
    title: CharSequence,
    body: CharSequence? = null,
    value: CharSequence? = null,
    onClick: (() -> Unit)? = null,
): ItemRowBinding {
    val row = ItemRowBinding.inflate(LayoutInflater.from(context), this, true)
    row.bindRow(icon, title, body, value, onClick)
    return row
}

fun ItemRowBinding.bindRow(
    @DrawableRes icon: Int,
    title: CharSequence,
    body: CharSequence? = null,
    value: CharSequence? = null,
    onClick: (() -> Unit)? = null,
) {
    this.icon.setImageResource(icon)
    this.title.text = title
    this.body.text = body
    this.body.visibility = if (body == null) View.GONE else View.VISIBLE
    this.value.text = value
    this.value.visibility = if (value == null) View.GONE else View.VISIBLE
    chevron.visibility = if (onClick != null) View.VISIBLE else View.GONE
    if (onClick != null) {
        root.isClickable = true
        root.isFocusable = true
        root.setOnClickListener { onClick() }
    } else {
        root.background = null
    }
}
