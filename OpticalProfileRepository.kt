package com.naturalai.camerapro.data

import android.content.Context
import com.naturalai.camerapro.domain.model.OpticalProfile
import com.naturalai.camerapro.domain.model.OpticalType
import org.json.JSONArray
import org.json.JSONObject

class OpticalProfileRepository(context: Context) {

    private val prefs = context.getSharedPreferences("optical_profiles_v1", Context.MODE_PRIVATE)

    fun loadProfiles(): List<OpticalProfile> {
        val raw = prefs.getString("profiles_json", null)
        if (raw.isNullOrBlank()) {
            val defaults = OpticalProfile.builtInDefaults()
            saveProfiles(defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<OpticalProfile>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    OpticalProfile(
                        id = o.getString("id"),
                        type = OpticalType.valueOf(o.getString("type")),
                        name = o.getString("name"),
                        estimatedFocalLengthMm = o.getDouble("estimatedFocalLengthMm").toFloat(),
                        workingDistanceMm = o.getDouble("workingDistanceMm").toFloat(),
                        apertureLabel = o.getString("apertureLabel"),
                        focusRangeNote = o.getString("focusRangeNote"),
                        colorCharacter = o.getString("colorCharacter"),
                        vignettingCorrection = o.getDouble("vignettingCorrection").toFloat(),
                        distortionCorrection = o.getDouble("distortionCorrection").toFloat(),
                        chromaticAberrationCorrection = o.getDouble("chromaticAberrationCorrection").toFloat(),
                        sharpeningLevel = o.getDouble("sharpeningLevel").toFloat(),
                        noiseProfileLevel = o.getDouble("noiseProfileLevel").toFloat(),
                        redGainTrim = o.optDouble("redGainTrim", 1.0).toFloat(),
                        greenGainTrim = o.optDouble("greenGainTrim", 1.0).toFloat(),
                        blueGainTrim = o.optDouble("blueGainTrim", 1.0).toFloat()
                    )
                )
            }
            if (list.isEmpty()) OpticalProfile.builtInDefaults() else list
        } catch (_: Exception) {
            OpticalProfile.builtInDefaults()
        }
    }

    fun saveProfiles(profiles: List<OpticalProfile>) {
        val arr = JSONArray()
        for (p in profiles) {
            val o = JSONObject().apply {
                put("id", p.id)
                put("type", p.type.name)
                put("name", p.name)
                put("estimatedFocalLengthMm", p.estimatedFocalLengthMm)
                put("workingDistanceMm", p.workingDistanceMm)
                put("apertureLabel", p.apertureLabel)
                put("focusRangeNote", p.focusRangeNote)
                put("colorCharacter", p.colorCharacter)
                put("vignettingCorrection", p.vignettingCorrection)
                put("distortionCorrection", p.distortionCorrection)
                put("chromaticAberrationCorrection", p.chromaticAberrationCorrection)
                put("sharpeningLevel", p.sharpeningLevel)
                put("noiseProfileLevel", p.noiseProfileLevel)
                put("redGainTrim", p.redGainTrim)
                put("greenGainTrim", p.greenGainTrim)
                put("blueGainTrim", p.blueGainTrim)
            }
            arr.put(o)
        }
        prefs.edit().putString("profiles_json", arr.toString()).apply()
    }

    fun getSelectedProfileId(): String {
        return prefs.getString("selected_profile_id", "stock_smartphone") ?: "stock_smartphone"
    }

    fun setSelectedProfileId(id: String) {
        prefs.edit().putString("selected_profile_id", id).apply()
    }
}
