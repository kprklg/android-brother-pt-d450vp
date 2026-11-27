package com.ptd450vp.labelprinter.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ptd450vp.labelprinter.data.TemplateRepository
import com.ptd450vp.labelprinter.model.LabelBlock
import com.ptd450vp.labelprinter.model.LabelDocument
import com.ptd450vp.labelprinter.model.LabelTemplate
import com.ptd450vp.labelprinter.model.PrintOptions
import com.ptd450vp.labelprinter.model.TapeWidth
import com.ptd450vp.labelprinter.printer.MockPrinterTransport
import com.ptd450vp.labelprinter.printer.PrintJobPacket
import com.ptd450vp.labelprinter.printer.PrintResult
import com.ptd450vp.labelprinter.printer.PrinterStatus
import com.ptd450vp.labelprinter.printer.RasterProtocol
import com.ptd450vp.labelprinter.printer.UsbDeviceSummary
import com.ptd450vp.labelprinter.printer.UsbPrinterTransport
import com.ptd450vp.labelprinter.render.LabelRenderer
import com.ptd450vp.labelprinter.render.RenderedLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TransportMode {
    USB_OTG,
    MOCK_SIMULATOR
}

data class LabelUiState(
    val document: LabelDocument = LabelDocument(),
    val printOptions: PrintOptions = PrintOptions(),
    val rendered: RenderedLabel? = null,
    val templates: List<LabelTemplate> = emptyList(),
    val transportMode: TransportMode = TransportMode.MOCK_SIMULATOR,
    val usbDevices: List<UsbDeviceSummary> = emptyList(),
    val printerConnected: Boolean = true,
    val printerDisplayName: String = "Симулятор PT-D450VP (без USB)",
    val lastStatus: PrinterStatus? = null,
    val lastPacket: PrintJobPacket? = null,
    val simulatedTapeWidth: TapeWidth = TapeWidth.MM_12,
    val simulateCoverOpen: Boolean = false,
    val isBusy: Boolean = false,
    val showCutPrompt: Boolean = false,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
    val pendingImageBlockId: String? = null
)

class LabelViewModel(application: Application) : AndroidViewModel(application) {

    private val templateRepository = TemplateRepository(application)
    private val usbTransport = UsbPrinterTransport(application)
    private val mockTransport = MockPrinterTransport()

    private val _uiState = MutableStateFlow(LabelUiState())
    val uiState: StateFlow<LabelUiState> = _uiState.asStateFlow()

    init {
        val initialTemplates = templateRepository.getAllTemplates()
        _uiState.update { it.copy(templates = initialTemplates) }
        refreshUsbInventory(autoSwitchIfDetected = true)
        renderCurrentDocument()
    }

    fun updateDocument(transform: (LabelDocument) -> LabelDocument) {
        _uiState.update { it.copy(document = transform(it.document)) }
        renderCurrentDocument()
    }

    fun updatePrintOptions(transform: (PrintOptions) -> PrintOptions) {
        _uiState.update { it.copy(printOptions = transform(it.printOptions)) }
    }

    fun setTapeWidth(tapeWidth: TapeWidth) {
        updateDocument { it.copy(tapeWidth = tapeWidth) }
    }

    fun addBlock(block: LabelBlock) {
        updateDocument { doc -> doc.copy(blocks = doc.blocks + block) }
    }

    fun updateBlock(blockId: String, transform: (LabelBlock) -> LabelBlock) {
        updateDocument { doc ->
            doc.copy(blocks = doc.blocks.map { if (it.id == blockId) transform(it) else it })
        }
    }

    fun removeBlock(blockId: String) {
        updateDocument { doc ->
            doc.copy(blocks = doc.blocks.filterNot { it.id == blockId })
        }
    }

    fun moveBlock(blockId: String, delta: Int) {
        updateDocument { doc ->
            val list = doc.blocks.toMutableList()
            val idx = list.indexOfFirst { it.id == blockId }
            val target = idx + delta
            if (idx >= 0 && target in list.indices) {
                val item = list.removeAt(idx)
                list.add(target, item)
            }
            doc.copy(blocks = list)
        }
    }

    fun markPendingImageBlock(blockId: String) {
        _uiState.update { it.copy(pendingImageBlockId = blockId) }
    }

