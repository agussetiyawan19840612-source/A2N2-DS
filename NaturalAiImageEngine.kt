package com.naturalai.camerapro.processing

import android.content.Context
import android.graphics.Bitmap
import com.naturalai.camerapro.domain.model.NaturalAiConfig
import com.naturalai.camerapro.domain.model.OpticalProfile
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class AiProcessingOutcome(
    val bitmap: Bitmap,
    val engineUsed: String
)

interface NaturalAiEngine {
    val isNeuralModelLoaded: Boolean
    val engineDescription: String
    fun enhance(
        frames: List<Bitmap>,
        config: NaturalAiConfig,
        opticalProfile: OpticalProfile
    ): AiProcessingOutcome
}

/**
 * TFLite neural interface with honest fallback:
 * Loads assets/models/natural_enhance.tflite if supplied at build time;
 * otherwise explicitly reports isNeuralModelLoaded = false and delegates to
 * DeterministicComputationalEngine without faking neural inference.
 */
class HybridNaturalAiEngine(
    context: Context
) : NaturalAiEngine {

    private var interpreter: Interpreter? = null
    private val fallbackEngine = DeterministicComputationalEngine()

    init {
        interpreter = try {
            val afd = context.assets.openFd("models/natural_enhance.tflite")
            FileInputStream(afd.fileDescriptor).channel.use { channel ->
                val mapped = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength
                )
                Interpreter(mapped)
            }
        } catch (_: Exception) {
            null
        }
    }

    override val isNeuralModelLoaded: Boolean
        get() = interpreter != null

    override val engineDescription: String
        get() = if (interpreter != null) {
            "TFLite Local Model + Computational Pipeline"
        } else {
            "Deterministic Computational Pipeline (No TFLite Model Bundled)"
        }

    override fun enhance(
        frames: List<Bitmap>,
        config: NaturalAiConfig,
        opticalProfile: OpticalProfile
    ): AiProcessingOutcome {
        val base = fallbackEngine.enhance(frames, config, opticalProfile)
        val tflite = interpreter ?: return base

        // Optional TFLite residual pass if user provides a compatible 512x512 RGB float model
        return try {
            val w = base.bitmap.width
            val h = base.bitmap.height
            val scaled = Bitmap.createScaledBitmap(base.bitmap, 512, 512, true)
            val input = ByteBuffer.allocateDirect(1 * 512 * 512 * 3 * 4).order(ByteOrder.nativeOrder())
            val px = IntArray(512 * 512)
            scaled.getPixels(px, 0, 512, 0, 0, 512, 512)
            for (c in px) {
                input.putFloat(((c shr 16) and 0xFF) / 255f)
                input.putFloat(((c shr 8) and 0xFF) / 255f)
                input.putFloat((c and 0xFF) / 255f)
            }
            val output = ByteBuffer.allocateDirect(1 * 512 * 512 * 3 * 4).order(ByteOrder.nativeOrder())
            tflite.run(input, output)
            output.rewind()
            val outPx = IntArray(512 * 512)
            for (i in outPx.indices) {
                val r = (output.float * 255f).toInt().coerceIn(0, 255)
                val g = (output.float * 255f).toInt().coerceIn(0, 255)
                val b = (output.float * 255f).toInt().coerceIn(0, 255)
                outPx[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            val out512 = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            out512.setPixels(outPx, 0, 512, 0, 0, 512, 512)
            val finalBmp = Bitmap.createScaledBitmap(out512, w, h, true)
            AiProcessingOutcome(finalBmp, "TFLite Local Model")
        } catch (_: Exception) {
            base
        }
    }
}

/**
 * Real pixel-level computational photography engine:
 * - Multi-frame temporal averaging (for Low Light / Night / Micro Macro)
 * - Optical radial vignette & RGB balance correction
 * - Edge-aware bilateral smoothing scaled by Noise Reduction & Texture Preservation
 * - Halo-clamped unsharp mask (prevents white/dark edge halos or fake texture)
 * - Tone-preserving highlight recovery & shadow lift
 */
class DeterministicComputationalEngine : NaturalAiEngine {

    override val isNeuralModelLoaded: Boolean = false
    override val engineDescription: String = "Deterministic Computational Pipeline"

    override fun enhance(
        frames: List<Bitmap>,
        config: NaturalAiConfig,
        opticalProfile: OpticalProfile
    ): AiProcessingOutcome {
        require(frames.isNotEmpty()) { "At least one frame is required" }
        val first = frames.first()
        val w = first.width
        val h = first.height
        val count = w * h

        // 1. Multi-frame temporal averaging (suppresses sensor shot noise without hallucinating detail)
        val accR = FloatArray(count)
        val accG = FloatArray(count)
        val accB = FloatArray(count)
        val rowBuf = IntArray(count)

        for (frame in frames) {
            val aligned = if (frame.width == w && frame.height == h) frame else {
                Bitmap.createScaledBitmap(frame, w, h, true)
            }
            aligned.getPixels(rowBuf, 0, w, 0, 0, w, h)
            for (i in 0 until count) {
                val c = rowBuf[i]
                accR[i] += ((c shr 16) and 0xFF)
                accG[i] += ((c shr 8) and 0xFF)
                accB[i] += (c and 0xFF)
            }
        }
        val invN = 1f / frames.size
        for (i in 0 until count) {
            accR[i] *= invN
            accG[i] *= invN
            accB[i] *= invN
        }

        if (!config.enabled) {
            val out = IntArray(count)
            for (i in 0 until count) {
                out[i] = (0xFF shl 24) or
                    (accR[i].toInt().coerceIn(0, 255) shl 16) or
                    (accG[i].toInt().coerceIn(0, 255) shl 8) or
                    accB[i].toInt().coerceIn(0, 255)
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(out, 0, w, 0, 0, w, h)
            return AiProcessingOutcome(bmp, "Bypass (Sensor Direct)")
        }

        // 2. Optical Vignette & RGB Balance Trim
        val cx = w * 0.5f
        val cy = h * 0.5f
        val maxR2 = cx * cx + cy * cy
        val vigBoost = opticalProfile.vignettingCorrection * 0.45f
        val wbShift = (config.rgbBalance - 50) / 50f * 0.15f
        val rGain = opticalProfile.redGainTrim * (1f + wbShift)
        val gGain = opticalProfile.greenGainTrim
        val bGain = opticalProfile.blueGainTrim * (1f - wbShift)

        val luma = FloatArray(count)
        for (y in 0 until h) {
            val dy = y - cy
            for (x in 0 until w) {
                val idx = y * w + x
                val dx = x - cx
                val rNorm = (dx * dx + dy * dy) / max(1f, maxR2)
                val vFactor = 1f + vigBoost * rNorm
                accR[idx] = (accR[idx] * vFactor * rGain).coerceIn(0f, 255f)
                accG[idx] = (accG[idx] * vFactor * gGain).coerceIn(0f, 255f)
                accB[idx] = (accB[idx] * vFactor * bGain).coerceIn(0f, 255f)
                luma[idx] = 0.2126f * accR[idx] + 0.7152f * accG[idx] + 0.0722f * accB[idx]
            }
        }

        // 3. Edge-preserving luma smoothing & local mean (for Noise Reduction + Micro Contrast + Halo-free Sharpness)
        val nrStrength = (config.noiseReduction / 100f) * (1f - (config.texturePreservation / 100f) * 0.65f)
        val sharpStrength = (config.naturalSharpness / 100f) * 0.9f + (config.detailRecovery / 100f) * 0.55f
        val microContrastAmt = (config.microContrast / 100f) * 0.35f
        val hlRec = config.highlightRecovery / 100f
        val shRec = (config.shadowRecovery / 100f) * 0.65f + (config.lowLightRecovery / 100f) * 0.55f
        val satFactor = 1f + (config.naturalColor - 50) / 50f * 0.35f

        val out = IntArray(count)
        val rangeSigma = 18f + nrStrength * 24f

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val y0 = luma[idx]

                var sumW = 0f
                var sumL = 0f
                for (ky in -1..1) {
                    val yy = (y + ky).coerceIn(0, h - 1)
                    for (kx in -1..1) {
                        val xx = (x + kx).coerceIn(0, w - 1)
                        val nL = luma[yy * w + xx]
                        val diff = abs(nL - y0)
                        val wVal = exp(-(diff * diff) / (2f * rangeSigma * rangeSigma))
                        sumL += nL * wVal
                        sumW += wVal
                    }
                }
                val smoothL = sumL / max(0.001f, sumW)

                // Detail residual with overshoot clamping (prevents halos!)
                val detail = (y0 - smoothL).coerceIn(-24f, 24f)
                val denoisedL = y0 * (1f - nrStrength) + smoothL * nrStrength
                var enhancedL = denoisedL + detail * sharpStrength

                // Micro-contrast around midtones
                enhancedL += (enhancedL - smoothL) * microContrastAmt

                // Tone-preserving Shadow Lift & Highlight Recovery (preserves night depth)
                val normY = (enhancedL / 255f).coerceIn(0f, 1f)
                var mappedY = normY
                if (shRec > 0.01f && normY < 0.45f) {
                    val shadowMask = (1f - normY / 0.45f).pow(2f)
                    mappedY += shRec * 0.18f * shadowMask * (0.15f + normY)
                }
                if (hlRec > 0.01f && normY > 0.68f) {
                    val hlMask = ((normY - 0.68f) / 0.32f).pow(1.5f)
                    mappedY -= hlRec * 0.14f * hlMask
                }
                mappedY = mappedY.coerceIn(0f, 1f)

                val targetL = mappedY * 255f
                val lumaRatio = if (y0 > 1f) targetL / y0 else 1f

                // Scale RGB proportionally to preserve natural hue, then apply gentle chroma scaling
                var r = accR[idx] * lumaRatio
                var g = accG[idx] * lumaRatio
                var b = accB[idx] * lumaRatio

                val finalY = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = finalY + (r - finalY) * satFactor
                g = finalY + (g - finalY) * satFactor
                b = finalY + (b - finalY) * satFactor

                out[idx] = (0xFF shl 24) or
                    (r.toInt().coerceIn(0, 255) shl 16) or
                    (g.toInt().coerceIn(0, 255) shl 8) or
                    b.toInt().coerceIn(0, 255)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return AiProcessingOutcome(
            bitmap = result,
            engineUsed = if (frames.size > 1) "Multi-Frame (${frames.size}x) + Natural AI" else engineDescription
        )
    }
}
