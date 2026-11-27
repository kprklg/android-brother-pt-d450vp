package com.ptd450vp.labelprinter

import com.ptd450vp.labelprinter.model.PrintOptions
import com.ptd450vp.labelprinter.model.TapeWidth
import com.ptd450vp.labelprinter.printer.MonochromeImage
import com.ptd450vp.labelprinter.printer.PackBitsEncoder
import com.ptd450vp.labelprinter.printer.RasterProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterProtocolTest {

    @Test
    fun statusRequestContainsNullsEscAtAndEscIS() {
        val req = RasterProtocol.buildStatusRequest()
        assertEquals(105, req.size)
        for (i in 0 until 100) {
            assertEquals(0.toByte(), req[i])
        }
        assertArrayEquals(
            byteArrayOf(0x1B, 0x40, 0x1B, 0x69, 0x53),
            req.copyOfRange(100, 105)
        )
    }

    @Test
    fun buildPrintJobEmitsValidHeaderPackBitsAndTerminator() {
        // 3 columns x 12 rows: col 0 empty, col 1 all black, col 2 top pixel black
        val w = 3
        val h = 12
        val pixels = BooleanArray(w * h)
        for (y in 0 until h) {
            pixels[y * w + 1] = true
        }
        pixels[0 * w + 2] = true

        val image = MonochromeImage(w, h, pixels)
        val packet = RasterProtocol.buildPrintJob(
            image = image,
            tapeWidth = TapeWidth.MM_12,
            options = PrintOptions(copies = 1, feedMarginDots = 14, chainPrint = false, useCompression = true)
        )

        assertEquals(3, packet.rasterLineCount)
        assertEquals(1, packet.emptyLineCount)
        assertEquals(TapeWidth.MM_12, packet.tapeWidth)

        val bytes = packet.bytes
        // Check ESC @ and ESC i a 0x01 right after 100 zero bytes
        assertArrayEquals(
            byteArrayOf(0x1B, 0x40, 0x1B, 0x69, 0x61, 0x01),
            bytes.copyOfRange(100, 106)
        )
        // Check ESC i z (media width = 12, raster lines = 3)
        assertEquals(0x1B.toByte(), bytes[106])
        assertEquals(0x69.toByte(), bytes[107])
        assertEquals(0x7A.toByte(), bytes[108])
        assertEquals(12.toByte(), bytes[111])
        assertEquals(3.toByte(), bytes[113])

        // Final byte must be 0x1A (print with feeding)
        assertEquals(0x1A.toByte(), bytes.last())
    }

    @Test
    fun multiCopyJobUsesFormFeedBetweenCopies() {
        val pixels = BooleanArray(4 * 10) { true }
        val image = MonochromeImage(4, 10, pixels)
        val packet = RasterProtocol.buildPrintJob(
            image = image,
            tapeWidth = TapeWidth.MM_9,
            options = PrintOptions(copies = 2, useCompression = true)
        )
        // Must contain 0x0C (copy 1 terminator) and end with 0x1A (final copy)
        assertTrue(packet.bytes.contains(0x0C.toByte()))
        assertEquals(0x1A.toByte(), packet.bytes.last())
    }

    @Test
    fun rasterLinesCenterImageOn128PinHeadAndSurvivePackBits() {
        val pixels = BooleanArray(1 * 70) { true }
        val image = MonochromeImage(1, 70, pixels)
        val lines = RasterProtocol.imageToRasterLines(image, mirrorVertical = true)
        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals(16, line.size)

        // Count total set bits: must equal 70
        val bitCount = line.sumOf { Integer.bitCount(it.toInt() and 0xFF) }
        assertEquals(70, bitCount)

        // Ensure PackBits round-trip matches exact line
        assertArrayEquals(line, PackBitsEncoder.decode(PackBitsEncoder.encode(line)))
    }
}
