package com.ptd450vp.labelprinter

import com.ptd450vp.labelprinter.model.BarcodeFormatOption
import com.ptd450vp.labelprinter.model.QrErrorCorrection
import com.ptd450vp.labelprinter.render.BarcodeGenerator
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeGeneratorTest {

    @Test
    fun generatesUtf8QrCode() {
        val res = BarcodeGenerator.encodeQrMatrix(
            content = "Принтер Brother PT-D450VP",
            targetSizePx = 70,
            errorCorrection = QrErrorCorrection.M
        )
        assertTrue(res.isSuccess)
        val matrix = res.getOrThrow()
        assertTrue(matrix.width >= 70 && matrix.height >= 70)
    }

    @Test
    fun generatesCode128AndValidatesEan13() {
        val code128 = BarcodeGenerator.encodeBarcodeMatrix(
            content = "INV-2026-001",
            format = BarcodeFormatOption.CODE_128,
            heightPx = 50,
            moduleWidthPx = 2
        )
        assertTrue(code128.isSuccess)
        assertTrue(code128.getOrThrow().width > 40)

        val validEan = BarcodeGenerator.encodeBarcodeMatrix(
            content = "460000000001",
            format = BarcodeFormatOption.EAN_13,
            heightPx = 50,
            moduleWidthPx = 2
        )
        assertTrue(validEan.isSuccess)

        val invalidEan = BarcodeGenerator.encodeBarcodeMatrix(
            content = "12345",
            format = BarcodeFormatOption.EAN_13,
            heightPx = 50,
            moduleWidthPx = 2
        )
        assertTrue(invalidEan.isFailure)
    }

    @Test
    fun generatesDataMatrix() {
        val dm = BarcodeGenerator.encodeBarcodeMatrix(
            content = "SN:PTD450VP-9981",
            format = BarcodeFormatOption.DATA_MATRIX,
            heightPx = 64,
            moduleWidthPx = 2
        )
        assertTrue(dm.isSuccess)
    }
}
