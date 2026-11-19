package com.naturalai.camerapro.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Size
import com.naturalai.camerapro.domain.model.CameraCapabilitiesInfo
import java.util.Locale

class CameraCapabilityInspector(private val context: Context) {

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    fun inspectAllCameras(): List<CameraCapabilitiesInfo> {
        val ids = try {
            cameraManager.cameraIdList
        } catch (_: Exception) {
            emptyArray()
        }
        return ids.mapNotNull { id ->
            try {
                inspectCamera(id)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun inspectCamera(cameraId: String): CameraCapabilitiesInfo {
        val c = cameraManager.getCameraCharacteristics(cameraId)

        val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "UNSUPPORTED"
        }

        val hwLevel = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNSUPPORTED"
        }

        val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val supportsManualSensor = capabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        )
        val supportsManualPostProcessing = capabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING
        )
        val supportsRawCap = capabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
        )

        val streamMap = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawFormats = mutableListOf<String>()
        var maxRawSize: Size? = null

        val candidateRawFormats = listOf(
            ImageFormat.RAW_SENSOR to "RAW_SENSOR (16-bit)",
            ImageFormat.RAW10 to "RAW10",
            ImageFormat.RAW12 to "RAW12",
            ImageFormat.RAW_PRIVATE to "RAW_PRIVATE"
        )

        if (streamMap != null) {
            for ((fmt, label) in candidateRawFormats) {
                val sizes = streamMap.getOutputSizes(fmt)
                if (sizes != null && sizes.isNotEmpty()) {
                    rawFormats.add(label)
                    if (fmt == ImageFormat.RAW_SENSOR || maxRawSize == null) {
                        maxRawSize = sizes.maxByOrNull { it.width * it.height }
                    }
                }
            }
        }

        val afModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        val supportsAutofocus = afModes.any {
            it == CameraCharacteristics.CONTROL_AF_MODE_AUTO ||
                it == CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE ||
                it == CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO ||
                it == CameraCharacteristics.CONTROL_AF_MODE_MACRO
        }

        val minFocusDist = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        val supportsManualFocus = (minFocusDist != null && minFocusDist > 0f) &&
            afModes.contains(CameraMetadata.CONTROL_AF_MODE_OFF)

        val isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val aeCompRange = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val aeCompStep = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat()
        val aeLockAvailable = c.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) ?: false
        val awbLockAvailable = c.get(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) ?: false
        val awbModes = c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList() ?: emptyList()

        val focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val physicalSize = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val physicalSizeText = physicalSize?.let {
            String.format(Locale.US, "%.2f x %.2f mm", it.width, it.height)
        }

        val maxJpegSize = streamMap?.getOutputSizes(ImageFormat.JPEG)
            ?.maxByOrNull { it.width * it.height }

        return CameraCapabilitiesInfo(
            cameraId = cameraId,
            lensFacingLabel = facing,
            sensorOrientationDeg = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
            hardwareLevelLabel = hwLevel,
            supportsManualSensor = supportsManualSensor,
            supportsManualPostProcessing = supportsManualPostProcessing,
            supportsRawCapability = supportsRawCap && rawFormats.isNotEmpty(),
            supportedRawFormats = rawFormats,
            supportsAutofocus = supportsAutofocus,
            supportsManualFocus = supportsManualFocus,
            minimumFocusDistanceDiopters = minFocusDist,
            isoRange = isoRange,
            exposureTimeRangeNs = exposureRange,
            exposureCompensationRange = if (aeCompRange != null && (aeCompRange.lower != 0 || aeCompRange.upper != 0)) aeCompRange else null,
            exposureCompensationStep = aeCompStep,
            aeLockAvailable = aeLockAvailable,
            awbLockAvailable = awbLockAvailable,
            supportedAwbModes = awbModes,
            focalLengthsMm = focalLengths,
            sensorPhysicalSizeMm = physicalSizeText,
            maxJpegSize = maxJpegSize,
            maxRawSize = maxRawSize
        )
    }
}
