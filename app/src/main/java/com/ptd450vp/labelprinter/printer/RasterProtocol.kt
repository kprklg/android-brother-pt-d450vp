package com.ptd450vp.labelprinter.printer

import com.ptd450vp.labelprinter.model.PrintOptions
import com.ptd450vp.labelprinter.model.TapeWidth
import java.io.ByteArrayOutputStream

/**
 * Pure 1-bit monochrome bitmap ready to be encoded into Brother raster lines.
 *
 * [width] = label length in pixels along the tape feed direction (number of raster lines).
 * [height] = label printable height in pixels across the tape width (<= 128 pins).
 * [pixels] = row-major boolean array (`pixels[y * width + x] == true` means black dot printed).
 */
data class MonochromeImage(
    val width: Int,
    val height: Int,
    val pixels: BooleanArray
) {
    init {
        require(width > 0) { "width must be > 0" }
        require(height in 1..TapeWidth.PRINTHEAD_PINS) {
            "height must be in 1..${TapeWidth.PRINTHEAD_PINS}, got $height"
        }
        require(pixels.size == width * height) {
            "pixels size (${pixels.size}) must equal width * height (${width * height})"
        }
    }

    fun isBlack(x: Int, y: Int): Boolean = pixels[y * width + x]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MonochromeImage) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

/**
 * Encoded binary print job plus diagnostic metadata for preview & debugging.
 */
