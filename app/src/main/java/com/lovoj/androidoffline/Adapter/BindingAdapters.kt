package com.lovoj.androidoffline.Adapter



import android.view.View
import androidx.databinding.BindingAdapter

@BindingAdapter("isVisible")
fun View.setIsVisible(visible: Boolean?) {
    visibility = if (visible == true) View.VISIBLE else View.GONE
}
