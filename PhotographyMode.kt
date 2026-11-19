package com.naturalai.camerapro.domain.model

enum class PhotographyMode(
    val displayName: String,
    val fileTag: String,
    val multiFrameCount: Int,
    val prefersDirectSensorDefaults: Boolean
) {
    NATURAL_AUTO("Natural Auto", "AUTO", 1, false),
    DIRECT_SENSOR("Direct Sensor", "DIRECT", 1, true),
    PORTRAIT("Portrait", "PORTRAIT", 1, false),
    LANDSCAPE("Landscape", "LANDSCAPE", 1, false),
    MACRO("Macro", "MACRO", 1, false),
    MICRO_MACRO("Micro Macro", "MICRO", 3, true),
    TELE_MACRO("Tele Macro", "TELEMACRO", 1, false),
    NIGHT("Night", "NIGHT", 4, false),
    LOW_LIGHT("Low Light", "LOWLIGHT", 3, false),
    MOVING_OBJECT("Moving Object", "ACTION", 1, false),
    BIRD_WILDLIFE("Bird & Wildlife", "WILDLIFE", 1, false),
    MOON("Moon", "MOON", 3, false),
    RAW_LAB("RAW Lab", "RAWLAB", 1, true)
}
