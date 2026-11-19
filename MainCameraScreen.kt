package com.naturalai.camerapro.ui.screens

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.naturalai.camerapro.domain.model.GridOverlayMode
import com.naturalai.camerapro.domain.model.LiveFrameTelemetry
import com.naturalai.camerapro.domain.model.OutputFormat
import com.naturalai.camerapro.domain.model.PhotographyMode
import com.naturalai.camerapro.domain.model.WhiteBalancePreset
import com.naturalai.camerapro.processing.OpenGlPreviewRenderer
import com.naturalai.camerapro.ui.theme.AmberAccent
import com.naturalai.camerapro.ui.theme.BorderHairline
import com.naturalai.camerapro.ui.theme.CyanAccent
import com.naturalai.camerapro.ui.theme.ObsidianBlack
import com.naturalai.camerapro.ui.theme.SurfacePanel
import com.naturalai.camerapro.ui.viewmodel.CameraUiState
import com.naturalai.camerapro.ui.viewmodel.CameraViewModel

@Composable
fun MainCameraScreen(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onOpenDirectSensor: () -> Unit,
    onOpenAiEngine: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenCompatibility: () -> Unit,
    onOpenGallery: () -> Unit
) {
    var activeControlTab by remember { mutableStateOf("ISO") }
    var glViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    val renderer = remember {
        OpenGlPreviewRenderer(
            onSurfaceReady = { surface -> viewModel.onPreviewSurfaceReady(surface) },
            requestRender = { glViewRef?.requestRender() }
        )
    }

    LaunchedEffect(state.captureParams, state.selectedProfile) {
        renderer.focusPeakingEnabled = state.captureParams.focusPeakingEnabled
        renderer.zebraEnabled = state.captureParams.zebraExposureEnabled
        renderer.vignetteCorrection = if (state.captureParams.directSensorMode) {
            state.selectedProfile.vignettingCorrection
        } else 0f
        renderer.redGainTrim = state.selectedProfile.redGainTrim
        renderer.greenGainTrim = state.selectedProfile.greenGainTrim
        renderer.blueGainTrim = state.selectedProfile.blueGainTrim
        renderer.gridModeIndex = state.captureParams.gridMode.ordinal
    }

    DisposableEffect(Unit) {
        onDispose { renderer.release() }
    }

    val cam = state.activeCamera

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Top hardware telemetry bar (displays actual sensor values or UNSUPPORTED)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfacePanel)
                .border(1.dp, BorderHairline)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isoText = state.telemetry.actualIso?.toString()
                ?: if (cam?.supportsManualSensor == false) "AUTO" else "WAIT"
            val expText = state.telemetry.actualExposureTimeNs?.let { ns ->
                if (ns >= 1_000_000_000L) "${"%.1f".format(ns / 1e9)}s"
                else "1/${(1_000_000_000.0 / ns).toInt().coerceAtLeast(1)}"
            } ?: "AUTO"

            Text(
                text = "CAM:${cam?.cameraId ?: "NONE"} (${cam?.lensFacingLabel ?: "UNSUPPORTED"})  ISO:$isoText  S:$expText",
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactTopButton("INFO", onClick = onOpenCompatibility)
                CompactTopButton("OPTICS", onClick = onOpenProfiles)
            }
        }

        // Live OpenGL Camera2 Preview Stage
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(3)
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        glViewRef = this
                    }
                }
            )

            // Live RGB Histogram Overlay
            RgbHistogramOverlay(
                telemetry = state.telemetry,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .width(126.dp)
                    .height(54.dp)
            )

            // Quick viewfinder toggles (Peaking, Zebra, Grid, AE/AF Lock)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OverlayToggleChip(
                    label = "PEAK",
                    active = state.captureParams.focusPeakingEnabled
                ) {
                    viewModel.updateParameters { it.copy(focusPeakingEnabled = !it.focusPeakingEnabled) }
                }
                OverlayToggleChip(
                    label = "ZEBRA",
                    active = state.captureParams.zebraExposureEnabled
                ) {
                    viewModel.updateParameters { it.copy(zebraExposureEnabled = !it.zebraExposureEnabled) }
                }
                OverlayToggleChip(
                    label = state.captureParams.gridMode.label,
                    active = state.captureParams.gridMode != GridOverlayMode.OFF
                ) {
                    viewModel.updateParameters {
                        val next = GridOverlayMode.entries[(it.gridMode.ordinal + 1) % GridOverlayMode.entries.size]
                        it.copy(gridMode = next)
                    }
                }
                OverlayToggleChip(
                    label = "AE-L",
                    active = state.captureParams.exposureLocked
                ) {
                    viewModel.updateParameters { it.copy(exposureLocked = !it.exposureLocked) }
                }
            }

            if (state.countdownRemaining > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${state.countdownRemaining}",
                        color = AmberAccent,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            state.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable { viewModel.clearStatusMessage() }
                )
            }
        }

        // Manual Parameter Control Drawer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfacePanel)
                .border(1.dp, BorderHairline)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("ISO", "SHUTTER", "FOCUS", "WB", "EV", "ZOOM").forEach { tab ->
                    Text(
                        text = tab,
                        color = if (activeControlTab == tab) AmberAccent else Color(0xFFA1A1AA),
                        fontSize = 11.sp,
                        fontWeight = if (activeControlTab == tab) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clickable { activeControlTab = tab }
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            when (activeControlTab) {
                "ISO" -> {
                    val isoRange = cam?.isoRange
                    if (cam?.supportsManualSensor != true || isoRange == null) {
                        Text("Manual ISO: UNSUPPORTED on this camera", color = Color(0xFFEF4444), fontSize = 12.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OverlayToggleChip("AUTO", state.captureParams.isoAuto) {
                                viewModel.updateParameters { it.copy(isoAuto = !it.isoAuto) }
                            }
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = state.captureParams.manualIso.toFloat(),
                                onValueChange = { v ->
                                    viewModel.updateParameters { it.copy(isoAuto = false, manualIso = v.toInt()) }
                                },
                                valueRange = isoRange.lower.toFloat()..isoRange.upper.toFloat(),
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = AmberAccent, activeTrackColor = AmberAccent)
                            )
                            Text(
                                text = if (state.captureParams.isoAuto) "AUTO" else "${state.captureParams.manualIso}",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                "SHUTTER" -> {
                    val expRange = cam?.exposureTimeRangeNs
                    if (cam?.supportsManualSensor != true || expRange == null) {
                        Text("Manual Shutter: UNSUPPORTED on this camera", color = Color(0xFFEF4444), fontSize = 12.sp)
                    } else {
                        val currentMs = (state.captureParams.manualShutterNs / 1_000_000f).coerceIn(0.1f, 1000f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OverlayToggleChip("AUTO", state.captureParams.shutterAuto) {
                                viewModel.updateParameters { it.copy(shutterAuto = !it.shutterAuto) }
                            }
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = currentMs,
                                onValueChange = { ms ->
                                    val ns = (ms * 1_000_000L).toLong().coerceIn(expRange.lower, expRange.upper)
                                    viewModel.updateParameters { it.copy(shutterAuto = false, manualShutterNs = ns) }
                                },
                                valueRange = 0.1f..500f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = AmberAccent, activeTrackColor = AmberAccent)
                            )
                            Text(
                                text = if (state.captureParams.shutterAuto) "AUTO" else "${"%.1f".format(currentMs)}ms",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                "FOCUS" -> {
                    val maxD = cam?.minimumFocusDistanceDiopters
                    if (cam?.supportsManualFocus != true || maxD == null || maxD <= 0f) {
                        Text(
                            text = if (cam?.supportsAutofocus == true) "Manual Diopter: UNSUPPORTED (Autofocus Only)" else "Focus Control: UNSUPPORTED (Fixed / Lensless)",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OverlayToggleChip("AF", state.captureParams.autofocusEnabled) {
                                viewModel.updateParameters { it.copy(autofocusEnabled = !it.autofocusEnabled) }
                            }
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = state.captureParams.manualFocusDistanceDiopters,
                                onValueChange = { d ->
                                    viewModel.updateParameters { it.copy(autofocusEnabled = false, manualFocusDistanceDiopters = d) }
                                },
                                valueRange = 0f..maxD,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = CyanAccent, activeTrackColor = CyanAccent)
                            )
                            Text(
                                text = "${"%.2f".format(state.captureParams.manualFocusDistanceDiopters)}D",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                "WB" -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WhiteBalancePreset.entries.forEach { preset ->
                            OverlayToggleChip(
                                label = preset.label,
                                active = state.captureParams.whiteBalancePreset == preset
                            ) {
                                viewModel.updateParameters { it.copy(whiteBalancePreset = preset) }
                            }
                        }
                    }
                }
                "EV" -> {
                    val range = cam?.exposureCompensationRange
                    if (range == null) {
                        Text("EV Compensation: UNSUPPORTED", color = Color(0xFFEF4444), fontSize = 12.sp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = state.captureParams.exposureCompensationIndex.toFloat(),
                                onValueChange = { v ->
                                    viewModel.updateParameters { it.copy(exposureCompensationIndex = v.toInt()) }
                                },
                                valueRange = range.lower.toFloat()..range.upper.toFloat(),
                                modifier = Modifier.weight(1f)
                            )
                            val ev = state.captureParams.exposureCompensationIndex * (cam.exposureCompensationStep ?: 0.33f)
                            Text(
                                text = "${"%.1f".format(ev)} EV",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                "ZOOM" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = state.captureParams.digitalZoomRatio,
                            onValueChange = { z ->
                                viewModel.updateParameters { it.copy(digitalZoomRatio = z) }
                            },
                            valueRange = 1f..6f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${"%.1f".format(state.captureParams.digitalZoomRatio)}x",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Mode selector strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBlack)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PhotographyMode.entries.forEach { mode ->
                val selected = state.selectedMode == mode
                Text(
                    text = mode.displayName,
                    color = if (selected) AmberAccent else Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { viewModel.selectMode(mode) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Primary feature toggles row (Direct Sensor, AI Engine, Format, Timer/Burst)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverlayToggleChip(
                label = if (state.captureParams.directSensorMode) "DIRECT SENSOR: ON" else "DIRECT SENSOR",
                active = state.captureParams.directSensorMode
            ) {
                onOpenDirectSensor()
            }

            OverlayToggleChip(
                label = if (state.aiConfig.enabled) "AI: ON" else "AI: OFF",
                active = state.aiConfig.enabled
            ) {
                onOpenAiEngine()
            }

            val nextFormat = when (state.captureParams.outputFormat) {
                OutputFormat.JPEG -> if (cam?.supportsRawCapability == true) OutputFormat.RAW_AND_JPEG else OutputFormat.JPEG
                OutputFormat.RAW_AND_JPEG -> OutputFormat.RAW_DNG
                OutputFormat.RAW_DNG -> OutputFormat.JPEG
            }
            OverlayToggleChip(
                label = if (cam?.supportsRawCapability == true) state.captureParams.outputFormat.label else "JPEG (RAW UNSUPPORTED)",
                active = state.captureParams.outputFormat != OutputFormat.JPEG
            ) {
                viewModel.updateParameters { it.copy(outputFormat = nextFormat) }
            }
        }

        // Bottom Shutter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onOpenGallery,
                colors = ButtonDefaults.buttonColors(containerColor = SurfacePanel),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Gallery (${state.galleryRecords.size})", color = Color.White, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(if (state.isCapturing) AmberAccent else Color.White)
                    .clickable(enabled = !state.isCapturing) {
                        val isMacroSweep = state.selectedMode == PhotographyMode.MACRO ||
                            state.selectedMode == PhotographyMode.MICRO_MACRO
                        viewModel.triggerCapture(focusSweepEnabled = isMacroSweep)
                    }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OverlayToggleChip(
                    label = if (state.captureParams.timerSeconds == 0) "T:0" else "T:${state.captureParams.timerSeconds}s",
                    active = state.captureParams.timerSeconds > 0
                ) {
                    val next = when (state.captureParams.timerSeconds) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                    viewModel.updateParameters { it.copy(timerSeconds = next) }
                }

                if (state.cameras.size > 1) {
                    OverlayToggleChip(label = "CAM+", active = false) {
                        val idx = state.cameras.indexOfFirst { it.cameraId == cam?.cameraId }
                        val next = state.cameras[(idx + 1) % state.cameras.size]
                        viewModel.selectCamera(next.cameraId)
                    }
                }
            }
        }
    }
}

@Composable
private fun RgbHistogramOverlay(telemetry: LiveFrameTelemetry, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(6.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(6.dp))
            .padding(4.dp)
    ) {
        val bins = telemetry.lumaHistogram.size.coerceAtLeast(1)
        val maxVal = maxOf(
            telemetry.redHistogram.maxOrNull() ?: 1,
            telemetry.greenHistogram.maxOrNull() ?: 1,
            telemetry.blueHistogram.maxOrNull() ?: 1,
            1
        ).toFloat()

        fun drawChannel(arr: IntArray, color: Color) {
            val path = Path()
            for (i in arr.indices) {
                val x = (i.toFloat() / (bins - 1)) * size.width
                val y = size.height - (arr[i] / maxVal).coerceIn(0f, 1f) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 1.5f))
        }

        drawChannel(telemetry.redHistogram, Color(0xFFEF4444))
        drawChannel(telemetry.greenHistogram, Color(0xFF22C55E))
        drawChannel(telemetry.blueHistogram, Color(0xFF3B82F6))
    }
}

@Composable
fun OverlayToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (active) Color.Black else Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AmberAccent else SurfacePanel.copy(alpha = 0.85f))
            .border(1.dp, if (active) AmberAccent else BorderHairline, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun CompactTopButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, BorderHairline, RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}
