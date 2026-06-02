package com.smartalarm.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object ImageUtils {

    fun fileToBase64(path: String, maxDim: Int = 768): String {
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bmp = BitmapFactory.decodeFile(path, opts) ?: return ""
        val scaled = scaleBitmap(bmp, maxDim)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun uriToBase64(context: Context, uri: Uri, maxDim: Int = 768): String {
        val stream = context.contentResolver.openInputStream(uri) ?: return ""
        val bmp = BitmapFactory.decodeStream(stream) ?: return ""
        stream.close()
        val scaled = scaleBitmap(bmp, maxDim)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmap(bmp: Bitmap, maxDim: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= maxDim && h <= maxDim) return bmp
        val ratio = maxDim.toFloat() / maxOf(w, h)
        val matrix = Matrix().apply { postScale(ratio, ratio) }
        return Bitmap.createBitmap(bmp, 0, 0, w, h, matrix, true)
    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, filename: String): String {
        val dir = File(context.filesDir, "targets")
        dir.mkdirs()
        val file = File(dir, filename)
        val out = file.outputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.close()
        return file.absolutePath
    }

    fun copyFileToInternal(context: Context, srcPath: String, destName: String): String {
        val dir = File(context.filesDir, "targets")
        dir.mkdirs()
        val dest = File(dir, destName)
        File(srcPath).copyTo(dest, overwrite = true)
        return dest.absolutePath
    }

    fun deleteFile(path: String) {
        try { File(path).delete() } catch (_: Exception) {}
    }
}
