package com.naturalai.camerapro.domain.model

import android.util.Range
import android.util.Size

data class CameraCapabilitiesInfo(
    val cameraId: String,
    val lensFacingLabel: String,
    val sensorOrientationDeg: Int,
    val hardwareLevelLabel: String,
    val supportsManualSensor: Boolean,
    val supportsManualPostProcessing: Boolean,
    val supportsRawCapability: Boolean,
    val supportedRawFormats: List<String>,
    val supportsAutofocus: Boolean,
    val supportsManualFocus: Boolean,
    val minimumFocusDistanceDiopters: Float?,
    val isoRange: Range<Int>?,
    val exposureTimeRangeNs: Range<Long>?,
    val exposureCompensationRange: Range<Int>?,
    val exposureCompensationStep: Float?,
    val aeLockAvailable: Boolean,
    val awbLockAvailable: Boolean,
    val supportedAwbModes: List<Int>,
    val focalLengthsMm: FloatArray?,
    val sensorPhysicalSizeMm: String?,
    val maxJpegSize: Size?,
    val maxRawSize: Size?
)
