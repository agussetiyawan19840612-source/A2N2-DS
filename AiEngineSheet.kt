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
import com.naturalai.camerapro.domain.model.NaturalAiConfig
import com.naturalai.camerapro.ui.theme.AmberAccent
import com.naturalai.camerapro.ui.theme.BorderHairline
import com.naturalai.camerapro.ui.theme.CyanAccent
import com.naturalai.camerapro.ui.theme.ObsidianBlack
import com.naturalai.camerapro.ui.theme.SurfacePanel
import com.naturalai.camerapro.ui.viewmodel.CameraUiState
import com.naturalai.camerapro.ui.viewmodel.CameraViewModel

@Composable
fun AiEngineSheet(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onBack: () -> Unit
) {
    val cfg = state.aiConfig

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
            Text("Natural AI Image Engine", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Close", color = AmberAccent, modifier = Modifier.clickable { onBack() })
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfacePanel, RoundedCornerShape(10.dp))
                .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Natural AI Processing", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Backend: ${state.aiEngineDescription}", color = CyanAccent, fontSize = 11.sp)
                }
                Switch(
                    checked = cfg.enabled,
                    onCheckedChange = { on -> viewModel.updateAiConfig { it.copy(enabled = on) } }
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.updateAiConfig { NaturalAiConfig.naturalDefaults() } }) {
                    Text("Natural Default", fontSize = 11.sp)
                }
                Button(onClick = { viewModel.updateAiConfig { NaturalAiConfig.directSensorLinear() } }) {
                    Text("Direct Sensor Linear", fontSize = 11.sp)
                }
            }
        }

        AiSliderItem("1. Natural Sharpness", cfg.naturalSharpness) { v ->
            viewModel.updateAiConfig { it.copy(naturalSharpness = v) }
        }
        AiSliderItem("2. Detail Recovery", cfg.detailRecovery) { v ->
            viewModel.updateAiConfig { it.copy(detailRecovery = v) }
        }
        AiSliderItem("3. Noise Reduction", cfg.noiseReduction) { v ->
            viewModel.updateAiConfig { it.copy(noiseReduction = v) }
        }
        AiSliderItem("4. Low Light Recovery", cfg.lowLightRecovery) { v ->
            viewModel.updateAiConfig { it.copy(lowLightRecovery = v) }
        }
        AiSliderItem("5. RGB Balance", cfg.rgbBalance) { v ->
            viewModel.updateAiConfig { it.copy(rgbBalance = v) }
        }
        AiSliderItem("6. Micro Contrast", cfg.microContrast) { v ->
            viewModel.updateAiConfig { it.copy(microContrast = v) }
        }
        AiSliderItem("7. Texture Preservation", cfg.texturePreservation) { v ->
            viewModel.updateAiConfig { it.copy(texturePreservation = v) }
        }
        AiSliderItem("8. Highlight Recovery", cfg.highlightRecovery) { v ->
            viewModel.updateAiConfig { it.copy(highlightRecovery = v) }
        }
        AiSliderItem("9. Shadow Recovery", cfg.shadowRecovery) { v ->
            viewModel.updateAiConfig { it.copy(shadowRecovery = v) }
        }
        AiSliderItem("10. Natural Color", cfg.naturalColor) { v ->
            viewModel.updateAiConfig { it.copy(naturalColor = v) }
        }
    }
}

@Composable
private fun AiSliderItem(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfacePanel, RoundedCornerShape(8.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White, fontSize = 12.sp)
            Text("$value", color = AmberAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f
        )
    }
}
