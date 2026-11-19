package com.naturalai.camerapro.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.naturalai.camerapro.domain.model.CameraCapabilitiesInfo
import com.naturalai.camerapro.domain.model.CaptureParameters
import com.naturalai.camerapro.domain.model.LiveFrameTelemetry
import com.naturalai.camerapro.domain.model.OpticalProfile
import com.naturalai.camerapro.domain.model.OutputFormat
import com.naturalai.camerapro.domain.model.WhiteBalancePreset
import com.naturalai.camerapro.processing.FrameAnalyzer
import java.util.concurrent.Executors
import kotlin.math.abs

data class RawAndJpegResult(
    val jpegBytes: ByteArray?,
    val rawImage: Image?,
    val captureResult: TotalCaptureResult,
    val characteristics: CameraCharacteristics
)

class Camera2Controller(
    private val context: Context,
    private val onTelemetryUpdate: (LiveFrameTelemetry) -> Unit,
    private val onError: (String) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val frameAnalyzer = FrameAnalyzer()

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var currentCharacteristics: CameraCharacteristics? = null
    private var currentCapabilities: CameraCapabilitiesInfo? = null

    private var previewSurface: Surface? = null
    private var yuvReader: ImageReader? = null
    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null

    private var latestParams = CaptureParameters()
    private var latestOpticalProfile: OpticalProfile? = null
    private var lastLaplacianScore = 0f

    private var pendingJpegBytes: ByteArray? = null
    private var pendingRawImage: Image? = null
    private var pendingCaptureResult: TotalCaptureResult? = null
    private var pendingCaptureCallback: ((RawAndJpegResult) -> Unit)? = null
    private var expectedRawInCapture = false

    fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("Camera2EngineThread").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (_: InterruptedException) {
        }
        backgroundThread = null
        backgroundHandler = null
    }

    @SuppressLint("MissingPermission")
    fun openCamera(
        cameraId: String,
        capabilities: CameraCapabilitiesInfo,
        surface: Surface,
        params: CaptureParameters,
        opticalProfile: OpticalProfile
    ) {
        startBackgroundThread()
        closeCamera()

        previewSurface = surface
        currentCapabilities = capabilities
        currentCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        latestParams = params
        latestOpticalProfile = opticalProfile

        val jpegSize = capabilities.maxJpegSize ?: Size(1920, 1080)
        jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 4).apply {
            setOnImageAvailableListener({ reader ->
                val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buf = img.planes[0].buffer
                val bytes = ByteArray(buf.remaining())
                buf.get(bytes)
                img.close()
                synchronized(this@Camera2Controller) {
                    pendingJpegBytes = bytes
                    tryDispatchPendingCapture()
                }
            }, backgroundHandler)
        }

        yuvReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val analysis = frameAnalyzer.analyzeYuvFrame(img)
                img.close()
                val delta = analysis.laplacianFocusScore - lastLaplacianScore
                lastLaplacianScore = analysis.laplacianFocusScore
                onTelemetryUpdate(
                    LiveFrameTelemetry(
                        actualIso = latestTelemetryIso,
                        actualExposureTimeNs = latestTelemetryExpNs,
                        actualFocusDistanceDiopters = latestTelemetryFocusDist,
                        actualAfState = latestTelemetryAfState,
                        actualAeState = latestTelemetryAeState,
                        actualWbGains = latestTelemetryWb,
                        redHistogram = analysis.redBins,
                        greenHistogram = analysis.greenBins,
                        blueHistogram = analysis.blueBins,
                        lumaHistogram = analysis.lumaBins,
                        highlightClipPercent = analysis.highlightClipPercent,
                        shadowCrushPercent = analysis.shadowCrushPercent,
                        laplacianFocusScore = analysis.laplacianFocusScore,
                        focusTrendDelta = delta
                    )
                )
            }, backgroundHandler)
        }

        if (capabilities.supportsRawCapability && capabilities.maxRawSize != null) {
            val rawSize = capabilities.maxRawSize
            val rawFormat = when {
                capabilities.supportedRawFormats.any { it.startsWith("RAW_SENSOR") } -> ImageFormat.RAW_SENSOR
                capabilities.supportedRawFormats.contains("RAW10") -> ImageFormat.RAW10
                capabilities.supportedRawFormats.contains("RAW12") -> ImageFormat.RAW12
                else -> ImageFormat.RAW_SENSOR
            }
            rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, rawFormat, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val img = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    synchronized(this@Camera2Controller) {
                        pendingRawImage?.close()
                        pendingRawImage = img
                        tryDispatchPendingCapture()
                    }
                }, backgroundHandler)
            }
        }

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    onError("Camera2 device error code: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            onError("Failed to open camera $cameraId: ${e.message}")
        }
    }

    @Volatile private var latestTelemetryIso: Int? = null
    @Volatile private var latestTelemetryExpNs: Long? = null
    @Volatile private var latestTelemetryFocusDist: Float? = null
    @Volatile private var latestTelemetryAfState: String = "UNAVAILABLE"
    @Volatile private var latestTelemetryAeState: String = "UNAVAILABLE"
    @Volatile private var latestTelemetryWb: String = "AUTO"

    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val pSurface = previewSurface ?: return
        val ySurface = yuvReader?.surface ?: return
        val jSurface = jpegReader?.surface ?: return

        val targets = mutableListOf(pSurface, ySurface, jSurface)
        rawReader?.surface?.let { targets.add(it) }

        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                updateRepeatingPreview()
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                onError("Failed to configure Camera2 capture session")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val configs = targets.map { OutputConfiguration(it) }
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                configs,
                executor,
                stateCallback
            )
            device.createCaptureSession(sessionConfig)
        } else {
            @Suppress("DEPRECATION")
            device.createCaptureSession(targets, stateCallback, backgroundHandler)
        }
    }

    fun updateParameters(params: CaptureParameters, opticalProfile: OpticalProfile) {
        latestParams = params
        latestOpticalProfile = opticalProfile
        updateRepeatingPreview()
    }

    private fun updateRepeatingPreview() {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val pSurface = previewSurface ?: return
        val ySurface = yuvReader?.surface ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(pSurface)
                addTarget(ySurface)
            }
            applyCamera2Controls(builder, latestParams, latestOpticalProfile)
            session.setRepeatingRequest(
                builder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        extractResultTelemetry(result)
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            onError("Preview request failed: ${e.message}")
        }
    }

    private fun extractResultTelemetry(result: CaptureResult) {
        latestTelemetryIso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        latestTelemetryExpNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        latestTelemetryFocusDist = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
        latestTelemetryAfState = when (result.get(CaptureResult.CONTROL_AF_STATE)) {
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> "LOCKED"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> "FOCUSED"
            CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN,
            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> "SCANNING"
            CaptureResult.CONTROL_AF_STATE_INACTIVE -> "MANUAL/OFF"
            else -> "UNAVAILABLE"
        }
        latestTelemetryAeState = when (result.get(CaptureResult.CONTROL_AE_STATE)) {
            CaptureResult.CONTROL_AE_STATE_CONVERGED -> "CONVERGED"
            CaptureResult.CONTROL_AE_STATE_LOCKED -> "LOCKED"
            CaptureResult.CONTROL_AE_STATE_SEARCHING -> "METERING"
            else -> "MANUAL/OFF"
        }
        val vec = result.get(CaptureResult.COLOR_CORRECTION_GAINS)
        latestTelemetryWb = if (vec != null) {
            "R:${"%.2f".format(vec.red)} G:${"%.2f".format(vec.greenEven)} B:${"%.2f".format(vec.blue)}"
        } else {
            latestParams.whiteBalancePreset.label
        }
    }

    private fun applyCamera2Controls(
        builder: CaptureRequest.Builder,
        params: CaptureParameters,
        profile: OpticalProfile?
    ) {
        val caps = currentCapabilities ?: return
        val chars = currentCharacteristics ?: return

        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        // Manual ISO / Shutter only if hardware supports MANUAL_SENSOR
        if (caps.supportsManualSensor && (!params.isoAuto || !params.shutterAuto)) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            val iso = if (!params.isoAuto && caps.isoRange != null) {
                params.manualIso.coerceIn(caps.isoRange.lower, caps.isoRange.upper)
            } else {
                latestTelemetryIso ?: caps.isoRange?.lower ?: 100
            }
            val expNs = if (!params.shutterAuto && caps.exposureTimeRangeNs != null) {
                params.manualShutterNs.coerceIn(
                    caps.exposureTimeRangeNs.lower,
                    caps.exposureTimeRangeNs.upper
                )
            } else {
                latestTelemetryExpNs ?: 16_666_666L
            }
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expNs)
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            if (caps.exposureCompensationRange != null) {
                val clampedEv = params.exposureCompensationIndex.coerceIn(
                    caps.exposureCompensationRange.lower,
                    caps.exposureCompensationRange.upper
                )
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, clampedEv)
            }
            if (caps.aeLockAvailable) {
                builder.set(CaptureRequest.CONTROL_AE_LOCK, params.exposureLocked)
            }
        }

        // Autofocus vs Manual Focus vs Lensless Direct Sensor
        if (params.directSensorMode || !caps.supportsAutofocus || !params.autofocusEnabled) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            if (caps.supportsManualFocus && caps.minimumFocusDistanceDiopters != null) {
                val clampedDist = params.manualFocusDistanceDiopters.coerceIn(
                    0f,
                    caps.minimumFocusDistanceDiopters
                )
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, clampedDist)
            }
        } else {
            builder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            if (params.focusLocked) {
                builder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_IDLE
                )
            }
        }

        // White Balance & Direct Sensor Color Matrix
        when (params.whiteBalancePreset) {
            WhiteBalancePreset.AUTO -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
                if (caps.awbLockAvailable) {
                    builder.set(CaptureRequest.CONTROL_AWB_LOCK, params.exposureLocked)
                }
            }
            WhiteBalancePreset.DAYLIGHT ->
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT)
            WhiteBalancePreset.CLOUDY ->
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
            WhiteBalancePreset.TUNGSTEN ->
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT)
            WhiteBalancePreset.FLUORESCENT ->
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT)
            WhiteBalancePreset.MANUAL -> {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
                builder.set(
                    CaptureRequest.COLOR_CORRECTION_MODE,
                    CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
                )
                val gains = kelvinToRggbGains(params.manualKelvin, profile)
                builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
            }
        }

        // Digital Zoom (Hardware scalar crop region)
        val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (activeArray != null && params.digitalZoomRatio > 1.0f) {
            val z = params.digitalZoomRatio.coerceIn(1.0f, 8.0f)
            val cropW = (activeArray.width() / z).toInt()
            val cropH = (activeArray.height() / z).toInt()
            val left = (activeArray.width() - cropW) / 2
            val top = (activeArray.height() - cropH) / 2
            builder.set(
                CaptureRequest.SCALER_CROP_REGION,
                Rect(left, top, left + cropW, top + cropH)
            )
        } else if (activeArray != null) {
            builder.set(CaptureRequest.SCALER_CROP_REGION, activeArray)
        }

        // Direct Sensor overrides: disable aggressive stock sharpening / denoise / shading
        if (params.directSensorMode) {
            if (params.disableHardwareSharpening) {
                builder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF)
            }
            if (params.minimizeHardwareDenoise) {
                builder.set(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL
                )
            }
            if (params.disableLensCorrection) {
                builder.set(CaptureRequest.SHADING_MODE, CameraMetadata.SHADING_MODE_OFF)
                builder.set(
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_OFF
                )
            }
            builder.set(CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY)
        } else {
            builder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY)
            builder.set(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
            )
        }
    }

    private fun kelvinToRggbGains(kelvin: Int, profile: OpticalProfile?): RggbChannelVector {
        val t = (kelvin.coerceIn(2200, 9000) - 5500f) / 3500f
        val rTrim = profile?.redGainTrim ?: 1.0f
        val gTrim = profile?.greenGainTrim ?: 1.0f
        val bTrim = profile?.blueGainTrim ?: 1.0f
        val red = (1.75f + t * 0.55f).coerceIn(1.0f, 2.8f) * rTrim
        val green = 1.0f * gTrim
        val blue = (1.75f - t * 0.55f).coerceIn(1.0f, 2.8f) * bTrim
        return RggbChannelVector(red, green, green, blue)
    }

    fun captureStill(
        focusDistanceOverride: Float? = null,
        onCaptured: (RawAndJpegResult) -> Unit
    ) {
        val device = cameraDevice ?: return onError("Camera device not ready")
        val session = captureSession ?: return onError("Capture session not ready")
        val chars = currentCharacteristics ?: return
        val caps = currentCapabilities ?: return

        val wantRaw = (latestParams.outputFormat == OutputFormat.RAW_DNG ||
            latestParams.outputFormat == OutputFormat.RAW_AND_JPEG) &&
            caps.supportsRawCapability && rawReader != null

        synchronized(this) {
            pendingJpegBytes = null
            pendingRawImage?.close()
            pendingRawImage = null
            pendingCaptureResult = null
            pendingCaptureCallback = onCaptured
            expectedRawInCapture = wantRaw
        }

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            jpegReader?.surface?.let { builder.addTarget(it) }
            if (wantRaw) {
                rawReader?.surface?.let { builder.addTarget(it) }
            }
            val effectiveParams = if (focusDistanceOverride != null) {
                latestParams.copy(autofocusEnabled = false, manualFocusDistanceDiopters = focusDistanceOverride)
            } else {
                latestParams
            }
            applyCamera2Controls(builder, effectiveParams, latestOpticalProfile)
            builder.set(CaptureRequest.JPEG_QUALITY, 98.toByte())

            session.capture(
                builder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        synchronized(this@Camera2Controller) {
                            pendingCaptureResult = result
                            tryDispatchPendingCapture()
                        }
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            onError("Still capture failed: ${e.message}")
        }
    }

    private fun tryDispatchPendingCapture() {
        val result = pendingCaptureResult ?: return
        val jpeg = pendingJpegBytes ?: return
        if (expectedRawInCapture && pendingRawImage == null) return

        val chars = currentCharacteristics ?: return
        val callback = pendingCaptureCallback ?: return
        val rawImg = pendingRawImage

        pendingCaptureResult = null
        pendingJpegBytes = null
        pendingRawImage = null
        pendingCaptureCallback = null

        callback(
            RawAndJpegResult(
                jpegBytes = jpeg,
                rawImage = rawImg,
                captureResult = result,
                characteristics = chars
            )
        )
    }

    fun closeCamera() {
        try {
            captureSession?.close()
        } catch (_: Exception) {
        }
        captureSession = null
        try {
            cameraDevice?.close()
        } catch (_: Exception) {
        }
        cameraDevice = null
        jpegReader?.close()
        jpegReader = null
        yuvReader?.close()
        yuvReader = null
        rawReader?.close()
        rawReader = null
        pendingRawImage?.close()
        pendingRawImage = null
    }

    fun release() {
        closeCamera()
        stopBackgroundThread()
        executor.shutdown()
    }
}
