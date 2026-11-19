package com.naturalai.camerapro.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.naturalai.camerapro.camera.Camera2Controller
import com.naturalai.camerapro.camera.CameraCapabilityInspector
import com.naturalai.camerapro.camera.RawAndJpegResult
import com.naturalai.camerapro.data.MediaStoreGalleryRepository
import com.naturalai.camerapro.data.OpticalProfileRepository
import com.naturalai.camerapro.domain.model.CameraCapabilitiesInfo
import com.naturalai.camerapro.domain.model.CaptureParameters
import com.naturalai.camerapro.domain.model.CapturedPhotoRecord
import com.naturalai.camerapro.domain.model.LiveFrameTelemetry
import com.naturalai.camerapro.domain.model.NaturalAiConfig
import com.naturalai.camerapro.domain.model.OpticalProfile
import com.naturalai.camerapro.domain.model.PhotographyMode
import com.naturalai.camerapro.processing.HybridNaturalAiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class CameraUiState(
    val cameras: List<CameraCapabilitiesInfo> = emptyList(),
    val activeCamera: CameraCapabilitiesInfo? = null,
    val selectedMode: PhotographyMode = PhotographyMode.NATURAL_AUTO,
    val captureParams: CaptureParameters = CaptureParameters(),
    val aiConfig: NaturalAiConfig = NaturalAiConfig.naturalDefaults(),
    val opticalProfiles: List<OpticalProfile> = emptyList(),
    val selectedProfile: OpticalProfile = OpticalProfile.builtInDefaults().first(),
    val telemetry: LiveFrameTelemetry = LiveFrameTelemetry(),
    val galleryRecords: List<CapturedPhotoRecord> = emptyList(),
    val isCapturing: Boolean = false,
    val countdownRemaining: Int = 0,
    val statusMessage: String? = null,
    val aiEngineDescription: String = ""
)

class CameraViewModel(app: Application) : AndroidViewModel(app) {

    private val inspector = CameraCapabilityInspector(app)
    private val profileRepo = OpticalProfileRepository(app)
    private val galleryRepo = MediaStoreGalleryRepository(app)
    private val aiEngine = HybridNaturalAiEngine(app)

    private val _uiState = MutableStateFlow(
        CameraUiState(
            aiEngineDescription = aiEngine.engineDescription
        )
    )
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var previewSurface: Surface? = null

    private val controller = Camera2Controller(
        context = app,
        onTelemetryUpdate = { telemetry ->
            _uiState.value = _uiState.value.copy(telemetry = telemetry)
        },
        onError = { msg ->
            _uiState.value = _uiState.value.copy(statusMessage = msg, isCapturing = false)
        }
    )

    init {
        refreshCapabilities()
        loadOpticalProfiles()
    }

    fun refreshCapabilities() {
        val list = inspector.inspectAllCameras()
        val preferred = list.firstOrNull { it.lensFacingLabel == "Back" } ?: list.firstOrNull()
        _uiState.value = _uiState.value.copy(
            cameras = list,
            activeCamera = preferred
        )
    }

    private fun loadOpticalProfiles() {
        val profiles = profileRepo.loadProfiles()
        val selectedId = profileRepo.getSelectedProfileId()
        val selected = profiles.find { it.id == selectedId } ?: profiles.first()
        _uiState.value = _uiState.value.copy(
            opticalProfiles = profiles,
            selectedProfile = selected
        )
    }

    fun onPreviewSurfaceReady(surface: Surface) {
        previewSurface = surface
        openActiveCamera()
    }

    fun openActiveCamera() {
        val cam = _uiState.value.activeCamera ?: return
        val surf = previewSurface ?: return
        controller.openCamera(
            cameraId = cam.cameraId,
            capabilities = cam,
            surface = surf,
            params = _uiState.value.captureParams,
            opticalProfile = _uiState.value.selectedProfile
        )
    }

    fun selectCamera(cameraId: String) {
        val cam = _uiState.value.cameras.find { it.cameraId == cameraId } ?: return
        _uiState.value = _uiState.value.copy(activeCamera = cam)
        openActiveCamera()
    }

    fun selectMode(mode: PhotographyMode) {
        val current = _uiState.value.captureParams
        val updatedParams = when (mode) {
            PhotographyMode.DIRECT_SENSOR -> current.copy(
                directSensorMode = true,
                autofocusEnabled = false,
                disableHardwareSharpening = true,
                minimizeHardwareDenoise = true,
                disableLensCorrection = true
            )
            PhotographyMode.RAW_LAB -> current.copy(
                directSensorMode = true,
                disableHardwareSharpening = true,
                minimizeHardwareDenoise = true
            )
            PhotographyMode.MACRO, PhotographyMode.MICRO_MACRO -> current.copy(
                focusPeakingEnabled = true
            )
            else -> current
        }
        val updatedAi = if (mode.prefersDirectSensorDefaults) {
            NaturalAiConfig.directSensorLinear().copy(enabled = _uiState.value.aiConfig.enabled)
        } else {
            _uiState.value.aiConfig
        }
        _uiState.value = _uiState.value.copy(
            selectedMode = mode,
            captureParams = updatedParams,
            aiConfig = updatedAi
        )
        controller.updateParameters(updatedParams, _uiState.value.selectedProfile)
    }

