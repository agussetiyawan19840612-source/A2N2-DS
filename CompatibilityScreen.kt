package com.naturalai.camerapro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naturalai.camerapro.ui.theme.AmberAccent
import com.naturalai.camerapro.ui.theme.BorderHairline
import com.naturalai.camerapro.ui.theme.CrimsonClip
import com.naturalai.camerapro.ui.theme.EmeraldOk
import com.naturalai.camerapro.ui.theme.ObsidianBlack
import com.naturalai.camerapro.ui.theme.SurfacePanel
import com.naturalai.camerapro.ui.viewmodel.CameraUiState

@Composable
fun CompatibilityScreen(
    state: CameraUiState,
    onSelectCamera: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Camera2 Hardware Diagnostics", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Close", color = AmberAccent, modifier = Modifier.clickable { onBack() })
        }

        if (state.cameras.isEmpty()) {
            Text("No Camera2 devices reported by Android CameraManager.", color = CrimsonClip, fontSize = 13.sp)
        }

        state.cameras.forEach { cam ->
            val isActive = state.activeCamera?.cameraId == cam.cameraId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfacePanel, RoundedCornerShape(10.dp))
                    .border(1.dp, if (isActive) AmberAccent else BorderHairline, RoundedCornerShape(10.dp))
                    .clickable { onSelectCamera(cam.cameraId) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Camera ID: ${cam.cameraId} (${cam.lensFacingLabel}) — ${cam.hardwareLevelLabel}",
                    color = if (isActive) AmberAccent else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(2.dp))
                CapRow("Sensor Orientation", "${cam.sensorOrientationDeg} deg")
                CapRow("Physical Sensor Size", cam.sensorPhysicalSizeMm ?: "UNSUPPORTED")
                CapRow("Max JPEG Size", cam.maxJpegSize?.let { "${it.width} x ${it.height}" } ?: "UNSUPPORTED")
                CapRow(
                    "RAW Capability",
                    if (cam.supportsRawCapability) cam.supportedRawFormats.joinToString(", ") else "UNSUPPORTED",
                    supported = cam.supportsRawCapability
                )
                CapRow(
                    "Manual Sensor (ISO/Shutter)",
                    if (cam.supportsManualSensor) "SUPPORTED" else "UNSUPPORTED",
                    supported = cam.supportsManualSensor
                )
                CapRow(
                    "ISO Range",
                    cam.isoRange?.let { "${it.lower} - ${it.upper}" } ?: "UNSUPPORTED",
                    supported = cam.isoRange != null
                )
                CapRow(
                    "Shutter Range (ns)",
                    cam.exposureTimeRangeNs?.let { "${it.lower} - ${it.upper}" } ?: "UNSUPPORTED",
                    supported = cam.exposureTimeRangeNs != null
                )
                CapRow(
                    "Exposure Compensation",
                    cam.exposureCompensationRange?.let { "${it.lower}..${it.upper} (step ${cam.exposureCompensationStep})" } ?: "UNSUPPORTED",
                    supported = cam.exposureCompensationRange != null
                )
                CapRow(
                    "Autofocus",
                    if (cam.supportsAutofocus) "SUPPORTED" else "UNSUPPORTED",
                    supported = cam.supportsAutofocus
                )
                CapRow(
                    "Manual Focus (Diopters)",
                    if (cam.supportsManualFocus) "0.0 - ${cam.minimumFocusDistanceDiopters}" else "UNSUPPORTED",
                    supported = cam.supportsManualFocus
                )
                CapRow(
                    "White Balance Modes",
                    if (cam.supportedAwbModes.isNotEmpty()) "${cam.supportedAwbModes.size} hardware modes" else "UNSUPPORTED",
                    supported = cam.supportedAwbModes.isNotEmpty()
                )
            }
        }
    }
}

@Composable
private fun CapRow(label: String, value: String, supported: Boolean = value != "UNSUPPORTED") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFA1A1AA), fontSize = 11.sp)
        Text(
            text = value,
            color = if (supported) EmeraldOk else CrimsonClip,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}
