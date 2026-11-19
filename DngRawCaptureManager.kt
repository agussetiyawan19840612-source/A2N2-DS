package com.naturalai.camerapro.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import java.io.OutputStream

class DngRawCaptureManager {

    fun writeDng(
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        rawImage: Image,
        orientationDeg: Int,
        outputStream: OutputStream
    ) {
        val dngCreator = DngCreator(characteristics, captureResult)
        dngCreator.use { creator ->
            val exifOrientation = when ((orientationDeg % 360 + 360) % 360) {
                90 -> android.media.ExifInterface.ORIENTATION_ROTATE_90
                180 -> android.media.ExifInterface.ORIENTATION_ROTATE_180
                270 -> android.media.ExifInterface.ORIENTATION_ROTATE_270
                else -> android.media.ExifInterface.ORIENTATION_NORMAL
            }
            creator.setOrientation(exifOrientation)
            creator.setDescription("Captured with Natural AI Camera Pro - Direct Sensor Edition")
            creator.writeImage(outputStream, rawImage)
        }
    }
}
