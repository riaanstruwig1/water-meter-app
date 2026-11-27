package com.watermeter.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Photos {
    fun dir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

    fun newFile(context: Context): File =
        File(dir(context), "meter_${System.currentTimeMillis()}.jpg")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Copies a picked image (e.g. from the gallery) into app storage. */
    fun importFrom(context: Context, uri: Uri): File? {
        val dest = newFile(context)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return null
            dest
        } catch (e: Exception) {
            dest.delete()
            null
        }
    }

    /** Decodes a photo scaled down to roughly [maxSide] px, rotated upright from EXIF. */
    fun decode(path: String, maxSide: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val degrees = try {
            when (ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
        if (degrees == 0f) return bmp
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}

@Composable
fun rememberPhoto(path: String?, maxSide: Int): State<Bitmap?> =
    produceState<Bitmap?>(initialValue = null, path, maxSide) {
        value = if (path == null) null else withContext(Dispatchers.IO) { Photos.decode(path, maxSide) }
    }
