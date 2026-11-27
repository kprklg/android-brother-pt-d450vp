package com.ptd450vp.labelprinter.render

import com.ptd450vp.labelprinter.model.DitherMode

/**
 * Pure-Kotlin 1-bit binarization and Floyd-Steinberg error-diffusion dithering
 * for converting ARGB bitmaps into thermal-ready monochrome pixels.
 */
object Dithering {

    /**
     * Converts ARGB pixels into a row-major BooleanArray where `true` = black dot to print.
     * Transparent pixels are composited onto white background before luminance calculation.
     */
    fun toMonochrome(
        argbPixels: IntArray,
        width: Int,
        height: Int,
        mode: DitherMode = DitherMode.THRESHOLD,
        threshold: Int = 140,
        invert: Boolean = false
    ): BooleanArray {
        val gray = FloatArray(width * height)
        for (i in argbPixels.indices) {
            val c = argbPixels[i]
            val a = ((c ushr 24) and 0xFF) / 255f
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            // Alpha-composite over white (255)
            val cr = r * a + 255f * (1f - a)
            val cg = g * a + 255f * (1f - a)
            val cb = b * a + 255f * (1f - a)
            gray[i] = 0.299f * cr + 0.587f * cg + 0.114f * cb
        }

        val out = BooleanArray(width * height)
        val thresh = threshold.coerceIn(1, 254).toFloat()

        when (mode) {
            DitherMode.THRESHOLD -> {
                for (i in gray.indices) {
                    val isBlack = gray[i] < thresh
                    out[i] = if (invert) !isBlack else isBlack
                }
            }
            DitherMode.FLOYD_STEINBERG -> {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val idx = y * width + x
                        val oldVal = gray[idx]
                        val newVal = if (oldVal < thresh) 0f else 255f
                        val isBlack = newVal == 0f
                        out[idx] = if (invert) !isBlack else isBlack
                        val err = oldVal - newVal

                        if (x + 1 < width) {
                            gray[idx + 1] += err * (7f / 16f)
                        }
                        if (y + 1 < height) {
                            val nextRow = (y + 1) * width
                            if (x - 1 >= 0) gray[nextRow + x - 1] += err * (3f / 16f)
                            gray[nextRow + x] += err * (5f / 16f)
                            if (x + 1 < width) gray[nextRow + x + 1] += err * (1f / 16f)
                        }
                    }
                }
            }
        }
        return out
    }
}
