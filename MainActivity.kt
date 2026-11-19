package com.naturalai.camerapro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naturalai.camerapro.data.PermissionManager
import com.naturalai.camerapro.ui.screens.AiEngineSheet
import com.naturalai.camerapro.ui.screens.CompatibilityScreen
import com.naturalai.camerapro.ui.screens.DirectSensorScreen
import com.naturalai.camerapro.ui.screens.GalleryScreen
import com.naturalai.camerapro.ui.screens.MainCameraScreen
import com.naturalai.camerapro.ui.screens.OpticalProfilesScreen
import com.naturalai.camerapro.ui.theme.NaturalCameraTheme
import com.naturalai.camerapro.ui.theme.ObsidianBlack
import com.naturalai.camerapro.ui.viewmodel.CameraViewModel

enum class AppScreen {
    CAMERA,
    DIRECT_SENSOR,
    AI_ENGINE,
    OPTICAL_PROFILES,
    COMPATIBILITY,
    GALLERY
}

class MainActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NaturalCameraTheme {
                var hasPermission by remember {
                    mutableStateOf(PermissionManager.hasCameraPermission(this))
                }
                var activeScreen by remember { mutableStateOf(AppScreen.CAMERA) }
                val uiState by viewModel.uiState.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    val granted = result[android.Manifest.permission.CAMERA] == true
                    hasPermission = granted
                    if (granted) {
                        viewModel.refreshCapabilities()
                    }
                }

                LaunchedEffect(Unit) {
                    if (!hasPermission) {
                        permissionLauncher.launch(PermissionManager.requiredPermissions())
                    }
                }

                if (!hasPermission) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianBlack)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Camera permission is required to inspect Camera2 hardware and capture photos.",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            permissionLauncher.launch(PermissionManager.requiredPermissions())
                        }) {
                            Text("Grant Camera Permission")
                        }
                    }
                } else {
                    when (activeScreen) {
                        AppScreen.CAMERA -> MainCameraScreen(
                            state = uiState,
                            viewModel = viewModel,
                            onOpenDirectSensor = { activeScreen = AppScreen.DIRECT_SENSOR },
                            onOpenAiEngine = { activeScreen = AppScreen.AI_ENGINE },
                            onOpenProfiles = { activeScreen = AppScreen.OPTICAL_PROFILES },
                            onOpenCompatibility = { activeScreen = AppScreen.COMPATIBILITY },
                            onOpenGallery = { activeScreen = AppScreen.GALLERY }
                        )
                        AppScreen.DIRECT_SENSOR -> DirectSensorScreen(
                            state = uiState,
                            viewModel = viewModel,
                            onOpenOpticalProfiles = { activeScreen = AppScreen.OPTICAL_PROFILES },
                            onBack = { activeScreen = AppScreen.CAMERA }
                        )
                        AppScreen.AI_ENGINE -> AiEngineSheet(
                            state = uiState,
                            viewModel = viewModel,
                            onBack = { activeScreen = AppScreen.CAMERA }
                        )
                        AppScreen.OPTICAL_PROFILES -> OpticalProfilesScreen(
                            state = uiState,
                            viewModel = viewModel,
                            onBack = { activeScreen = AppScreen.CAMERA }
                        )
                        AppScreen.COMPATIBILITY -> CompatibilityScreen(
                            state = uiState,
                            onSelectCamera = { id ->
                                viewModel.selectCamera(id)
                                activeScreen = AppScreen.CAMERA
                            },
                            onBack = { activeScreen = AppScreen.CAMERA }
                        )
                        AppScreen.GALLERY -> GalleryScreen(
                            state = uiState,
                            onBack = { activeScreen = AppScreen.CAMERA }
                        )
                    }
                }
            }
        }
    }
}