    fun updateParameters(transform: (CaptureParameters) -> CaptureParameters) {
        val updated = transform(_uiState.value.captureParams)
        _uiState.value = _uiState.value.copy(captureParams = updated)
        controller.updateParameters(updated, _uiState.value.selectedProfile)
    }

    fun updateAiConfig(transform: (NaturalAiConfig) -> NaturalAiConfig) {
        _uiState.value = _uiState.value.copy(aiConfig = transform(_uiState.value.aiConfig))
    }

    fun selectOpticalProfile(profile: OpticalProfile) {
        profileRepo.setSelectedProfileId(profile.id)
        _uiState.value = _uiState.value.copy(selectedProfile = profile)
        controller.updateParameters(_uiState.value.captureParams, profile)
    }

    fun saveOpticalProfile(profile: OpticalProfile) {
        val current = _uiState.value.opticalProfiles.toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        profileRepo.saveProfiles(current)
        profileRepo.setSelectedProfileId(profile.id)
        _uiState.value = _uiState.value.copy(
            opticalProfiles = current,
            selectedProfile = profile
        )
        controller.updateParameters(_uiState.value.captureParams, profile)
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    fun triggerCapture(focusSweepEnabled: Boolean = false) {
        if (_uiState.value.isCapturing) return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCapturing = true, statusMessage = null)

            if (state.captureParams.timerSeconds > 0) {
                for (sec in state.captureParams.timerSeconds downTo 1) {
                    _uiState.value = _uiState.value.copy(countdownRemaining = sec)
                    delay(1000)
                }
                _uiState.value = _uiState.value.copy(countdownRemaining = 0)
            }

            val cam = state.activeCamera
            if (cam == null) {
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    statusMessage = "No active camera hardware detected"
                )
                return@launch
            }

            val frameCount = if (state.captureParams.burstCount > 1) {
                state.captureParams.burstCount
            } else {
                state.selectedMode.multiFrameCount
            }

            val bitmaps = mutableListOf<Bitmap>()
            var lastRawResult: RawAndJpegResult? = null

            val maxDiopter = cam.minimumFocusDistanceDiopters ?: 0f
            val baseDiopter = state.captureParams.manualFocusDistanceDiopters

            for (i in 0 until frameCount) {
                val focusOverride = if (focusSweepEnabled && cam.supportsManualFocus && frameCount > 1) {
                    (baseDiopter + (i - 1) * 0.4f).coerceIn(0f, maxDiopter)
                } else null

                val res = captureSingleSuspending(focusOverride)
                if (res != null) {
                    res.jpegBytes?.let { bytes ->
                        decodeRotatedBitmap(bytes, cam.sensorOrientationDeg)?.let { bmp ->
                            bitmaps.add(bmp)
                        }
                    }
                    if (i == 0) {
                        lastRawResult = res
                    } else {
                        res.rawImage?.close()
                    }
                }
            }

            if (bitmaps.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    statusMessage = "Capture failed to produce frame data"
                )
                return@launch
            }

            val record = withContext(Dispatchers.Default) {
                val beforeBmp = bitmaps.first()
                val outcome = aiEngine.enhance(
                    frames = bitmaps,
                    config = state.aiConfig,
                    opticalProfile = state.selectedProfile
                )
                galleryRepo.saveCapture(
                    finalBitmap = outcome.bitmap,
                    beforeBitmap = beforeBmp,
                    rawImage = lastRawResult?.rawImage,
                    captureResult = lastRawResult?.captureResult,
                    characteristics = lastRawResult?.characteristics,
                    cameraId = cam.cameraId,
                    mode = state.selectedMode,
                    whiteBalanceLabel = state.captureParams.whiteBalancePreset.label,
                    aiApplied = state.aiConfig.enabled,
                    engineLabel = outcome.engineUsed
                )
            }

            _uiState.value = _uiState.value.copy(
                isCapturing = false,
                galleryRecords = listOf(record) + _uiState.value.galleryRecords,
                statusMessage = "Saved ${record.displayName} (${record.formatLabel})"
            )
        }
    }

    private suspend fun captureSingleSuspending(focusOverride: Float?): RawAndJpegResult? =
        suspendCancellableCoroutine { cont ->
            controller.captureStill(focusOverride) { res ->
                if (cont.isActive) cont.resume(res)
            }
        }

    private fun decodeRotatedBitmap(bytes: ByteArray, orientationDeg: Int): Bitmap? {
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (orientationDeg == 0) return raw
        val matrix = Matrix().apply { postRotate(orientationDeg.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}
