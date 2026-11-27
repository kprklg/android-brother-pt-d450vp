package com.ptd450vp.labelprinter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ptd450vp.labelprinter.model.BarcodeFormatOption
import com.ptd450vp.labelprinter.model.DitherMode
import com.ptd450vp.labelprinter.model.FontChoice
import com.ptd450vp.labelprinter.model.LabelBlock
import com.ptd450vp.labelprinter.model.QrErrorCorrection
import com.ptd450vp.labelprinter.model.TapeWidth
import com.ptd450vp.labelprinter.model.TextAlignOption
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    viewModel: LabelViewModel,
    onPickImageForBlock: (String?) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showPrinterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.feedbackMessage) {
        val msg = state.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Brother PT-D450VP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.printerDisplayName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { showTemplatesDialog = true }) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "Шаблоны")
                    }
                    IconButton(onClick = {
                        viewModel.refreshUsbInventory()
                        showPrinterDialog = true
                    }) {
                        Icon(Icons.Default.Usb, contentDescription = "Принтер и USB")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (state.feedbackIsError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.inverseSurface
                    },
                    contentColor = if (state.feedbackIsError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.inverseOnSurface
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Live WYSIWYG preview + tape selector + print CTA
            item(key = "preview_card") {
                PreviewAndPrintCard(
                    state = state,
                    onSelectTape = viewModel::setTapeWidth,
                    onToggleBorder = {
                        viewModel.updateDocument { it.copy(showBorder = !it.showBorder) }
                    },
                    onToggleInvert = {
                        viewModel.updateDocument { it.copy(invertColors = !it.invertColors) }
                    },
                    onChangeCopies = { delta ->
                        viewModel.updatePrintOptions {
                            it.copy(copies = (it.copies + delta).coerceIn(1, 99))
                        }
                    },
                    onOpenPrinter = {
                        viewModel.refreshUsbInventory()
                        showPrinterDialog = true
                    },
                    onPrint = viewModel::printCurrentLabel
                )
            }

            // 2. Add block bar
            item(key = "add_block_bar") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Добавить элемент на этикетку",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = { viewModel.addBlock(LabelBlock.TextBlock(text = "Новый текст")) },
                                label = { Text("Текст") },
                                leadingIcon = {
                                    Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                            AssistChip(
                                onClick = { viewModel.addBlock(LabelBlock.QrBlock()) },
                                label = { Text("QR-код") },
                                leadingIcon = {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                            AssistChip(
                                onClick = { viewModel.addBlock(LabelBlock.BarcodeBlock()) },
                                label = { Text("Штрихкод") },
                                leadingIcon = {
                                    Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                            AssistChip(
                                onClick = { onPickImageForBlock(null) },
                                label = { Text("Картинка") },
                                leadingIcon = {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }
            }

            // 3. Block editors
            if (state.document.blocks.isEmpty()) {
                item(key = "empty_blocks") {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Этикетка пуста. Добавьте текст, QR-код, штрихкод или картинку выше, либо выберите готовый шаблон.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = state.document.blocks,
                    key = { _, blk -> blk.id }
                ) { index, block ->
                    BlockEditorCard(
                        block = block,
                        index = index,
                        totalCount = state.document.blocks.size,
                        onMoveUp = { viewModel.moveBlock(block.id, -1) },
                        onMoveDown = { viewModel.moveBlock(block.id, +1) },
                        onDelete = { viewModel.removeBlock(block.id) },
                        onUpdate = { updated -> viewModel.updateBlock(block.id) { updated } },
                        onPickImage = { onPickImageForBlock(block.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showTemplatesDialog) {
        TemplatesDialog(
            templates = state.templates,
            onDismiss = { showTemplatesDialog = false },
            onApply = { tpl ->
                viewModel.applyTemplate(tpl)
                showTemplatesDialog = false
            },
            onSaveCurrent = { name, desc ->
                viewModel.saveAsTemplate(name, desc)
            },
            onDelete = viewModel::deleteTemplate
        )
    }

    if (showPrinterDialog) {
        PrinterDialog(
            state = state,
            onDismiss = { showPrinterDialog = false },
            onSelectMode = viewModel::setTransportMode,
            onRefreshUsb = { viewModel.refreshUsbInventory() },
            onConnectUsb = viewModel::connectUsbPrinter,
            onRequestStatus = { viewModel.requestPrinterStatus(syncTapeWidth = true) },
            onSetSimulatedTape = viewModel::setSimulatedTapeWidth,
            onSetSimulateCoverOpen = viewModel::setSimulateCoverOpen,
            onUpdatePrintOptions = viewModel::updatePrintOptions
        )
    }

    if (state.showCutPrompt) {
        CutPromptDialog(
            copies = state.printOptions.copies,
            chainPrint = state.printOptions.chainPrint,
            onDismiss = viewModel::dismissCutPrompt
        )
    }
}

@Composable
private fun CutPromptDialog(
    copies: Int,
    chainPrint: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Печать завершена") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (copies > 1) {
                        "Лента с $copies этикетками выведена из принтера."
                    } else {
                        "Этикетка выведена из принтера."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Нажмите на нож, чтобы отрезать ленту.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (copies > 1 && chainPrint) {
                    Text(
                        "Chain Print: этикетки напечатаны встык одной лентой — разрежьте её между этикетками.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewAndPrintCard(
    state: LabelUiState,
    onSelectTape: (TapeWidth) -> Unit,
    onToggleBorder: () -> Unit,
    onToggleInvert: () -> Unit,
    onChangeCopies: (Int) -> Unit,
    onOpenPrinter: () -> Unit,
    onPrint: () -> Unit
) {
    val rendered = state.rendered
    val lengthMm = rendered?.let { it.monochrome.width / TapeWidth.DOTS_PER_MM } ?: 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tape width selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Лента TZe (180 dpi)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (rendered != null) {
                    Text(
                        text = "~${"%.1f".format(lengthMm)} мм · ${rendered.monochrome.width}×${rendered.monochrome.height} px",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TapeWidth.entries.forEach { width ->
                    FilterChip(
                        selected = state.document.tapeWidth == width,
                        onClick = { onSelectTape(width) },
                        label = { Text(width.displayLabel) }
                    )
                }
            }

            // Crisp 1-bit preview strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0E5EC), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (rendered != null) {
                    val img = remember(rendered.previewBitmap) {
                        rendered.previewBitmap.asImageBitmap()
                    }
                    val scale = 2.2f
                    val displayW = (rendered.previewBitmap.width * scale / 2.2f * 1.15f).dp
                        .coerceAtLeast(60.dp)
                    val displayH = (rendered.previewBitmap.height * 1.15f).dp
                        .coerceIn(28.dp, 130.dp)

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(3.dp))
                                .border(1.dp, Color(0xFF90A4AE), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(displayW)
                                    .height(displayH)
                            ) {
                                drawImage(
                                    image = img,
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                    filterQuality = FilterQuality.None
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Рендеринг предпросмотра...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Warnings from barcode/image blocks if any
            rendered?.warnings?.forEach { warn ->
                Text(
                    text = "⚠ $warn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Border, invert, copies row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = state.document.showBorder,
                    onClick = onToggleBorder,
                    label = { Text("Рамка") }
                )
                FilterChip(
                    selected = state.document.invertColors,
                    onClick = onToggleInvert,
                    label = { Text("Инверсия") }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = { onChangeCopies(-1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Меньше копий", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "Копий: ${state.printOptions.copies}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { onChangeCopies(+1) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Больше копий", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Print + Mode buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenPrinter,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.transportMode == TransportMode.USB_OTG) "USB OTG" else "Симулятор",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onPrint,
                    enabled = !state.isBusy && rendered != null,
                    modifier = Modifier.weight(1.4f)
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.transportMode == TransportMode.USB_OTG) {
                            "Печать"
                        } else {
                            "Тест печати"
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockEditorCard(
    block: LabelBlock,
    index: Int,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (LabelBlock) -> Unit,
    onPickImage: () -> Unit
) {
    val title = when (block) {
        is LabelBlock.TextBlock -> "Текст #${index + 1}"
        is LabelBlock.QrBlock -> "QR-код #${index + 1}"
        is LabelBlock.BarcodeBlock -> "Штрихкод #${index + 1}"
        is LabelBlock.ImageBlock -> "Картинка #${index + 1}"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Влево/выше", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Вправо/ниже", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить блок",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            when (block) {
                is LabelBlock.TextBlock -> TextBlockEditor(block, onUpdate)
                is LabelBlock.QrBlock -> QrBlockEditor(block, onUpdate)
                is LabelBlock.BarcodeBlock -> BarcodeBlockEditor(block, onUpdate)
                is LabelBlock.ImageBlock -> ImageBlockEditor(block, onUpdate, onPickImage)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextBlockEditor(
    block: LabelBlock.TextBlock,
    onUpdate: (LabelBlock) -> Unit
) {
    OutlinedTextField(
        value = block.text,
        onValueChange = { onUpdate(block.copy(text = it)) },
        label = { Text("Текст (можно несколько строк через Enter)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 5
    )

    Text("Шрифт", style = MaterialTheme.typography.labelMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FontChoice.entries.forEach { font ->
            FilterChip(
                selected = block.font == font,
                onClick = { onUpdate(block.copy(font = font)) },
                label = { Text(font.displayName) }
            )
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FilterChip(
            selected = block.bold,
            onClick = { onUpdate(block.copy(bold = !block.bold)) },
            label = { Text("Жирный") },
            leadingIcon = { Icon(Icons.Default.FormatBold, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        FilterChip(
            selected = block.italic,
            onClick = { onUpdate(block.copy(italic = !block.italic)) },
            label = { Text("Курсив") },
            leadingIcon = { Icon(Icons.Default.FormatItalic, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )
        TextAlignOption.entries.forEach { align ->
            val icon = when (align) {
                TextAlignOption.LEFT -> Icons.AutoMirrored.Filled.FormatAlignLeft
                TextAlignOption.CENTER -> Icons.Default.FormatAlignCenter
                TextAlignOption.RIGHT -> Icons.AutoMirrored.Filled.FormatAlignRight
            }
            FilterChip(
                selected = block.align == align,
                onClick = { onUpdate(block.copy(align = align)) },
                label = { Text(align.displayName) },
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Автоподбор размера под ленту", style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = block.autoFit,
            onCheckedChange = { onUpdate(block.copy(autoFit = it)) }
        )
    }

    if (!block.autoFit) {
        Text("Размер шрифта: ${block.fontSizePx} px", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = block.fontSizePx.toFloat(),
            onValueChange = { onUpdate(block.copy(fontSizePx = it.roundToInt())) },
            valueRange = 12f..110f
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QrBlockEditor(
    block: LabelBlock.QrBlock,
    onUpdate: (LabelBlock) -> Unit
) {
    OutlinedTextField(
        value = block.content,
        onValueChange = { onUpdate(block.copy(content = it)) },
        label = { Text("Данные QR-кода (ссылка, текст, Wi-Fi, серийник)") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3
    )

    Text("Уровень коррекции ошибок", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        QrErrorCorrection.entries.forEach { ec ->
            FilterChip(
                selected = block.errorCorrection == ec,
                onClick = { onUpdate(block.copy(errorCorrection = ec)) },
                label = { Text(ec.displayName) }
            )
        }
    }

    Text("Размер относительно высоты ленты: ${block.sizePercent}%", style = MaterialTheme.typography.labelMedium)
    Slider(
        value = block.sizePercent.toFloat(),
        onValueChange = { onUpdate(block.copy(sizePercent = it.roundToInt())) },
        valueRange = 50f..100f
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarcodeBlockEditor(
    block: LabelBlock.BarcodeBlock,
    onUpdate: (LabelBlock) -> Unit
) {
    OutlinedTextField(
        value = block.content,
        onValueChange = { onUpdate(block.copy(content = it)) },
        label = { Text("Код (${block.format.hint})") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Text("Формат штрихкода", style = MaterialTheme.typography.labelMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BarcodeFormatOption.entries.forEach { fmt ->
            FilterChip(
                selected = block.format == fmt,
                onClick = { onUpdate(block.copy(format = fmt)) },
                label = { Text(fmt.displayName) }
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Подпись текста под штрихкодом", style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = block.showHumanReadable,
            onCheckedChange = { onUpdate(block.copy(showHumanReadable = it)) }
        )
    }

    if (block.format != BarcodeFormatOption.DATA_MATRIX) {
        Text("Толщина штриха: ${block.moduleWidthPx} px", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = block.moduleWidthPx.toFloat(),
            onValueChange = { onUpdate(block.copy(moduleWidthPx = it.roundToInt())) },
            valueRange = 1f..4f,
            steps = 2
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageBlockEditor(
    block: LabelBlock.ImageBlock,
    onUpdate: (LabelBlock) -> Unit,
    onPickImage: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (block.pngBase64.isBlank()) "Файл не выбран" else block.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onPickImage) {
            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (block.pngBase64.isBlank()) "Выбрать" else "Заменить")
        }
    }

    Text("Режим преобразования в ч/б", style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DitherMode.entries.forEach { mode ->
            FilterChip(
                selected = block.ditherMode == mode,
                onClick = { onUpdate(block.copy(ditherMode = mode)) },
                label = { Text(mode.displayName) }
            )
        }
        FilterChip(
            selected = block.invert,
            onClick = { onUpdate(block.copy(invert = !block.invert)) },
            label = { Text("Инвертировать") }
        )
    }

    Text("Порог яркости: ${block.threshold}", style = MaterialTheme.typography.labelMedium)
    Slider(
        value = block.threshold.toFloat(),
        onValueChange = { onUpdate(block.copy(threshold = it.roundToInt())) },
        valueRange = 40f..220f
    )

    Text("Масштаб по высоте: ${block.scalePercent}%", style = MaterialTheme.typography.labelMedium)
    Slider(
        value = block.scalePercent.toFloat(),
        onValueChange = { onUpdate(block.copy(scalePercent = it.roundToInt())) },
        valueRange = 40f..100f
    )
}
