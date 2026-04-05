package com.equationl.paddleocr4android.Util.paddle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.os.Environment
import androidx.core.graphics.scale
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

object Utils {
    private val TAG: String = Utils::class.java.simpleName

    fun copyFileFromAssets(appCtx: Context, srcPath: String, dstPath: String) {
        if (srcPath.isEmpty() || dstPath.isEmpty()) {
            return
        }
        var `is`: InputStream? = null
        var os: OutputStream? = null
        try {
            `is` = BufferedInputStream(appCtx.assets.open(srcPath))
            os = BufferedOutputStream(FileOutputStream(File(dstPath)))
            val buffer = ByteArray(1024)
            var length = 0
            while ((`is`.read(buffer).also { length = it }) != -1) {
                os.write(buffer, 0, length)
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                os!!.close()
                `is`!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun copyDirectoryFromAssets(appCtx: Context, srcDir: String, dstDir: String) {
        if (srcDir.isEmpty() || dstDir.isEmpty()) {
            return
        }
        try {
            if (!File(dstDir).exists()) {
                File(dstDir).mkdirs()
            }
            for (fileName in appCtx.assets.list(srcDir)!!) {
                val srcSubPath = srcDir + File.separator + fileName
                val dstSubPath = dstDir + File.separator + fileName
                if (File(srcSubPath).isDirectory) {
                    copyDirectoryFromAssets(appCtx, srcSubPath, dstSubPath)
                } else {
                    copyFileFromAssets(appCtx, srcSubPath, dstSubPath)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun parseFloatsFromString(string: String, delimiter: String): FloatArray {
        val pieces: Array<String?> =
            string.trim { it <= ' ' }.lowercase(Locale.getDefault()).split(delimiter.toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
        val floats = FloatArray(pieces.size)
        for (i in pieces.indices) {
            floats[i] = pieces[i]!!.trim { it <= ' ' }.toFloat()
        }
        return floats
    }

    fun parseLongsFromString(string: String, delimiter: String): LongArray {
        val pieces: Array<String?> =
            string.trim { it <= ' ' }.lowercase(Locale.getDefault()).split(delimiter.toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()
        val longs = LongArray(pieces.size)
        for (i in pieces.indices) {
            longs[i] = pieces[i]!!.trim { it <= ' ' }.toLong()
        }
        return longs
    }

    val sDCardDirectory: String
        get() = Environment.getExternalStorageDirectory().absolutePath

    val isSupportedNPU: Boolean
        get() = false
    // String hardware = android.os.Build.HARDWARE;
    // return hardware.equalsIgnoreCase("kirin810") || hardware.equalsIgnoreCase("kirin990");

    fun resizeWithStep(bitmap: Bitmap, maxLength: Int, step: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxWH = max(width, height)
        var ratio = 1f
        var newWidth = width
        var newHeight = height
        if (maxWH > maxLength) {
            ratio = maxLength * 1.0f / maxWH
            newWidth = floor((ratio * width).toDouble()).toInt()
            newHeight = floor((ratio * height).toDouble()).toInt()
        }

        newWidth -= newWidth % step
        if (newWidth == 0) {
            newWidth = step
        }
        newHeight -= newHeight % step
        if (newHeight == 0) {
            newHeight = step
        }
        return bitmap.scale(newWidth, newHeight)
    }

    fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> return bitmap
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        try {
            val bmRotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            bitmap.recycle()
            return bmRotated
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return null
        }
    }
}
