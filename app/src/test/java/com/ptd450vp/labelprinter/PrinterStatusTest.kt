package com.ptd450vp.labelprinter

import com.ptd450vp.labelprinter.model.TapeWidth
import com.ptd450vp.labelprinter.printer.PrinterStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterStatusTest {

    @Test
    fun parsesSyntheticStatusForAllTapeWidths() {
        TapeWidth.entries.forEach { width ->
            val st = PrinterStatus.synthetic(tapeWidth = width)
            assertEquals(width, st.tapeWidth)
            assertFalse(st.hasError)
            assertEquals("Белая", st.tapeColorName)
            assertEquals("Чёрный", st.textColorName)
        }
    }

    @Test
    fun parsesCoverOpenAndNoMediaErrors() {
        val st = PrinterStatus.synthetic(
            tapeWidth = TapeWidth.MM_12,
            error1 = 0x01, // No media
            error2 = 0x10  // Cover open
        )
        assertTrue(st.hasError)
        assertEquals(2, st.errorMessages.size)
        assertTrue(st.errorMessages.any { it.contains("Нет кассеты") })
        assertTrue(st.errorMessages.any { it.contains("Открыта крышка") })
    }

    @Test
    fun parsesRealHardwareCapturedPtD450Packet() {
        // Real 32-byte packet captured from Brother PT-D450VP (VID 04F9, PID 2073)
        val raw = byteArrayOf(
            0x80.toByte(), 0x20, 0x42, 0x30, 0x69, 0x30, 0x00, 0x00,
            0x00, 0x00, 0x0C, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val st = PrinterStatus.parse(raw)
        assertNotNull(st)
        st!!
        assertEquals(0x69, st.modelCode)
        assertEquals("PT-D450 / PT-D450VP", st.modelName)
        assertEquals(TapeWidth.MM_12, st.tapeWidth)
        assertEquals("Ламинированная TZe", st.mediaTypeName)
        assertEquals("Белая", st.tapeColorName)
        assertEquals("Чёрный", st.textColorName)
        assertFalse(st.hasError)
    }

    @Test
    fun rejectsShortOrInvalidHeaderPacket() {
        assertNull(PrinterStatus.parse(ByteArray(10)))
        val badHeader = ByteArray(32)
        badHeader[0] = 0x00
        assertNull(PrinterStatus.parse(badHeader))
        val good = PrinterStatus.synthetic().rawBytes
        assertNotNull(PrinterStatus.parse(good))
    }
}
