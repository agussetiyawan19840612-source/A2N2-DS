package com.naturalai.camerapro.processing

import android.media.Image

data class FrameAnalysisResult(
    val redBins: IntArray,
    val greenBins: IntArray,
    val blueBins: IntArray,
    val lumaBins: IntArray,
    val highlightClipPercent: Float,
    val shadowCrushPercent: Float,
    val laplacianFocusScore: Float
)

class FrameAnalyzer {

    fun analyzeYuvFrame(image: Image): FrameAnalysisResult {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val width = image.width
        val height = image.height
        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val redBins = IntArray(64)
        val greenBins = IntArray(64)
        val blueBins = IntArray(64)
        val lumaBins = IntArray(64)

        var clipped = 0
        var crushed = 0
        var sampled = 0
        var laplacianSum = 0.0
        var laplacianCount = 0

        val step = 4
        for (y in step until height - step step step) {
            val yRowOffset = y * yRowStride
            val uvRowOffset = (y / 2) * uvRowStride
            for (x in step until width - step step step) {
                val yIdx = yRowOffset + x
                if (yIdx >= yBuf.limit()) continue
                val luma = yBuf.get(yIdx).toInt() and 0xFF

                val uvIdx = uvRowOffset + (x / 2) * uvPixelStride
                val uVal = if (uvIdx < uBuf.limit()) (uBuf.get(uvIdx).toInt() and 0xFF) - 128 else 0
                val vVal = if (uvIdx < vBuf.limit()) (vBuf.get(uvIdx).toInt() and 0xFF) - 128 else 0

                val r = (luma + 1.402f * vVal).toInt().coerceIn(0, 255)
                val g = (luma - 0.344136f * uVal - 0.714136f * vVal).toInt().coerceIn(0, 255)
                val b = (luma + 1.772f * uVal).toInt().coerceIn(0, 255)

                redBins[r shr 2]++
                greenBins[g shr 2]++
                blueBins[b shr 2]++
                lumaBins[luma shr 2]++

                if (luma >= 250) clipped++
                if (luma <= 5) crushed++
                sampled++

                // 4-neighbor Laplacian inside central 60% ROI for optical focus assistance
                if (x in (width / 5)..(width * 4 / 5) && y in (height / 5)..(height * 4 / 5)) {
                    val left = yBuf.get(yRowOffset + (x - 1)).toInt() and 0xFF
                    val right = yBuf.get(yRowOffset + (x + 1)).toInt() and 0xFF
                    val up = yBuf.get((y - 1) * yRowStride + x).toInt() and 0xFF
                    val down = yBuf.get((y + 1) * yRowStride + x).toInt() and 0xFF
                    val lap = 4 * luma - (left + right + up + down)
                    laplacianSum += (lap * lap).toDouble()
                    laplacianCount++
                }
            }
        }

        val denom = sampled.coerceAtLeast(1).toFloat()
        val focusScore = if (laplacianCount > 0) {
            (laplacianSum / laplacianCount).toFloat()
        } else 0f

        return FrameAnalysisResult(
            redBins = redBins,
            greenBins = greenBins,
            blueBins = blueBins,
            lumaBins = lumaBins,
            highlightClipPercent = (clipped / denom) * 100f,
            shadowCrushPercent = (crushed / denom) * 100f,
            laplacianFocusScore = focusScore
        )
    }
}
