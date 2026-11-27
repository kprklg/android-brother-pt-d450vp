package com.ptd450vp.labelprinter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ptd450vp.labelprinter.model.PrintOptions
import com.ptd450vp.labelprinter.model.TapeWidth
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrinterDialog(
    state: LabelUiState,
    onDismiss: () -> Unit,
    onSelectMode: (TransportMode) -> Unit,
    onRefreshUsb: () -> Unit,
    onConnectUsb: () -> Unit,
    onRequestStatus: () -> Unit,
    onSetSimulatedTape: (TapeWidth) -> Unit,
    onSetSimulateCoverOpen: (Boolean) -> Unit,
    onUpdatePrintOptions: ((PrintOptions) -> PrintOptions) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Принтер PT-D450VP и USB OTG") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Режим подключения", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.transportMode == TransportMode.USB_OTG,
                        onClick = { onSelectMode(TransportMode.USB_OTG) },
                        label = { Text("USB OTG") }
                    )
                    FilterChip(
                        selected = state.transportMode == TransportMode.MOCK_SIMULATOR,
                        onClick = { onSelectMode(TransportMode.MOCK_SIMULATOR) },
                        label = { Text("Симулятор") }
                    )
                }

                if (state.transportMode == TransportMode.USB_OTG) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRefreshUsb, modifier = Modifier.weight(1f)) {
                            Text("Обновить")
                        }
                        Button(onClick = onConnectUsb, modifier = Modifier.weight(1f)) {
                            Text("Подключить")
                        }
                    }

                    Text(
                        text = "Найденные USB-устройства (${state.usbDevices.size}):",
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (state.usbDevices.isEmpty()) {
                        Text(
                            text = "Нет подключённых USB-устройств. Подключите PT-D450VP кабелем через USB OTG-переходник и включите питание принтера.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.usbDevices.forEach { dev ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "${dev.productName ?: "USB Device"} (${dev.vidPidHex})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Права: ${if (dev.hasPermission) "выданы" else "нет"} · " +
                                        if (dev.isExactPtD450) "PT-D450 (точное совпадение)" else "производитель: ${dev.manufacturerName ?: "—"}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (dev.interfaceSummary.isNotBlank()) {
                                    Text(
                                        text = dev.interfaceSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Виртуальная кассета симулятора:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TapeWidth.entries.forEach { tw ->
                            FilterChip(
                                selected = state.simulatedTapeWidth == tw,
                                onClick = { onSetSimulatedTape(tw) },
                                label = { Text(tw.displayLabel) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Симулировать «Открыта крышка»", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = state.simulateCoverOpen,
                            onCheckedChange = onSetSimulateCoverOpen
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRequestStatus,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Запросить статус принтера (ESC i S)")
                }

                state.lastStatus?.let { st ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Последний статус · ${st.modelName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Лента: ${st.tapeWidth?.displayLabel ?: "нет (${st.mediaWidthByte})"} · ${st.mediaTypeName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Цвета: лента ${st.tapeColorName}, текст ${st.textColorName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (st.hasError) {
                            Text(
                                text = "Ошибки: ${st.errorMessages.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = st.toHexString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                HorizontalDivider()
                Text("Параметры протокола и калибровка", style = MaterialTheme.typography.labelLarge)

                Text(
                    text = "Поле подачи ленты: ${state.printOptions.feedMarginDots} точек (~${"%.1f".format(state.printOptions.feedMarginDots / TapeWidth.DOTS_PER_MM)} мм)",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = state.printOptions.feedMarginDots.toFloat(),
                    onValueChange = { v ->
                        onUpdatePrintOptions { it.copy(feedMarginDots = v.roundToInt()) }
                    },
                    valueRange = 0f..140f
                )

                OptionSwitchRow(
                    label = "Chain Print (экономия ленты между копиями)",
                    checked = state.printOptions.chainPrint,
                    onCheckedChange = { checked ->
                        onUpdatePrintOptions { it.copy(chainPrint = checked) }
                    }
                )
                OptionSwitchRow(
                    label = "Сжатие PackBits TIFF (стандарт PT-D450)",
                    checked = state.printOptions.useCompression,
                    onCheckedChange = { checked ->
                        onUpdatePrintOptions { it.copy(useCompression = checked) }
                    }
                )
                OptionSwitchRow(
                    label = "Зеркалить по вертикали (калибровка: выключено)",
                    checked = state.printOptions.mirrorVertical,
                    onCheckedChange = { checked ->
                        onUpdatePrintOptions { it.copy(mirrorVertical = checked) }
                    }
                )

                state.lastPacket?.let { pkt ->
                    HorizontalDivider()
                    Text(
                        text = "Последний пакет печати: ${pkt.bytes.size} байт · ${pkt.rasterLineCount} строк (пустых: ${pkt.emptyLineCount})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pkt.headerHexPreview(128),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E222A), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        color = Color(0xFFB5E890)
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

@Composable
private fun OptionSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
