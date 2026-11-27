package com.ptd450vp.labelprinter.printer

import com.ptd450vp.labelprinter.model.TapeWidth
import kotlinx.coroutines.delay

data class UsbDeviceSummary(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val productName: String?,
    val manufacturerName: String?,
    val hasPermission: Boolean,
    val isExactPtD450: Boolean,
    val interfaceSummary: String
) {
    val vidPidHex: String
        get() = "%04X:%04X".format(vendorId, productId)
}

sealed class PrintResult {
    data class Success(
        val message: String,
        val packet: PrintJobPacket,
        val postStatus: PrinterStatus? = null
    ) : PrintResult()

    data class Failure(
        val error: String,
        val packet: PrintJobPacket? = null
    ) : PrintResult()
}

interface PrinterTransport {
    val displayName: String
    val isConnected: Boolean

    suspend fun requestStatus(): Result<PrinterStatus>
    suspend fun sendPrintJob(packet: PrintJobPacket): PrintResult
}

/**
 * Virtual Brother PT-D450VP simulator for developing and testing the full app pipeline
 * (status reading, tape auto-detection, raster generation, hex inspection) without physical hardware.
 */
class MockPrinterTransport(
    var simulatedTapeWidth: TapeWidth = TapeWidth.MM_12,
    var simulateCoverOpen: Boolean = false
) : PrinterTransport {

    var lastPacket: PrintJobPacket? = null
        private set

    override val displayName: String
        get() = "Симулятор PT-D450VP (без USB)"

    override val isConnected: Boolean
        get() = true

    override suspend fun requestStatus(): Result<PrinterStatus> {
        delay(120)
        val status = PrinterStatus.synthetic(
            tapeWidth = simulatedTapeWidth,
            error2 = if (simulateCoverOpen) 0x10 else 0x00
        )
        return Result.success(status)
    }

    override suspend fun sendPrintJob(packet: PrintJobPacket): PrintResult {
        delay(220)
        lastPacket = packet
        if (simulateCoverOpen) {
            return PrintResult.Failure(
                error = "Симуляция ошибки: открыта крышка кассеты",
                packet = packet
            )
        }
        val status = PrinterStatus.synthetic(
            tapeWidth = simulatedTapeWidth,
            statusTypeByte = 0x01 // Printing completed
        )
        return PrintResult.Success(
            message = "Симуляция печати успешна: ${packet.bytes.size} байт, " +
                "${packet.rasterLineCount} строк (~${"%.1f".format(packet.labelLengthMm)} мм), " +
                "копий: ${packet.copies}",
            packet = packet,
            postStatus = status
        )
    }
}
