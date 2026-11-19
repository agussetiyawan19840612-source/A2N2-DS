package com.naturalai.camerapro.domain.model

enum class OutputFormat(val label: String) {
    JPEG("JPEG"),
    RAW_DNG("DNG"),
    RAW_AND_JPEG("JPEG+DNG")
}

enum class GridOverlayMode(val label: String) {
    OFF("Off"),
    RULE_OF_THIRDS("3x3"),
    GOLDEN_RATIO("Phi"),
    CROSSHAIR("Center")
}

enum class WhiteBalancePreset(val label: String, val kelvin: Int) {
    AUTO("Auto", 0),
    DAYLIGHT("Daylight 5500K", 5500),
    CLOUDY("Cloudy 6500K", 6500),
    TUNGSTEN("Tungsten 3200K", 3200),
    FLUORESCENT("Fluorescent 4200K", 4200),
    MANUAL("Manual Kelvin", 5200)
}

data class CaptureParameters(
    val isoAuto: Boolean = true,
    val manualIso: Int = 100,
    val shutterAuto: Boolean = true,
    val manualShutterNs: Long = 16_666_666L, // 1/60s
    val exposureCompensationIndex: Int = 0,
    val whiteBalancePreset: WhiteBalancePreset = WhiteBalancePreset.AUTO,
    val manualKelvin: Int = 5200,
    val autofocusEnabled: Boolean = true,
    val manualFocusDistanceDiopters: Float = 0.0f,
    val focusPeakingEnabled: Boolean = false,
    val zebraExposureEnabled: Boolean = false,
    val gridMode: GridOverlayMode = GridOverlayMode.RULE_OF_THIRDS,
    val digitalZoomRatio: Float = 1.0f,
    val timerSeconds: Int = 0,
    val burstCount: Int = 1,
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val exposureLocked: Boolean = false,
    val focusLocked: Boolean = false,
    val directSensorMode: Boolean = false,
    val disableHardwareSharpening: Boolean = true,
    val minimizeHardwareDenoise: Boolean = true,
    val disableLensCorrection: Boolean = true,
    val avoidAggressiveInterpolation: Boolean = true,
    val clippingGuardEnabled: Boolean = true
)

data class LiveFrameTelemetry(
    val actualIso: Int? = null,
    val actualExposureTimeNs: Long? = null,
    val actualFocusDistanceDiopters: Float? = null,
    val actualAfState: String = "UNKNOWN",
    val actualAeState: String = "UNKNOWN",
    val actualWbGains: String = "AUTO",
    val redHistogram: IntArray = IntArray(64),
    val greenHistogram: IntArray = IntArray(64),
    val blueHistogram: IntArray = IntArray(64),
    val lumaHistogram: IntArray = IntArray(64),
    val highlightClipPercent: Float = 0f,
    val shadowCrushPercent: Float = 0f,
    val laplacianFocusScore: Float = 0f,
    val focusTrendDelta: Float = 0f
)
