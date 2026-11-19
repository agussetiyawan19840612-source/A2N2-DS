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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.naturalai.camerapro.ui.theme.CyanAccent
import com.naturalai.camerapro.ui.theme.ObsidianBlack
import com.naturalai.camerapro.ui.theme.SurfacePanel
import com.naturalai.camerapro.ui.viewmodel.CameraUiState
import com.naturalai.camerapro.ui.viewmodel.CameraViewModel

@Composable
fun DirectSensorScreen(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onOpenOpticalProfiles: () -> Unit,
    onBack: () -> Unit
) {
    val cam = state.activeCamera
    val params = state.captureParams

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
            Text(
                text = "Direct Sensor Console",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Close",
                color = AmberAccent,
                modifier = Modifier.clickable { onBack() }
            )
        }

        // Direct Sensor Master Switch
        DirectSensorToggleRow(
            title = "Direct Sensor Mode",
            subtitle = "Assume non-standard / lensless optics; bypass stock lens assumptions",
            checked = params.directSensorMode
        ) { checked ->
            viewModel.updateParameters { it.copy(directSensorMode = checked) }
        }

        DirectSensorToggleRow(
            title = "Disable Hardware Sharpening",
            subtitle = "Sets Camera2 EDGE_MODE_OFF to prevent edge halos",
            checked = params.disableHardwareSharpening
        ) { checked ->
            viewModel.updateParameters { it.copy(disableHardwareSharpening = checked) }
        }

        DirectSensorToggleRow(
            title = "Minimize Hardware Denoise",
            subtitle = "Sets NOISE_REDUCTION_MODE_MINIMAL to preserve true sensor grain",
            checked = params.minimizeHardwareDenoise
        ) { checked ->
            viewModel.updateParameters { it.copy(minimizeHardwareDenoise = checked) }
        }

        DirectSensorToggleRow(
            title = "Bypass Stock Lens Shading & Aberration",
            subtitle = "Disables stock lens shading assumptions for external DSLR/CCTV/microscope",
            checked = params.disableLensCorrection
        ) { checked ->
            viewModel.updateParameters { it.copy(disableLensCorrection = checked) }
        }

        // Optical Focus Assistance (for lensless / manual external optics)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfacePanel, RoundedCornerShape(10.dp))
                .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text("Optical Focus Assistance (External / Lensless)", color = CyanAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Laplacian Contrast Score: ${"%.1f".format(state.telemetry.laplacianFocusScore)}  (Delta: ${"%+.1f".format(state.telemetry.focusTrendDelta)})",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = if (cam?.supportsAutofocus == true) "Hardware AF available, bypassed when Direct Sensor is active."
                else "Hardware AF: UNSUPPORTED. Adjust external lens ring or object distance until score peaks.",
                color = Color(0xFFA1A1AA),
                fontSize = 11.sp
            )
        }

        // Clipping & RGB Balance Guard
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfacePanel, RoundedCornerShape(10.dp))
                .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text("Highlight / Shadow & RGB Balance", color = AmberAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Highlight Clip: ${"%.2f".format(state.telemetry.highlightClipPercent)}%  |  Shadow Crush: ${"%.2f".format(state.telemetry.shadowCrushPercent)}%",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
            Text(
                text = "WB Telemetry: ${state.telemetry.actualWbGains}",
                color = Color(0xFFA1A1AA),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )

            if (params.whiteBalancePreset.name == "MANUAL") {
                Spacer(Modifier.height(6.dp))
                Text("Manual Kelvin: ${params.manualKelvin}K", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = params.manualKelvin.toFloat(),
                    onValueChange = { k -> viewModel.updateParameters { it.copy(manualKelvin = k.toInt()) } },
                    valueRange = 2400f..8500f
                )
            }
        }

        // Sensor Information & Active Optical Profile
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfacePanel, RoundedCornerShape(10.dp))
                .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text("Active Sensor & Optical Profile", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("Camera ID: ${cam?.cameraId ?: "NONE"} (${cam?.hardwareLevelLabel ?: "UNSUPPORTED"})", color = Color(0xFFD4D4D8), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("Sensor Size: ${cam?.sensorPhysicalSizeMm ?: "UNSUPPORTED"}  |  Orient: ${cam?.sensorOrientationDeg ?: 0} deg", color = Color(0xFFD4D4D8), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("RAW Formats: ${if (cam?.supportedRawFormats?.isNotEmpty() == true) cam.supportedRawFormats.joinToString() else "UNSUPPORTED"}", color = Color(0xFFD4D4D8), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Optical Profile: ${state.selectedProfile.name} (Tap to configure)",
                color = AmberAccent,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onOpenOpticalProfiles() }
            )
        }
    }
}

@Composable
private fun DirectSensorToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfacePanel, RoundedCornerShape(10.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFA1A1AA), fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
