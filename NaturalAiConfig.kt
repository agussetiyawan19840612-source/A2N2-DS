package com.naturalai.camerapro.domain.model

data class NaturalAiConfig(
    val enabled: Boolean = true,
    val naturalSharpness: Int = 22,     // 0-100: halo-free micro edge sharpening
    val detailRecovery: Int = 28,       // 0-100: real luminance texture recovery
    val noiseReduction: Int = 18,       // 0-100: edge-preserving chroma/luma denoise
    val lowLightRecovery: Int = 20,     // 0-100: shadow lift keeping night mood
    val rgbBalance: Int = 50,           // 0-100: 50 = neutral, <50 cool, >50 warm/auto trim
    val microContrast: Int = 15,        // 0-100: local tonal separation
    val texturePreservation: Int = 85,  // 0-100: prevents plastic/painterly smoothing
    val highlightRecovery: Int = 35,    // 0-100: soft highlight shoulder compression
    val shadowRecovery: Int = 25,       // 0-100: deep shadow lift without haze
    val naturalColor: Int = 50          // 0-100: 50 = true sensor color, avoids oversaturation
) {
    companion object {
        fun naturalDefaults() = NaturalAiConfig()

        fun directSensorLinear() = NaturalAiConfig(
            enabled = true,
            naturalSharpness = 8,
            detailRecovery = 15,
            noiseReduction = 5,
            lowLightRecovery = 10,
            rgbBalance = 50,
            microContrast = 5,
            texturePreservation = 95,
            highlightRecovery = 25,
            shadowRecovery = 10,
            naturalColor = 50
        )
    }
}