    fun onImagePicked(uri: Uri) {
        val targetBlockId = _uiState.value.pendingImageBlockId
        viewModelScope.launch {
            val encodedResult = withContext(Dispatchers.IO) {
                LabelRenderer.importImageUriToBase64(getApplication(), uri)
            }
            encodedResult.fold(
                onSuccess = { base64 ->
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Изображение"
                    if (targetBlockId != null) {
                        updateBlock(targetBlockId) { blk ->
                            if (blk is LabelBlock.ImageBlock) {
                                blk.copy(pngBase64 = base64, name = name)
                            } else blk
                        }
                    } else {
                        addBlock(LabelBlock.ImageBlock(name = name, pngBase64 = base64))
                    }
                    _uiState.update { it.copy(pendingImageBlockId = null) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            pendingImageBlockId = null,
                            feedbackMessage = err.message ?: "Ошибка загрузки изображения",
                            feedbackIsError = true
                        )
                    }
                }
            )
        }
    }

    fun applyTemplate(template: LabelTemplate) {
        updateDocument { template.document }
        showFeedback("Применён шаблон «${template.name}»", isError = false)
    }

    fun saveAsTemplate(name: String, description: String) {
        val doc = _uiState.value.document
        templateRepository.saveUserTemplate(name, description, doc)
        _uiState.update { it.copy(templates = templateRepository.getAllTemplates()) }
        showFeedback("Шаблон «$name» сохранён", isError = false)
    }

    fun deleteTemplate(id: String) {
        templateRepository.deleteUserTemplate(id)
        _uiState.update { it.copy(templates = templateRepository.getAllTemplates()) }
        showFeedback("Шаблон удалён", isError = false)
    }

    fun setTransportMode(mode: TransportMode) {
        _uiState.update { it.copy(transportMode = mode) }
        refreshTransportState()
    }

    fun setSimulatedTapeWidth(width: TapeWidth) {
        mockTransport.simulatedTapeWidth = width
        _uiState.update { it.copy(simulatedTapeWidth = width) }
    }

    fun setSimulateCoverOpen(open: Boolean) {
        mockTransport.simulateCoverOpen = open
        _uiState.update { it.copy(simulateCoverOpen = open) }
    }

    fun refreshUsbInventory(autoSwitchIfDetected: Boolean = false) {
        val devices = usbTransport.listAllUsbDevices()
        val hasBrother = devices.any { it.vendorId == RasterProtocol.BROTHER_VENDOR_ID }
        _uiState.update { state ->
            val nextMode = if (autoSwitchIfDetected && hasBrother) {
                TransportMode.USB_OTG
            } else {
                state.transportMode
            }
            state.copy(usbDevices = devices, transportMode = nextMode)
        }
        refreshTransportState()
    }

    fun connectUsbPrinter() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, transportMode = TransportMode.USB_OTG) }
            val result = usbTransport.requestUsbPermission()
            refreshUsbInventory(autoSwitchIfDetected = false)
            result.fold(
                onSuccess = { dev ->
                    showFeedback(
                        "USB подключён: ${dev.productName ?: "Brother"} (%04X:%04X)"
                            .format(dev.vendorId, dev.productId),
                        isError = false
                    )
                    requestPrinterStatus(syncTapeWidth = true)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isBusy = false) }
                    showFeedback(err.message ?: "Не удалось подключиться по USB", isError = true)
                }
            )
        }
    }

    fun requestPrinterStatus(syncTapeWidth: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val transport = currentTransport()
            val result = transport.requestStatus()
            result.fold(
                onSuccess = { status ->
                    _uiState.update { it.copy(lastStatus = status, isBusy = false) }
                    val detectedTape = status.tapeWidth
                    if (syncTapeWidth && detectedTape != null) {
                        setTapeWidth(detectedTape)
                    }
                    val summary = buildString {
                        append("Статус получен: ")
                        append(detectedTape?.displayLabel ?: "лента не определена")
                        append(" · ")
                        append(status.tapeColorName)
                        if (status.hasError) {
                            append(" · ОШИБКА: ")
                            append(status.errorMessages.joinToString(", "))
                        }
                    }
                    showFeedback(summary, isError = status.hasError)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isBusy = false) }
                    showFeedback(err.message ?: "Ошибка чтения статуса", isError = true)
                }
            )
            refreshTransportState()
        }
    }

    fun printCurrentLabel() {
        val rendered = _uiState.value.rendered
        if (rendered == null) {
            showFeedback("Нет данных для печати", isError = true)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val state = _uiState.value
            val packet = withContext(Dispatchers.Default) {
                RasterProtocol.buildPrintJob(
                    image = rendered.monochrome,
                    tapeWidth = state.document.tapeWidth,
                    options = state.printOptions,
                    mediaTypeByte = state.lastStatus?.mediaTypeByte?.takeIf { it != 0 } ?: 0x01
                )
            }
            val result = currentTransport().sendPrintJob(packet)
            when (result) {
                is PrintResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            lastPacket = result.packet,
                            lastStatus = result.postStatus ?: it.lastStatus,
                            showCutPrompt = true
                        )
                    }
                    showFeedback(result.message, isError = false)
                }
                is PrintResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            lastPacket = result.packet ?: it.lastPacket
                        )
                    }
                    showFeedback(result.error, isError = true)
                }
            }
            refreshTransportState()
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun dismissCutPrompt() {
        _uiState.update { it.copy(showCutPrompt = false) }
    }

    private fun currentTransport() = when (_uiState.value.transportMode) {
        TransportMode.USB_OTG -> usbTransport
        TransportMode.MOCK_SIMULATOR -> mockTransport
    }

    private fun refreshTransportState() {
        val transport = currentTransport()
        _uiState.update {
            it.copy(
                printerConnected = transport.isConnected,
                printerDisplayName = transport.displayName
            )
        }
    }

    private fun renderCurrentDocument() {
        val doc = _uiState.value.document
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                LabelRenderer.render(doc)
            }
            _uiState.update { it.copy(rendered = result) }
        }
    }

    private fun showFeedback(message: String, isError: Boolean) {
        _uiState.update {
            it.copy(feedbackMessage = message, feedbackIsError = isError)
        }
    }
}
