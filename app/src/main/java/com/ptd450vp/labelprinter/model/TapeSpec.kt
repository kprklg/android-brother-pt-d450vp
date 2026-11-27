package com.ptd450vp.labelprinter.model

import kotlinx.serialization.Serializable

/**
 * Supported Brother TZe tape widths for PT-D450 / PT-D450VP (180 dpi, 128-pin printhead).
 *
 * [mediaWidthByte] is the width value reported by the printer in status byte 10
 * and sent in the ESC i z print-info header.
 * [printablePixels] is the effective printable height in dots at 180 dpi (centered on 128 pins).
 */
@Serializable
enum class TapeWidth(
    val displayLabel: String,
    val widthMm: Float,
    val mediaWidthByte: Int,
    val printablePixels: Int
) {
    MM_3_5("3.5 мм", 3.5f, 4, 24),
    MM_6("6 мм", 6f, 6, 32),
    MM_9("9 мм", 9f, 9, 50),
    MM_12("12 мм", 12f, 12, 70),
    MM_18("18 мм", 18f, 18, 112);

    val totalHeightPixels: Int
        get() = (widthMm * DOTS_PER_MM).toInt().coerceAtMost(PRINTHEAD_PINS)

    companion object {
        const val DPI = 180
        const val DOTS_PER_MM = DPI / 25.4f
        const val PRINTHEAD_PINS = 128
        const val BYTES_PER_RASTER_LINE = PRINTHEAD_PINS / 8 // 16 bytes

        fun fromMediaWidthByte(value: Int): TapeWidth? =
            entries.firstOrNull { it.mediaWidthByte == value }
    }
}
