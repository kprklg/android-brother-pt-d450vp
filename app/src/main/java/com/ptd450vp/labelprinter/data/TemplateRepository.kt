package com.ptd450vp.labelprinter.data

import android.content.Context
import com.ptd450vp.labelprinter.model.BarcodeFormatOption
import com.ptd450vp.labelprinter.model.FontChoice
import com.ptd450vp.labelprinter.model.LabelBlock
import com.ptd450vp.labelprinter.model.LabelDocument
import com.ptd450vp.labelprinter.model.LabelTemplate
import com.ptd450vp.labelprinter.model.QrErrorCorrection
import com.ptd450vp.labelprinter.model.TapeWidth
import com.ptd450vp.labelprinter.model.TextAlignOption
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class TemplateRepository(context: Context) {

    private val file = File(context.filesDir, "user_templates.json")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun getAllTemplates(): List<LabelTemplate> = builtInTemplates + loadUserTemplates()

    fun saveUserTemplate(name: String, description: String, document: LabelDocument): LabelTemplate {
        val current = loadUserTemplates().toMutableList()
        val template = LabelTemplate(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Мой шаблон" },
            description = description.trim().ifEmpty {
                "${document.tapeWidth.displayLabel} · блоков: ${document.blocks.size}"
            },
            builtIn = false,
            document = document
        )
        current.add(0, template)
        writeUserTemplates(current)
        return template
    }

    fun deleteUserTemplate(id: String) {
        val updated = loadUserTemplates().filterNot { it.id == id }
        writeUserTemplates(updated)
    }

    private fun loadUserTemplates(): List<LabelTemplate> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<LabelTemplate>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private fun writeUserTemplates(templates: List<LabelTemplate>) {
        runCatching {
            file.writeText(json.encodeToString(templates))
        }
    }

    companion object {
        val builtInTemplates: List<LabelTemplate> = listOf(
            LabelTemplate(
                id = "builtin_inventory_qr",
                name = "Инвентарный QR + текст",
                description = "18 мм · QR-код слева и 2 строки описания",
                builtIn = true,
                document = LabelDocument(
                    tapeWidth = TapeWidth.MM_18,
                    blocks = listOf(
                        LabelBlock.QrBlock(
                            id = "qr_1",
                            content = "INV-2026-0042",
                            errorCorrection = QrErrorCorrection.M
                        ),
                        LabelBlock.TextBlock(
                            id = "txt_1",
                            text = "ИНВ. № 0042\nНоутбук офисный",
                            font = FontChoice.SANS_SERIF,
                            bold = true,
                            autoFit = true
                        )
                    ),
                    blockSpacingPx = 16,
                    horizontalPaddingPx = 12,
                    showBorder = true
                )
            ),
            LabelTemplate(
                id = "builtin_cable_tag",
                name = "Кабельная бирка",
                description = "12 мм · Крупный моноширинный код кабеля",
                builtIn = true,
                document = LabelDocument(
                    tapeWidth = TapeWidth.MM_12,
                    blocks = listOf(
                        LabelBlock.TextBlock(
                            id = "cable_1",
                            text = "SW1-P24 → RACK-A\nVLAN 100 / 1GbE",
                            font = FontChoice.MONOSPACE,
                            bold = true,
                            align = TextAlignOption.CENTER,
                            autoFit = true
                        )
                    ),
                    horizontalPaddingPx = 14,
                    showBorder = false
                )
            ),
            LabelTemplate(
                id = "builtin_barcode_item",
                name = "Штрихкод Code 128 + подпись",
                description = "18 мм · Товар / складская ячейка",
                builtIn = true,
                document = LabelDocument(
                    tapeWidth = TapeWidth.MM_18,
                    blocks = listOf(
                        LabelBlock.BarcodeBlock(
                            id = "bc_1",
                            content = "SKU-450-108",
                            format = BarcodeFormatOption.CODE_128,
                            showHumanReadable = true,
                            moduleWidthPx = 2
                        ),
                        LabelBlock.TextBlock(
                            id = "bc_txt",
                            text = "Кассета TZe-231\n12 мм ч/б",
                            font = FontChoice.SANS_SERIF,
                            autoFit = true
                        )
                    ),
                    blockSpacingPx = 18,
                    horizontalPaddingPx = 12
                )
            ),
            LabelTemplate(
                id = "builtin_two_lines",
                name = "Две строки (полка / папка)",
                description = "12 мм · Заголовок и уточнение по центру",
                builtIn = true,
                document = LabelDocument(
                    tapeWidth = TapeWidth.MM_12,
                    blocks = listOf(
                        LabelBlock.TextBlock(
                            id = "two_1",
                            text = "АРХИВ 2026\nДоговоры и акты",
                            font = FontChoice.SANS_SERIF,
                            bold = true,
                            align = TextAlignOption.CENTER,
                            autoFit = true
                        )
                    ),
                    horizontalPaddingPx = 16,
                    showBorder = true
                )
            ),
            LabelTemplate(
                id = "builtin_wifi_qr",
                name = "Wi-Fi QR-код",
                description = "18 мм · Быстрое подключение к Wi-Fi по QR",
                builtIn = true,
                document = LabelDocument(
                    tapeWidth = TapeWidth.MM_18,
                    blocks = listOf(
                        LabelBlock.QrBlock(
                            id = "wifi_qr",
                            content = "WIFI:T:WPA;S:OfficeNet;P:Pass12345;;",
                            errorCorrection = QrErrorCorrection.M
                        ),
                        LabelBlock.TextBlock(
                            id = "wifi_txt",
                            text = "Wi-Fi: OfficeNet\nПароль: Pass12345",
                            font = FontChoice.SANS_SERIF,
                            autoFit = true
                        )
                    ),
                    blockSpacingPx = 16,
                    horizontalPaddingPx = 12
                )
            ),
            LabelTemplate(
                id = "builtin_narrow_6mm",
                name = "Узкая маркировка (6 мм)",
                description = "6 мм · Компактная строка для проводов и щитков",
                builtIn = true,
                document = LabelDocument(
                    tapeWidth = TapeWidth.MM_6,
                    blocks = listOf(
                        LabelBlock.TextBlock(
                            id = "narrow_1",
                            text = "220V · РОЗЕТКИ КУХНЯ",
                            font = FontChoice.CONDENSED,
                            bold = true,
                            autoFit = true
                        )
                    ),
                    horizontalPaddingPx = 10
                )
            )
        )
    }
}
