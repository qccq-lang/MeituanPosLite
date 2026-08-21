package com.pos.lite.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import java.io.InputStream

object ImageUtil {
    fun loadSafeImage(context: Context, uriString: String, imageView: ImageView) {
        if (uriString.isEmpty()) {
            imageView.setImageDrawable(null)
            return
        }
        try {
            val uri = Uri.parse(uriString)
            var input: InputStream? = context.contentResolver.openInputStream(uri)

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input?.close()

            // 计算压缩采样率 (防止收银机图片过多引起内存溢出)
            options.inSampleSize = calculateInSampleSize(options, 200, 200)
            options.inJustDecodeBounds = false

            input = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(input, null, options)
            input?.close()

            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            imageView.setImageDrawable(null)
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}