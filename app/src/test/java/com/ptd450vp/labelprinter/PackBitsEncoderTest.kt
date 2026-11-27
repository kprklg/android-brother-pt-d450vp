package com.ptd450vp.labelprinter

import com.ptd450vp.labelprinter.printer.PackBitsEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackBitsEncoderTest {

    @Test
    fun roundTripAllZeroRasterLine() {
        val line = ByteArray(16) { 0x00 }
        val encoded = PackBitsEncoder.encode(line)
        // 16 identical zeros should compress to just 2 bytes: [257 - 16 = 241 (0xF1), 0x00]
        assertArrayEquals(byteArrayOf(0xF1.toByte(), 0x00), encoded)
        assertArrayEquals(line, PackBitsEncoder.decode(encoded))
    }

    @Test
    fun roundTripAllBlackRasterLine() {
        val line = ByteArray(16) { 0xFF.toByte() }
        val encoded = PackBitsEncoder.encode(line)
        assertArrayEquals(byteArrayOf(0xF1.toByte(), 0xFF.toByte()), encoded)
        assertArrayEquals(line, PackBitsEncoder.decode(encoded))
    }

    @Test
    fun roundTripAlternatingLiteralLine() {
        val line = ByteArray(16) { idx -> if (idx % 2 == 0) 0xAA.toByte() else 0x55.toByte() }
        val encoded = PackBitsEncoder.encode(line)
        assertArrayEquals(line, PackBitsEncoder.decode(encoded))
    }

    @Test
    fun roundTripMixedRunsAndLiterals() {
        val line = byteArrayOf(
            0x00, 0x00, 0x00, 0x00, // repeat 4
            0x12, 0x34, 0x56,       // literal 3
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // repeat 3
            0x78,                   // literal 1
            0x00, 0x00, 0x00, 0x00, 0x00 // repeat 5
        )
        val encoded = PackBitsEncoder.encode(line)
        assertTrue("Mixed line should compress cleanly", encoded.size < line.size)
        assertArrayEquals(line, PackBitsEncoder.decode(encoded))
    }

    @Test
    fun roundTripEmptyInput() {
        val encoded = PackBitsEncoder.encode(ByteArray(0))
        assertArrayEquals(ByteArray(0), encoded)
        assertArrayEquals(ByteArray(0), PackBitsEncoder.decode(encoded))
    }
}
