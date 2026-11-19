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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naturalai.camerapro.domain.model.OpticalProfile
import com.naturalai.camerapro.domain.model.OpticalType
import com.naturalai.camerapro.ui.theme.AmberAccent
import com.naturalai.camerapro.ui.theme.BorderHairline
import com.naturalai.camerapro.ui.theme.ObsidianBlack
import com.naturalai.camerapro.ui.theme.SurfacePanel
import com.naturalai.camerapro.ui.viewmodel.CameraUiState
import com.naturalai.camerapro.ui.viewmodel.CameraViewModel

@Composable
fun OpticalProfilesScreen(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onBack: () -> Unit
) {
    var editing by remember(state.selectedProfile) { mutableStateOf(state.selectedProfile) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Optical Profiles", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Close", color = AmberAccent, modifier = Modifier.clickable { onBack() })
        }

        // Saved profiles list
        state.opticalProfiles.forEach { profile ->
            val selected = profile.id == state.selectedProfile.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfacePanel, RoundedCornerShape(8.dp))
                    .border(1.dp, if (selected) AmberAccent else BorderHairline, RoundedCornerShape(8.dp))
                    .clickable {
                        viewModel.selectOpticalProfile(profile)
                        editing = profile
                    }
                    .padding(12.dp)
            ) {
                Text(profile.name, color = if (selected) AmberAccent else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    text = "${profile.type.label} | ${profile.estimatedFocalLengthMm}mm | WD ${profile.workingDistanceMm}mm | ${profile.apertureLabel}",
                    color = Color(0xFFA1A1AA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("Edit Active Profile Parameters", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

        OutlinedTextField(
            value = editing.name,
            onValueChange = { editing = editing.copy(name = it) },
            label = { Text("Profile Name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = editing.apertureLabel,
            onValueChange = { editing = editing.copy(apertureLabel = it) },
            label = { Text("Aperture (if available)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = editing.focusRangeNote,
            onValueChange = { editing = editing.copy(focusRangeNote = it) },
            label = { Text("Focus Range") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = editing.colorCharacter,
            onValueChange = { editing = editing.copy(colorCharacter = it) },
            label = { Text("Color Character") },
            modifier = Modifier.fillMaxWidth()
        )

        ProfileFloatSlider("Estimated Focal Length (mm)", editing.estimatedFocalLengthMm, 0f, 300f) {
            editing = editing.copy(estimatedFocalLengthMm = it)
        }
        ProfileFloatSlider("Working Distance (mm)", editing.workingDistanceMm, 0f, 1000f) {
            editing = editing.copy(workingDistanceMm = it)
        }
        ProfileFloatSlider("Vignetting Correction", editing.vignettingCorrection, 0f, 1f) {
            editing = editing.copy(vignettingCorrection = it)
        }
        ProfileFloatSlider("Distortion Correction", editing.distortionCorrection, -0.5f, 0.5f) {
            editing = editing.copy(distortionCorrection = it)
        }
        ProfileFloatSlider("Chromatic Aberration Correction", editing.chromaticAberrationCorrection, 0f, 1f) {
            editing = editing.copy(chromaticAberrationCorrection = it)
        }
        ProfileFloatSlider("Sharpening Profile", editing.sharpeningLevel, 0f, 1f) {
            editing = editing.copy(sharpeningLevel = it)
        }
        ProfileFloatSlider("Noise Profile", editing.noiseProfileLevel, 0f, 1f) {
            editing = editing.copy(noiseProfileLevel = it)
        }
        ProfileFloatSlider("Red Gain Trim (Pink Corner Guard)", editing.redGainTrim, 0.7f, 1.3f) {
            editing = editing.copy(redGainTrim = it)
        }
        ProfileFloatSlider("Blue Gain Trim", editing.blueGainTrim, 0.7f, 1.3f) {
            editing = editing.copy(blueGainTrim = it)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.saveOpticalProfile(editing) }) {
                Text("Save Profile")
            }
            Button(onClick = {
                val created = editing.copy(
                    id = "custom_${System.currentTimeMillis()}",
                    type = OpticalType.CUSTOM_OPTICS,
                    name = "${editing.name} (Copy)"
                )
                viewModel.saveOpticalProfile(created)
                editing = created
            }) {
                Text("Save as New")
            }
        }
    }
}

@Composable
private fun ProfileFloatSlider(
    label: String,
    value: Float,
    minVal: Float,
    maxVal: Float,
    onChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfacePanel, RoundedCornerShape(8.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 12.sp)
            Text("${"%.2f".format(value)}", color = AmberAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = minVal..maxVal)
    }
}
