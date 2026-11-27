package com.ptd450vp.labelprinter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class FontChoice(val displayName: String) {
    SANS_SERIF("Sans-Serif"),
    SERIF("Serif"),
    MONOSPACE("Monospace"),
    CONDENSED("Узкий (Condensed)")
}

@Serializable
enum class TextAlignOption(val displayName: String) {
    LEFT("Слева"),
    CENTER("По центру"),
    RIGHT("Справа")
}

@Serializable
enum class BarcodeFormatOption(val displayName: String, val hint: String) {
    CODE_128("Code 128", "Любой ASCII-текст или цифры"),
    EAN_13("EAN-13", "12 или 13 цифр"),
    CODE_39("Code 39", "Заглавные A-Z, 0-9, -. $/+%"),
    DATA_MATRIX("Data Matrix", "Компактный 2D-код")
}

@Serializable
enum class QrErrorCorrection(val displayName: String) {
    L("L (7%)"),
    M("M (15%)"),
    Q("Q (25%)"),
    H("H (30%)")
}

@Serializable
enum class DitherMode(val displayName: String) {
    THRESHOLD("Порог (чёткий)"),
    FLOYD_STEINBERG("Дизеринг (фото)")
}

@Serializable
sealed class LabelBlock {
    abstract val id: String

    @Serializable
    @SerialName("text")
    data class TextBlock(
        override val id: String = UUID.randomUUID().toString(),
        val text: String = "Brother PT-D450VP",
        val font: FontChoice = FontChoice.SANS_SERIF,
        val fontSizePx: Int = 36,
        val autoFit: Boolean = true,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val align: TextAlignOption = TextAlignOption.LEFT,
        val lineSpacingMultiplier: Float = 1.05f
    ) : LabelBlock()

    @Serializable
    @SerialName("qr")
    data class QrBlock(
        override val id: String = UUID.randomUUID().toString(),
        val content: String = "https://brother.com",
        val errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
        val sizePercent: Int = 100
    ) : LabelBlock()

    @Serializable
    @SerialName("barcode")
    data class BarcodeBlock(
        override val id: String = UUID.randomUUID().toString(),
        val content: String = "PT-D450VP-001",
        val format: BarcodeFormatOption = BarcodeFormatOption.CODE_128,
        val showHumanReadable: Boolean = true,
        val moduleWidthPx: Int = 2
    ) : LabelBlock()

    @Serializable
    @SerialName("image")
    data class ImageBlock(
        override val id: String = UUID.randomUUID().toString(),
        val name: String = "Изображение",
        val pngBase64: String = "",
        val ditherMode: DitherMode = DitherMode.FLOYD_STEINBERG,
        val threshold: Int = 140,
        val invert: Boolean = false,
        val scalePercent: Int = 100
    ) : LabelBlock()
}

@Serializable
data class LabelDocument(
    val tapeWidth: TapeWidth = TapeWidth.MM_12,
    val blocks: List<LabelBlock> = listOf(
        LabelBlock.TextBlock(text = "Образец этикетки", autoFit = true)
    ),
    val blockSpacingPx: Int = 14,
    val horizontalPaddingPx: Int = 12,
    val showBorder: Boolean = false,
    val invertColors: Boolean = false
)

@Serializable
data class PrintOptions(
    val copies: Int = 1,
    val feedMarginDots: Int = 14, // ~2 mm
    val chainPrint: Boolean = true, // Saves tape between labels/copies (FF without eject)
    val useCompression: Boolean = true, // PackBits TIFF (standard for PT-D450)
    val mirrorVertical: Boolean = false // Calibrated on real PT-D450VP: no mirroring needed
)
