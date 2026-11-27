package com.ptd450vp.labelprinter.render

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ptd450vp.labelprinter.model.BarcodeFormatOption
import com.ptd450vp.labelprinter.model.QrErrorCorrection

/**
 * Generates crisp pixel-aligned QR codes and 1D/2D barcodes via ZXing Core
 * for 180 dpi thermal label printing.
 */
object BarcodeGenerator {

    fun encodeQrMatrix(
        content: String,
        targetSizePx: Int,
        errorCorrection: QrErrorCorrection
    ): Result<BitMatrix> = runCatching {
        require(content.isNotBlank()) { "Содержимое QR-кода не может быть пустым" }
        val ecLevel = when (errorCorrection) {
            QrErrorCorrection.L -> ErrorCorrectionLevel.L
            QrErrorCorrection.M -> ErrorCorrectionLevel.M
            QrErrorCorrection.Q -> ErrorCorrectionLevel.Q
            QrErrorCorrection.H -> ErrorCorrectionLevel.H
        }
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ecLevel,
            EncodeHintType.MARGIN to 1
        )
        val size = targetSizePx.coerceAtLeast(21)
        MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    }

    fun encodeBarcodeMatrix(
        content: String,
        format: BarcodeFormatOption,
        heightPx: Int,
        moduleWidthPx: Int
    ): Result<BitMatrix> = runCatching {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "Содержимое штрихкода не может быть пустым" }

        val zxingFormat = when (format) {
            BarcodeFormatOption.CODE_128 -> BarcodeFormat.CODE_128
            BarcodeFormatOption.EAN_13 -> BarcodeFormat.EAN_13
            BarcodeFormatOption.CODE_39 -> BarcodeFormat.CODE_39
            BarcodeFormatOption.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        }

        val normalizedContent = when (format) {
            BarcodeFormatOption.EAN_13 -> {
                val digits = trimmed.filter { it.isDigit() }
                require(digits.length == 12 || digits.length == 13) {
                    "EAN-13 требует 12 или 13 цифр (сейчас: ${digits.length})"
                }
                digits
            }
            BarcodeFormatOption.CODE_39 -> trimmed.uppercase()
            else -> trimmed
        }

        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )

        if (zxingFormat == BarcodeFormat.DATA_MATRIX) {
            val size = heightPx.coerceAtLeast(16)
            return@runCatching MultiFormatWriter().encode(
                normalizedContent,
                BarcodeFormat.DATA_MATRIX,
                size,
                size,
                hints
            )
        }

        // First encode at minimal width (0) to get exact module count, then scale cleanly by integer moduleWidthPx
        val baseMatrix = MultiFormatWriter().encode(
            normalizedContent,
            zxingFormat,
            0,
            1,
            hints
        )
        val scale = moduleWidthPx.coerceIn(1, 5)
        val finalWidth = (baseMatrix.width * scale).coerceAtLeast(20)
        val finalHeight = heightPx.coerceAtLeast(12)
        val scaled = BitMatrix(finalWidth, finalHeight)
        for (x in 0 until baseMatrix.width) {
            if (baseMatrix[x, 0]) {
                val startX = x * scale
                for (dx in 0 until scale) {
                    for (y in 0 until finalHeight) {
                        scaled.set(startX + dx, y)
                    }
                }
            }
        }
        scaled
    }

    fun bitMatrixToBitmap(matrix: BitMatrix): Bitmap {
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                pixels[rowOffset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
