package com.naturalai.camerapro.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.naturalai.camerapro.camera.DngRawCaptureManager
import com.naturalai.camerapro.domain.model.CapturedPhotoRecord
import com.naturalai.camerapro.domain.model.PhotographyMode
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStoreGalleryRepository(private val context: Context) {

    private val dngManager = DngRawCaptureManager()
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun saveCapture(
        finalBitmap: Bitmap,
        beforeBitmap: Bitmap?,
        rawImage: Image?,
        captureResult: TotalCaptureResult?,
        characteristics: CameraCharacteristics?,
        cameraId: String,
        mode: PhotographyMode,
        whiteBalanceLabel: String,
        aiApplied: Boolean,
        engineLabel: String
    ): CapturedPhotoRecord {
        val now = Date()
        val stamp = fileDateFormat.format(now)
        val baseName = "NACP_${stamp}_${mode.fileTag}"

        val jpegUri = insertBitmapToMediaStore(finalBitmap, "$baseName.jpg")

        // Save internal before-AI preview for instant Before/After inspection
        val beforeUri = beforeBitmap?.let { bmp ->
            val file = File(context.cacheDir, "${baseName}_before.jpg")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            Uri.fromFile(file)
        }

        var dngUri: Uri? = null
        if (rawImage != null && captureResult != null && characteristics != null) {
            val orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            dngUri = insertDngToMediaStore(
                name = "$baseName.dng",
                characteristics = characteristics,
                captureResult = captureResult,
                rawImage = rawImage,
                orientationDeg = orientation
            )
            rawImage.close()
        }

        val isoVal = captureResult?.get(CaptureResult.SENSOR_SENSITIVITY)?.toString() ?: "AUTO"
        val expNs = captureResult?.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val shutterStr = if (expNs != null && expNs > 0) {
            if (expNs >= 1_000_000_000L) "${"%.2f".format(expNs / 1e9)}s"
            else "1/${(1_000_000_000.0 / expNs).toInt().coerceAtLeast(1)}s"
        } else "AUTO"

        val formatLabel = when {
            jpegUri != null && dngUri != null -> "JPEG + DNG"
            dngUri != null -> "DNG"
            else -> "JPEG"
        }

        return CapturedPhotoRecord(
            id = baseName,
            displayName = "$baseName.jpg",
            jpegUri = jpegUri,
            dngUri = dngUri,
            beforeAiUri = beforeUri,
            dateTimeText = displayDateFormat.format(now),
            modeName = mode.displayName,
            cameraId = cameraId,
            iso = isoVal,
            shutterSpeed = shutterStr,
            whiteBalance = whiteBalanceLabel,
            resolution = "${finalBitmap.width} x ${finalBitmap.height}",
            formatLabel = formatLabel,
            aiApplied = aiApplied,
            engineLabel = engineLabel
        )
    }

    private fun insertBitmapToMediaStore(bitmap: Bitmap, filename: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NaturalAICameraPro")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun insertDngToMediaStore(
        name: String,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        rawImage: Image,
        orientationDeg: Int
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NaturalAICameraPro")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            dngManager.writeDng(characteristics, captureResult, rawImage, orientationDeg, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    fun readExifSummary(jpegBytes: ByteArray): Map<String, String> {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(jpegBytes))
            mapOf(
                "Make" to (exif.getAttribute(ExifInterface.TAG_MAKE) ?: Build.MANUFACTURER),
                "Model" to (exif.getAttribute(ExifInterface.TAG_MODEL) ?: Build.MODEL),
                "ISO" to (exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: "UNAVAILABLE"),
                "Exposure" to (exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) ?: "UNAVAILABLE")
            )
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
