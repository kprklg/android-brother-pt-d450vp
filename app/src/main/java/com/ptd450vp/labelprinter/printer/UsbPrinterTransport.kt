package com.ptd450vp.labelprinter.printer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Android USB Host (USB OTG) transport for Brother PT-D450 / PT-D450VP.
 *
 * Recognizes Brother PT-D450 (VID 0x04F9, PID 0x2073) first, and also discovers any other
 * Brother USB Printer (VID 0x04F9) so hardware variants can be diagnosed immediately.
 */
class UsbPrinterTransport(
    private val context: Context
) : PrinterTransport {

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var activeDevice: UsbDevice? = null

    override val displayName: String
        get() {
            val dev = activeDevice ?: findBrotherDevice()
            return if (dev != null) {
                val label = dev.productName ?: "Brother USB"
                "$label (%04X:%04X)".format(dev.vendorId, dev.productId)
            } else {
                "USB OTG (принтер не найден)"
            }
        }

    override val isConnected: Boolean
        get() {
            val dev = findBrotherDevice()
            activeDevice = dev
            return dev != null && usbManager.hasPermission(dev)
        }

    fun listAllUsbDevices(): List<UsbDeviceSummary> {
        return usbManager.deviceList.values.map { device ->
            val interfaces = (0 until device.interfaceCount).joinToString("; ") { idx ->
                val intf = device.getInterface(idx)
                val eps = (0 until intf.endpointCount).joinToString(",") { epIdx ->
                    val ep = intf.getEndpoint(epIdx)
                    val dir = if (ep.direction == UsbConstants.USB_DIR_OUT) "OUT" else "IN"
                    val type = when (ep.type) {
                        UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                        UsbConstants.USB_ENDPOINT_XFER_INT -> "INT"
                        else -> "T${ep.type}"
                    }
                    "$type-$dir(0x${ep.address.toString(16)})"
                }
                "IF#$idx(cls=${intf.interfaceClass})[$eps]"
            }
            UsbDeviceSummary(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                productName = device.productName,
                manufacturerName = device.manufacturerName,
                hasPermission = usbManager.hasPermission(device),
                isExactPtD450 = device.vendorId == RasterProtocol.BROTHER_VENDOR_ID &&
                    device.productId == RasterProtocol.PTD450_PRODUCT_ID,
                interfaceSummary = interfaces
            )
        }.sortedByDescending { it.isExactPtD450 }
    }

    fun findBrotherDevice(): UsbDevice? {
        val devices = usbManager.deviceList.values
        return devices.firstOrNull {
            it.vendorId == RasterProtocol.BROTHER_VENDOR_ID &&
                it.productId == RasterProtocol.PTD450_PRODUCT_ID
        } ?: devices.firstOrNull {
            it.vendorId == RasterProtocol.BROTHER_VENDOR_ID
        }
    }

    suspend fun requestUsbPermission(): Result<UsbDevice> {
        val device = findBrotherDevice()
            ?: return Result.failure(IllegalStateException("Принтер Brother не обнаружен по USB OTG"))
        activeDevice = device

        if (usbManager.hasPermission(device)) {
            return Result.success(device)
        }

        val granted = suspendCancellableCoroutine<Boolean> { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == ACTION_USB_PERMISSION) {
                        try {
                            context.unregisterReceiver(this)
                        } catch (_: Exception) {
                        }
                        val ok = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (cont.isActive) cont.resume(ok)
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_USB_PERMISSION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            cont.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
        }

        return if (granted && usbManager.hasPermission(device)) {
            Result.success(device)
        } else {
            Result.failure(SecurityException("Доступ к USB-принтеру отклонён пользователем"))
        }
    }

    override suspend fun requestStatus(): Result<PrinterStatus> = withContext(Dispatchers.IO) {
        withOpenedPrinter { session ->
            drainInputBuffer(session.connection, session.bulkIn)
            val cmd = RasterProtocol.buildStatusRequest()
            writeAll(session.connection, session.bulkOut, cmd)
            val status = readStatusPacket(session.connection, session.bulkIn)
                ?: throw IllegalStateException("Принтер не вернул 32-байтный пакет статуса (таймаут)")
            status
        }
    }

    override suspend fun sendPrintJob(packet: PrintJobPacket): PrintResult = withContext(Dispatchers.IO) {
        val outcome = withOpenedPrinter { session ->
            writeAll(session.connection, session.bulkOut, packet.bytes)
            // Optional: read completion status if printer responds promptly
            val postStatus = runCatching {
                readStatusPacket(session.connection, session.bulkIn, attempts = 3, timeoutMs = 400)
            }.getOrNull()
            postStatus
        }

        outcome.fold(
            onSuccess = { postStatus ->
                if (postStatus != null && postStatus.hasError) {
                    PrintResult.Failure(
                        error = "Ошибка принтера: ${postStatus.errorMessages.joinToString(", ")}",
                        packet = packet
                    )
                } else {
                    PrintResult.Success(
                        message = "Отправлено на принтер (${packet.bytes.size} байт, " +
                            "${packet.rasterLineCount} строк, копий: ${packet.copies})",
                        packet = packet,
                        postStatus = postStatus
                    )
                }
            },
            onFailure = { err ->
                PrintResult.Failure(
                    error = err.message ?: "Ошибка обмена по USB OTG",
                    packet = packet
                )
            }
        )
    }

    private data class PrinterSession(
        val connection: UsbDeviceConnection,
        val usbInterface: UsbInterface,
        val bulkOut: UsbEndpoint,
        val bulkIn: UsbEndpoint?
    )

    private fun <T> withOpenedPrinter(block: (PrinterSession) -> T): Result<T> {
        val device = findBrotherDevice()
            ?: return Result.failure(IllegalStateException("Принтер Brother не подключён по USB OTG"))
        activeDevice = device

        if (!usbManager.hasPermission(device)) {
            return Result.failure(SecurityException("Нет разрешения на USB-устройство — нажмите «Подключить USB»"))
        }

        val endpoints = findPrinterEndpoints(device)
            ?: return Result.failure(
                IllegalStateException("Не найден USB Bulk OUT интерфейс принтера (проверьте диагностику USB)")
            )

        val connection = usbManager.openDevice(device)
            ?: return Result.failure(IllegalStateException("Не удалось открыть USB-соединение с принтером"))

        return try {
            if (!connection.claimInterface(endpoints.first, true)) {
                return Result.failure(IllegalStateException("Не удалось захватить USB-интерфейс принтера"))
            }
            try {
                val result = block(
                    PrinterSession(
                        connection = connection,
                        usbInterface = endpoints.first,
                        bulkOut = endpoints.second,
                        bulkIn = endpoints.third
                    )
                )
                Result.success(result)
            } finally {
                connection.releaseInterface(endpoints.first)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection.close()
        }
    }

    private fun findPrinterEndpoints(device: UsbDevice): Triple<UsbInterface, UsbEndpoint, UsbEndpoint?>? {
        var fallback: Triple<UsbInterface, UsbEndpoint, UsbEndpoint?>? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            var bulkOut: UsbEndpoint? = null
            var bulkIn: UsbEndpoint? = null
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_OUT && bulkOut == null) bulkOut = ep
                    if (ep.direction == UsbConstants.USB_DIR_IN && bulkIn == null) bulkIn = ep
                }
            }
            if (bulkOut != null) {
                val match = Triple(intf, bulkOut, bulkIn)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                    return match
                }
                if (fallback == null) fallback = match
            }
        }
        return fallback
    }

    private fun writeAll(connection: UsbDeviceConnection, endpoint: UsbEndpoint, data: ByteArray) {
        val chunkSize = 4096
        var offset = 0
        while (offset < data.size) {
            val len = (data.size - offset).coerceAtMost(chunkSize)
            val chunk = if (offset == 0 && len == data.size) {
                data
            } else {
                data.copyOfRange(offset, offset + len)
            }
            val written = connection.bulkTransfer(endpoint, chunk, chunk.size, WRITE_TIMEOUT_MS)
            if (written < 0) {
                throw IllegalStateException("Ошибка записи USB Bulk OUT (offset=$offset/${data.size})")
            }
            if (written == 0) {
                throw IllegalStateException("USB Bulk OUT вернул 0 байт (offset=$offset)")
            }
            offset += written
        }
    }

    private fun drainInputBuffer(connection: UsbDeviceConnection, bulkIn: UsbEndpoint?) {
        if (bulkIn == null) return
        val scratch = ByteArray(64)
        repeat(4) {
            val read = connection.bulkTransfer(bulkIn, scratch, scratch.size, 40)
            if (read <= 0) return
        }
    }

    private fun readStatusPacket(
        connection: UsbDeviceConnection,
        bulkIn: UsbEndpoint?,
        attempts: Int = 6,
        timeoutMs: Int = 500
    ): PrinterStatus? {
        if (bulkIn == null) return null
        val buffer = ByteArray(64)
        repeat(attempts) {
            val read = connection.bulkTransfer(bulkIn, buffer, buffer.size, timeoutMs)
            if (read >= PrinterStatus.PACKET_SIZE) {
                val parsed = PrinterStatus.parse(buffer)
                if (parsed != null) return parsed
            }
            Thread.sleep(60)
        }
        return null
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.ptd450vp.labelprinter.USB_PERMISSION"
        private const val WRITE_TIMEOUT_MS = 5000
    }
}
