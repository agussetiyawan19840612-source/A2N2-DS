package com.naturalai.camerapro.domain.model

enum class OpticalType(val label: String) {
    STOCK_SMARTPHONE("Stock Smartphone Lens"),
    DIRECT_SENSOR_LENSLESS("Direct Sensor / Lensless"),
    DSLR_ADAPTER("DSLR Lens Adapter"),
    CCTV_LENS("CCTV Lens"),
    MICROSCOPE_LENS("Microscope Lens"),
    MACRO_LENS("Macro Lens"),
    CUSTOM_OPTICS("Custom Optics")
}

data class OpticalProfile(
    val id: String,
    val type: OpticalType,
    val name: String,
    val estimatedFocalLengthMm: Float,
    val workingDistanceMm: Float,
    val apertureLabel: String,
    val focusRangeNote: String,
    val colorCharacter: String,
    val vignettingCorrection: Float,        // 0.0 - 1.0 radial boost
    val distortionCorrection: Float,        // -0.5 - 0.5
    val chromaticAberrationCorrection: Float, // 0.0 - 1.0
    val sharpeningLevel: Float,             // 0.0 - 1.0
    val noiseProfileLevel: Float,           // 0.0 - 1.0
    val redGainTrim: Float = 1.0f,          // 0.7 - 1.3
    val greenGainTrim: Float = 1.0f,        // 0.7 - 1.3
    val blueGainTrim: Float = 1.0f          // 0.7 - 1.3
) {
    companion object {
        fun builtInDefaults(): List<OpticalProfile> = listOf(
            OpticalProfile(
                id = "stock_smartphone",
                type = OpticalType.STOCK_SMARTPHONE,
                name = "Stock Smartphone Lens",
                estimatedFocalLengthMm = 24f,
                workingDistanceMm = 120f,
                apertureLabel = "Device Fixed",
                focusRangeNote = "10cm - Infinity (AF)",
                colorCharacter = "Standard Sensor Neutral",
                vignettingCorrection = 0.10f,
                distortionCorrection = 0.0f,
                chromaticAberrationCorrection = 0.10f,
                sharpeningLevel = 0.20f,
                noiseProfileLevel = 0.20f
            ),
            OpticalProfile(
                id = "direct_sensor_lensless",
                type = OpticalType.DIRECT_SENSOR_LENSLESS,
                name = "Direct Sensor / Lensless",
                estimatedFocalLengthMm = 0f,
                workingDistanceMm = 0f,
                apertureLabel = "UNSUPPORTED (Lensless)",
                focusRangeNote = "External Optics / Manual Distance",
                colorCharacter = "Linear Uncalibrated Sensor",
                vignettingCorrection = 0.35f,
                distortionCorrection = 0.0f,
                chromaticAberrationCorrection = 0.0f,
                sharpeningLevel = 0.0f,
                noiseProfileLevel = 0.05f,
                redGainTrim = 0.96f,
                greenGainTrim = 1.0f,
                blueGainTrim = 1.04f
            ),
            OpticalProfile(
                id = "dslr_adapter",
                type = OpticalType.DSLR_ADAPTER,
                name = "DSLR Lens Adapter (50mm f/1.8)",
                estimatedFocalLengthMm = 50f,
                workingDistanceMm = 450f,
                apertureLabel = "Manual Ring (f/1.8 - f/16)",
                focusRangeNote = "45cm - Infinity (Manual Ring)",
                colorCharacter = "Warm Optical Glass",
                vignettingCorrection = 0.25f,
                distortionCorrection = 0.02f,
                chromaticAberrationCorrection = 0.25f,
                sharpeningLevel = 0.15f,
                noiseProfileLevel = 0.12f
            ),
            OpticalProfile(
                id = "cctv_lens",
                type = OpticalType.CCTV_LENS,
                name = "C-Mount CCTV Lens (16mm)",
                estimatedFocalLengthMm = 16f,
                workingDistanceMm = 200f,
                apertureLabel = "Manual Iris (f/1.4 - f/16)",
                focusRangeNote = "20cm - Infinity (Manual Helical)",
                colorCharacter = "Edge Magenta Guard",
                vignettingCorrection = 0.45f,
                distortionCorrection = 0.08f,
                chromaticAberrationCorrection = 0.35f,
                sharpeningLevel = 0.15f,
                noiseProfileLevel = 0.15f,
                redGainTrim = 0.95f,
                greenGainTrim = 1.02f,
                blueGainTrim = 1.03f
            ),
            OpticalProfile(
                id = "microscope_lens",
                type = OpticalType.MICROSCOPE_LENS,
                name = "Microscope Objective Adapter",
                estimatedFocalLengthMm = 180f,
                workingDistanceMm = 8f,
                apertureLabel = "NA 0.25 Fixed",
                focusRangeNote = "Micrometer Stage Fine Focus",
                colorCharacter = "Neutral Illuminator Balance",
                vignettingCorrection = 0.40f,
                distortionCorrection = 0.0f,
                chromaticAberrationCorrection = 0.45f,
                sharpeningLevel = 0.10f,
                noiseProfileLevel = 0.15f
            ),
            OpticalProfile(
                id = "macro_lens",
                type = OpticalType.MACRO_LENS,
                name = "External Macro Doublet",
                estimatedFocalLengthMm = 85f,
                workingDistanceMm = 45f,
                apertureLabel = "Device / External Ring",
                focusRangeNote = "3cm - 8cm (Focus Sweep)",
                colorCharacter = "True Texture Neutral",
                vignettingCorrection = 0.22f,
                distortionCorrection = 0.0f,
                chromaticAberrationCorrection = 0.25f,
                sharpeningLevel = 0.18f,
                noiseProfileLevel = 0.15f
            ),
            OpticalProfile(
                id = "custom_optics",
                type = OpticalType.CUSTOM_OPTICS,
                name = "Custom DIY Optics",
                estimatedFocalLengthMm = 35f,
                workingDistanceMm = 100f,
                apertureLabel = "Manual / Unknown",
                focusRangeNote = "User Calibrated",
                colorCharacter = "Custom RGB Trim",
                vignettingCorrection = 0.30f,
                distortionCorrection = 0.0f,
                chromaticAberrationCorrection = 0.20f,
                sharpeningLevel = 0.12f,
                noiseProfileLevel = 0.15f
            )
        )
    }
}
