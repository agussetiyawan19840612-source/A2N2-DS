package com.naturalai.camerapro.domain.model

import android.net.Uri

data class CapturedPhotoRecord(
    val id: String,
    val displayName: String,
    val jpegUri: Uri?,
    val dngUri: Uri?,
    val beforeAiUri: Uri?,
    val dateTimeText: String,
    val modeName: String,
    val cameraId: String,
    val iso: String,
    val shutterSpeed: String,
    val whiteBalance: String,
    val resolution: String,
    val formatLabel: String,
    val aiApplied: Boolean,
    val engineLabel: String
)
