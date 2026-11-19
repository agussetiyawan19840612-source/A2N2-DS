# Natural AI Camera Pro — Direct Sensor Edition (Android Native)

## 1. Environment & APK Build Instructions

Open the `android-project/` folder in **Android Studio (Ladybug / 2024.2+)** with JDK 17 and Android SDK Platform 35 installed, or build from terminal:

```bash
# 1. Generate Gradle wrapper if needed (or open in Android Studio which generates it automatically)
gradle wrapper --gradle-version 8.10.2

# 2. Build debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk

# 3. Install via ADB onto connected Android device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 2. Optional Local TensorFlow Lite Model Integration

The `HybridNaturalAiEngine` checks `app/src/main/assets/models/natural_enhance.tflite` at runtime.
- If no `.tflite` file is bundled, `isNeuralModelLoaded` reports `false` and the app runs `DeterministicComputationalEngine` (real multi-frame temporal averaging, edge-preserving bilateral denoise, halo-clamped unsharp mask, highlight/shadow tone recovery, and optical vignette correction).
- To enable local TFLite enhancement, place a compatible `512x512 RGB float32` enhancement model at:
  `app/src/main/assets/models/natural_enhance.tflite`

## 3. Direct Sensor & External Optics Hardware Integration

1. **Lensless / Direct Sensor Modification**:
   - Enable **Direct Sensor Mode** in the app (`PhotographyMode.DIRECT_SENSOR` or toggle inside Direct Sensor Console).
   - This sets Camera2 `CONTROL_AF_MODE_OFF`, `EDGE_MODE_OFF`, `NOISE_REDUCTION_MODE_MINIMAL`, `SHADING_MODE_OFF`, and `COLOR_CORRECTION_ABERRATION_MODE_OFF` so stock ISP assumptions do not distort raw optical projection.
2. **Pink Corner & Vignetting Compensation**:
   - Bare smartphone sensors have Chief Ray Angle (CRA) microlens shifts when paired with telecentric or long-flange CCTV/DSLR glass.
   - Open **Optical Profiles** and tune `Vignetting Correction`, `Red Gain Trim`, and `Blue Gain Trim` while monitoring the live RGB Histogram and Highlight/Shadow Clip percentages.
3. **External Focus Without Hardware AF**:
   - When the stock VCM voice-coil actuator is removed or bypassed, Camera2 `LENS_INFO_MINIMUM_FOCUS_DISTANCE` may report `0` or `UNSUPPORTED`.
   - Enable **PEAK** (OpenGL ES Sobel focus peaking) and watch the **Laplacian Contrast Score** inside the Direct Sensor Console while turning your external DSLR/CCTV/microscope focus ring or micrometer stage.
