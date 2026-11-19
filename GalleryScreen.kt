package com.naturalai.camerapro.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naturalai.camerapro.domain.model.CapturedPhotoRecord
import com.naturalai.camerapro.ui.theme.AmberAccent
import com.naturalai.camerapro.ui.theme.BorderHairline
import com.naturalai.camerapro.ui.theme.ObsidianBlack
import com.naturalai.camerapro.ui.theme.SurfacePanel
import com.naturalai.camerapro.ui.viewmodel.CameraUiState

@Composable
fun GalleryScreen(
    state: CameraUiState,
    onBack: () -> Unit
) {
    var selectedRecord by remember(state.galleryRecords) {
        mutableStateOf(state.galleryRecords.firstOrNull())
    }
    var splitRatio by remember { mutableFloatStateOf(0.5f) }
    val context = LocalContext.current

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
            Text("Captured Gallery & EXIF", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Close", color = AmberAccent, modifier = Modifier.clickable { onBack() })
        }

        if (state.galleryRecords.isEmpty()) {
            Text(
                text = "No captures in current session yet. Photos are saved to Pictures/NaturalAICameraPro.",
                color = Color(0xFFA1A1AA),
                fontSize = 13.sp
            )
            return@Column
        }

        selectedRecord?.let { rec ->
            val afterBmp = remember(rec.jpegUri) {
                rec.jpegUri?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }
            }
            val beforeBmp = remember(rec.beforeAiUri) {
                rec.beforeAiUri?.path?.let { BitmapFactory.decodeFile(it) }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Color.Black, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
            ) {
                if (afterBmp != null) {
                    Image(
                        bitmap = afterBmp.asImageBitmap(),
                        contentDescription = rec.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (beforeBmp != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(splitRatio)
                            .height(320.dp)
                            .clipToBounds()
                    ) {
                        Image(
                            bitmap = beforeBmp.asImageBitmap(),
                            contentDescription = "Before AI",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (beforeBmp != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Before AI", color = Color(0xFFA1A1AA), fontSize = 11.sp)
                    Text("After AI", color = AmberAccent, fontSize = 11.sp)
                }
                Slider(value = splitRatio, onValueChange = { splitRatio = it }, valueRange = 0f..1f)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfacePanel, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(rec.displayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                ExifLine("Mode", rec.modeName)
                ExifLine("Timestamp", rec.dateTimeText)
                ExifLine("Camera ID", rec.cameraId)
                ExifLine("ISO", rec.iso)
                ExifLine("Shutter Speed", rec.shutterSpeed)
                ExifLine("White Balance", rec.whiteBalance)
                ExifLine("Resolution", rec.resolution)
                ExifLine("Format", rec.formatLabel)
                ExifLine("Processing", rec.engineLabel)
            }
        }

        Spacer(Modifier.height(4.dp))
        state.galleryRecords.forEach { item: CapturedPhotoRecord ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfacePanel, RoundedCornerShape(8.dp))
                    .border(1.dp, if (selectedRecord?.id == item.id) AmberAccent else BorderHairline, RoundedCornerShape(8.dp))
                    .clickable { selectedRecord = item }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.displayName, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(item.formatLabel, color = AmberAccent, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ExifLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFA1A1AA), fontSize = 11.sp)
        Text(value, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}
