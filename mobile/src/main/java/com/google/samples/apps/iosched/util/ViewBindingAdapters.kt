/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.iosched.util

import android.databinding.BindingAdapter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v4.view.ViewPager
import android.support.v7.content.res.AppCompatResources
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.samples.apps.iosched.R
import timber.log.Timber

@BindingAdapter("invisibleUnless")
fun invisibleUnless(view: View, visible: Boolean) {
    view.visibility = if (visible) VISIBLE else INVISIBLE
}

@BindingAdapter("goneUnless")
fun goneUnless(view: View, visible: Boolean) {
    view.visibility = if (visible) VISIBLE else GONE
}

@BindingAdapter("pageMargin")
fun pageMargin(viewPager: ViewPager, pageMargin: Float) {
    viewPager.pageMargin = pageMargin.toInt()
}

@BindingAdapter("clipToCircle")
fun clipToCircle(view: View, clip: Boolean) {
    view.clipToOutline = clip
    view.outlineProvider = if (clip) CircularOutlineProvider else null
}

@BindingAdapter(value = ["imageUrl", "placeholder"], requireAll = false)
fun imageUrl(imageView: ImageView, imageUrl: Uri?, placeholder: Drawable?) {
    val placeholderDrawable = placeholder ?: AppCompatResources.getDrawable(
        imageView.context, R.drawable.generic_placeholder)
    when (imageUrl) {
        null -> {
            Timber.d("Unsetting image url")
            Glide.with(imageView)
                .load(placeholderDrawable)
                .into(imageView)
        }
        else -> {
            Glide.with(imageView)
                .load(imageUrl)
                .apply(RequestOptions().placeholder(placeholderDrawable))
                .into(imageView)
        }
    }
}

@BindingAdapter(value = ["imageUrl", "placeholder"], requireAll = false)
fun imageUrl(imageView: ImageView, imageUrl: String?, placeholder: Drawable?) {
    imageUrl(imageView, Uri.parse(imageUrl), placeholder)
}