data class PrintJobPacket(
    val bytes: ByteArray,
    val tapeWidth: TapeWidth,
    val rasterLineCount: Int,
    val emptyLineCount: Int,
    val copies: Int,
    val compressed: Boolean
) {
    val labelLengthMm: Float
        get() = rasterLineCount / TapeWidth.DOTS_PER_MM

    fun headerHexPreview(maxBytes: Int = 160): String {
        val count = bytes.size.coerceAtMost(maxBytes)
        return buildString {
            for (i in 0 until count) {
                if (i > 0) append(if (i % 16 == 0) '\n' else ' ')
                append("%02X".format(bytes[i].toInt() and 0xFF))
            }
            if (bytes.size > maxBytes) {
                append("\n... (ещё ${bytes.size - maxBytes} байт)")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrintJobPacket) return false
        return bytes.contentEquals(other.bytes) &&
            tapeWidth == other.tapeWidth &&
            rasterLineCount == other.rasterLineCount &&
            emptyLineCount == other.emptyLineCount &&
            copies == other.copies &&
            compressed == other.compressed
    }

    override fun hashCode(): Int = bytes.contentHashCode()
}

/**
 * Brother P-touch Raster Command Protocol encoder targeting PT-D450 / PT-D450VP.
 */
object RasterProtocol {
    const val BROTHER_VENDOR_ID = 0x04F9
    const val PTD450_PRODUCT_ID = 0x2073

    private const val INVALIDATE_NULL_COUNT = 100

    /** Commands to clear buffer, initialize, and request a 32-byte status packet (`ESC i S`). */
    fun buildStatusRequest(): ByteArray {
        val out = ByteArrayOutputStream(INVALIDATE_NULL_COUNT + 5)
        repeat(INVALIDATE_NULL_COUNT) { out.write(0x00) }
        out.write(byteArrayOf(0x1B, 0x40)) // ESC @ (Initialize)
        out.write(byteArrayOf(0x1B, 0x69, 0x53)) // ESC i S (Status info request)
        return out.toByteArray()
    }

    /**
     * Converts a [MonochromeImage] into a sequence of 16-byte raster lines centered on the 128-pin printhead.
     *
     * Each column `x` in `0 until image.width` becomes one 16-byte raster line.
     */
    fun imageToRasterLines(
        image: MonochromeImage,
        mirrorVertical: Boolean = true
    ): List<ByteArray> {
        val marginPins = (TapeWidth.PRINTHEAD_PINS - image.height) / 2
        val lines = ArrayList<ByteArray>(image.width)

        for (x in 0 until image.width) {
            val line = ByteArray(TapeWidth.BYTES_PER_RASTER_LINE)
            for (y in 0 until image.height) {
                if (image.isBlack(x, y)) {
                    val rawPin = marginPins + y
                    val pin = if (mirrorVertical) {
                        (TapeWidth.PRINTHEAD_PINS - 1) - rawPin
                    } else {
                        rawPin
                    }
                    if (pin in 0 until TapeWidth.PRINTHEAD_PINS) {
                        val byteIndex = pin / 8
                        val bitMask = 1 shl (7 - (pin % 8))
                        line[byteIndex] = (line[byteIndex].toInt() or bitMask).toByte()
                    }
                }
            }
            lines.add(line)
        }
        return lines
    }

    /**
     * Builds the complete binary raster job ready to send over USB Bulk OUT to Brother PT-D450VP.
     */
    fun buildPrintJob(
        image: MonochromeImage,
        tapeWidth: TapeWidth,
        options: PrintOptions = PrintOptions(),
        mediaTypeByte: Int = 0x01
    ): PrintJobPacket {
        val copies = options.copies.coerceIn(1, 99)
        val rasterLines = imageToRasterLines(image, mirrorVertical = options.mirrorVertical)
        val out = ByteArrayOutputStream()

        // 1. Invalidate print buffer (100 zero bytes)
        repeat(INVALIDATE_NULL_COUNT) { out.write(0x00) }

        // 2. Initialize (ESC @)
        out.write(byteArrayOf(0x1B, 0x40))

        // 3. Switch dynamic command mode to Raster mode (ESC i a 0x01)
        out.write(byteArrayOf(0x1B, 0x69, 0x61, 0x01))

        var emptyLineCount = 0

        for (copyIndex in 0 until copies) {
            val isLastCopy = copyIndex == copies - 1

            // 4. Print information command (ESC i z)
            // Flags: 0x02 (media type) | 0x04 (media width) | 0x40 (quality/priority) | 0x80 (recovery on) = 0x86
            val lineCount = rasterLines.size
            out.write(
                byteArrayOf(
                    0x1B, 0x69, 0x7A,
                    0x86.toByte(), // valid flags: recovery + media width + media type
                    mediaTypeByte.toByte(),
                    tapeWidth.mediaWidthByte.toByte(),
                    0x00, // media length = 0 (continuous tape)
                    (lineCount and 0xFF).toByte(),
                    ((lineCount ushr 8) and 0xFF).toByte(),
                    ((lineCount ushr 16) and 0xFF).toByte(),
                    ((lineCount ushr 24) and 0xFF).toByte(),
                    if (copyIndex == 0) 0x00 else 0x01, // starting page (0) vs subsequent page (1)
                    0x00
                )
            )

            // 5. Various mode settings (ESC i M)
            // PT-D450 has manual cutter, leave bit 6 (auto-cut) off (0x00)
            out.write(byteArrayOf(0x1B, 0x69, 0x4D, 0x00))

            // 6. Advanced mode settings (ESC i K)
            // Bit 3 (0x08) = No chain printing (feed at end). Clear bit 3 when chainPrint is enabled.
            val advancedMode = if (options.chainPrint) 0x00 else 0x08
            out.write(byteArrayOf(0x1B, 0x69, 0x4B, advancedMode.toByte()))

            // 7. Specify margin amount (ESC i d <lo> <hi>)
            val margin = options.feedMarginDots.coerceIn(0, 600)
            out.write(
                byteArrayOf(
                    0x1B, 0x69, 0x64,
                    (margin and 0xFF).toByte(),
                    ((margin ushr 8) and 0xFF).toByte()
                )
            )

            // 8. Select compression mode: 0x02 = TIFF PackBits (standard for PT-D450), 0x00 = uncompressed
            out.write(byteArrayOf(0x4D, if (options.useCompression) 0x02 else 0x00))

            // 9. Raster graphics transfer
            for (line in rasterLines) {
                val isEmpty = line.all { it == 0.toByte() }
                if (isEmpty) {
                    if (copyIndex == 0) emptyLineCount++
                    out.write(0x5A) // 'Z' — zero raster line
                } else {
                    val payload = if (options.useCompression) {
                        PackBitsEncoder.encode(line)
                    } else {
                        line
                    }
                    out.write(0x47) // 'G' — raster graphics transfer
                    out.write(payload.size and 0xFF)
                    out.write((payload.size ushr 8) and 0xFF)
                    out.write(payload)
                }
            }

            // 10. Print command: 0x1A = print with feeding (last page), 0x0C = print (next page follows)
            out.write(if (isLastCopy) 0x1A else 0x0C)
        }

        return PrintJobPacket(
            bytes = out.toByteArray(),
            tapeWidth = tapeWidth,
            rasterLineCount = rasterLines.size,
            emptyLineCount = emptyLineCount,
            copies = copies,
            compressed = options.useCompression
        )
    }
}
