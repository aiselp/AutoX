package com.stardust.autojs.core.ui.inflater.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.stardust.autojs.R
import com.stardust.autojs.core.ui.inflater.ImageLoader
import java.util.regex.Pattern

/**
 * Created by Stardust on 2017/11/3.
 */
open class Drawables {
    private val drawableClass = R.drawable::class.java
    private val attrClass = R.attr::class.java
    private var imageLoader = sDefaultImageLoader

    fun parse(context: Context, value: String): Drawable? {
        if (value.startsWith("@color/") || value.startsWith("@android:color/") || value.startsWith("#")) {
            return Colors.parse(context, value).toDrawable()
        }
        if (value.startsWith("?")) {
            return loadAttrResources(context, value)
        }
        return if (value.startsWith("file://")) {
            decodeImage(value.substring(7))
        } else loadDrawableResources(context, value)
    }

    private fun loadDrawableResources(context: Context, value: String): Drawable? {
        val resId = try {
            val field = if (value.startsWith("@drawable/"))
                drawableClass.getField(value.substring(10))
            else drawableClass.getField(value)
            field.get(null) as Int
        } catch (e: Exception) {
            0
        }
        if (resId == 0) throw Resources.NotFoundException("drawable not found: $value")
        return ContextCompat.getDrawable(context, resId)
    }

    fun loadAttrResources(context: Context, value: String): Drawable? {
        val field = attrClass.getField(
            if (value.startsWith("?attr/")) value.substring(6) else value.substring(1)
        )
        val attr = intArrayOf(
            field.getInt(null)
        )
        val ta = context.obtainStyledAttributes(attr)
        val drawable = ta.getDrawable(0 /* index */)
        ta.recycle()
        return drawable
    }

    open fun decodeImage(path: String?): Drawable? {
        return BitmapDrawable(BitmapFactory.decodeFile(path))
    }

    fun parse(view: View, name: String): Drawable? {
        return parse(view.context, name)
    }

    fun loadInto(view: ImageView?, uri: Uri?) {
        imageLoader.loadInto(view, uri)
    }

    fun loadIntoBackground(view: View?, uri: Uri?) {
        imageLoader.loadIntoBackground(view, uri)
    }

    fun <V : ImageView> setupWithImage(view: V, value: String) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            loadInto(view, value.toUri())
        } else if (value.startsWith("data:")) {
            loadDataInto(view, value)
        } else {
            view.setImageDrawable(parse(view, value))
        }
    }

    private fun loadDataInto(view: ImageView, data: String) {
        val bitmap = loadBase64Data(data)
        view.setImageBitmap(bitmap)
    }

    fun setupWithViewBackground(view: View, value: String) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            loadIntoBackground(view, value.toUri())
        } else {
            view.background = parse(view, value)
        }
    }

    private class DefaultImageLoader : ImageLoader {
        override fun loadInto(imageView: ImageView, uri: Uri?) {
            Glide.with(imageView).load(uri).into(imageView)
        }

        override fun loadIntoBackground(view: View, uri: Uri?) {
            Glide.with(view)
                .load(uri)
                .into(object : SimpleTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        view.background = resource
                    }
                })
        }

        override fun load(view: View, uri: Uri?): Drawable {
            throw UnsupportedOperationException()
        }

        override fun load(view: View, uri: Uri?, drawableCallback: ImageLoader.DrawableCallback) {
            Glide.with(view)
                .load(uri)
                .into(object : SimpleTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        drawableCallback.onLoaded(resource)
                    }
                })
        }

        override fun load(view: View, uri: Uri?, bitmapCallback: ImageLoader.BitmapCallback) {
            Glide.with(view).asBitmap().load(uri).into(object : SimpleTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    bitmapCallback.onLoaded(resource)
                }
            })
        }
    }

    companion object {
        private val DATA_PATTERN = Pattern.compile("data:(\\w+/\\w+);base64,(.+)")
        private var sDefaultImageLoader: ImageLoader = DefaultImageLoader()

        fun loadBase64Data(data: String): Bitmap {
            val matcher = DATA_PATTERN.matcher(data)
            val base64: String? = if (!matcher.matches() || matcher.groupCount() != 2) {
                data
            } else {
                val mimeType = matcher.group(1)
                matcher.group(2)
            }
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}
