package com.ptd450vp.labelprinter.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import com.ptd450vp.labelprinter.model.DitherMode
import com.ptd450vp.labelprinter.model.FontChoice
import com.ptd450vp.labelprinter.model.LabelBlock
import com.ptd450vp.labelprinter.model.LabelDocument
import com.ptd450vp.labelprinter.model.TextAlignOption
import com.ptd450vp.labelprinter.printer.MonochromeImage
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class RenderedLabel(
    val monochrome: MonochromeImage,
    val previewBitmap: Bitmap,
    val warnings: List<String>
)

/**
 * Renders a [LabelDocument] into a pixel-exact 1-bit [MonochromeImage] matching the
 * selected Brother TZe tape printable height at 180 dpi, plus a crisp preview [Bitmap].
 */
object LabelRenderer {

    private const val MAX_LABEL_WIDTH_PX = 3600 // ~50 cm safety cap

    fun render(document: LabelDocument): RenderedLabel {
        val tapeHeight = document.tapeWidth.printablePixels
        val borderInset = if (document.showBorder) 3 else 0
        val innerHeight = (tapeHeight - borderInset * 2).coerceAtLeast(12)
        val warnings = mutableListOf<String>()

        val renderedBlocks = mutableListOf<Bitmap>()
        for (block in document.blocks) {
            val bmp = when (block) {
                is LabelBlock.TextBlock -> renderTextBlock(block, innerHeight)
                is LabelBlock.QrBlock -> renderQrBlock(block, innerHeight, warnings)
                is LabelBlock.BarcodeBlock -> renderBarcodeBlock(block, innerHeight, warnings)
                is LabelBlock.ImageBlock -> renderImageBlock(block, innerHeight, warnings)
            }
            if (bmp != null) {
                renderedBlocks.add(bmp)
            }
        }

        val padX = document.horizontalPaddingPx.coerceIn(0, 120)
        val spacing = document.blockSpacingPx.coerceIn(0, 120)

        val contentWidth = if (renderedBlocks.isEmpty()) {
            80
        } else {
            renderedBlocks.sumOf { it.width } + spacing * (renderedBlocks.size - 1)
        }
        val totalWidth = (contentWidth + padX * 2 + borderInset * 2)
            .coerceIn(24, MAX_LABEL_WIDTH_PX)

        val composed = Bitmap.createBitmap(totalWidth, tapeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composed)
        canvas.drawColor(Color.WHITE)

        var cursorX = padX + borderInset
        for (bmp in renderedBlocks) {
            val topY = borderInset + (innerHeight - bmp.height) / 2
            canvas.drawBitmap(bmp, cursorX.toFloat(), topY.toFloat(), null)
            cursorX += bmp.width + spacing
        }

        if (document.showBorder) {
            val borderPaint = Paint().apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 2f
                isAntiAlias = false
            }
            canvas.drawRect(
                1f,
                1f,
                (totalWidth - 2).toFloat(),
                (tapeHeight - 2).toFloat(),
                borderPaint
            )
        }

        // Convert final composed label to pure 1-bit MonochromeImage
        val argb = IntArray(totalWidth * tapeHeight)
        composed.getPixels(argb, 0, totalWidth, 0, 0, totalWidth, tapeHeight)
        val monoPixels = Dithering.toMonochrome(
            argbPixels = argb,
            width = totalWidth,
            height = tapeHeight,
            mode = DitherMode.THRESHOLD,
            threshold = 150,
            invert = document.invertColors
        )

        val monochrome = MonochromeImage(
            width = totalWidth,
            height = tapeHeight,
            pixels = monoPixels
        )

        // Build crisp WYSIWYG 1-bit preview bitmap directly from monochrome pixels
        val previewArgb = IntArray(monoPixels.size) { idx ->
            if (monoPixels[idx]) Color.BLACK else Color.WHITE
        }
        val previewBitmap = Bitmap.createBitmap(
            previewArgb,
            totalWidth,
            tapeHeight,
            Bitmap.Config.ARGB_8888
        )

        return RenderedLabel(
            monochrome = monochrome,
            previewBitmap = previewBitmap,
            warnings = warnings
        )
    }

    private fun renderTextBlock(block: LabelBlock.TextBlock, maxHeight: Int): Bitmap? {
        val rawLines = block.text.split("\n")
        if (rawLines.all { it.isEmpty() }) return null

        val typeface = resolveTypeface(block.font, block.bold, block.italic)
        val paint = Paint().apply {
            color = Color.BLACK
            this.typeface = typeface
            isAntiAlias = false // Crisp thermal edges without blurry gray halo
            isSubpixelText = false
        }

        val chosenSize = if (block.autoFit) {
            findBestFontSize(rawLines, paint, maxHeight, block.lineSpacingMultiplier)
        } else {
            block.fontSizePx.coerceIn(10, 140).toFloat()
        }
        paint.textSize = chosenSize

        val fm = paint.fontMetrics
        val singleLineHeight = (fm.descent - fm.ascent)
        val lineStep = singleLineHeight * block.lineSpacingMultiplier.coerceIn(0.85f, 1.8f)

        val lineWidths = rawLines.map { line ->
            if (line.isEmpty()) 0f else paint.measureText(line)
        }
        val blockWidth = max(8, ceil(lineWidths.maxOrNull() ?: 8f).toInt() + 4)
        val totalTextHeight = singleLineHeight + lineStep * (rawLines.size - 1)
        val bitmapHeight = maxHeight

        val bitmap = Bitmap.createBitmap(blockWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val startTop = (bitmapHeight - totalTextHeight) / 2f
        rawLines.forEachIndexed { idx, line ->
            if (line.isNotEmpty()) {
                val lw = lineWidths[idx]
                val x = when (block.align) {
                    TextAlignOption.LEFT -> 2f
                    TextAlignOption.CENTER -> (blockWidth - lw) / 2f
                    TextAlignOption.RIGHT -> blockWidth - lw - 2f
                }
                val baseline = startTop + idx * lineStep - fm.ascent
                canvas.drawText(line, x, baseline, paint)
            }
        }
        return bitmap
    }

    private fun findBestFontSize(
        lines: List<String>,
        paint: Paint,
        maxHeight: Int,
        lineSpacing: Float
    ): Float {
        var low = 10f
        var high = maxHeight.toFloat() * 1.15f
        var best = low
        val spacing = lineSpacing.coerceIn(0.85f, 1.8f)
        repeat(14) {
            val mid = (low + high) / 2f
            paint.textSize = mid
            val fm = paint.fontMetrics
            val singleH = fm.descent - fm.ascent
            val totalH = singleH + singleH * spacing * (lines.size - 1)
            if (totalH <= maxHeight - 2f) {
                best = mid
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }

    private fun renderQrBlock(
        block: LabelBlock.QrBlock,
        maxHeight: Int,
        warnings: MutableList<String>
    ): Bitmap? {
        val targetSize = (maxHeight * block.sizePercent.coerceIn(40, 100) / 100f)
            .roundToInt()
            .coerceIn(21, maxHeight)

        val matrixResult = BarcodeGenerator.encodeQrMatrix(
            content = block.content,
            targetSizePx = targetSize,
            errorCorrection = block.errorCorrection
        )
        return matrixResult.fold(
            onSuccess = { BarcodeGenerator.bitMatrixToBitmap(it) },
            onFailure = { err ->
                warnings.add("QR: ${err.message}")
                null
            }
        )
    }

    private fun renderBarcodeBlock(
        block: LabelBlock.BarcodeBlock,
        maxHeight: Int,
        warnings: MutableList<String>
    ): Bitmap? {
        val showCaption = block.showHumanReadable && maxHeight >= 30
        val captionHeight = if (showCaption) (maxHeight * 0.28f).roundToInt().coerceIn(11, 20) else 0
        val barsHeight = (maxHeight - captionHeight - if (showCaption) 2 else 0).coerceAtLeast(14)

        val matrixResult = BarcodeGenerator.encodeBarcodeMatrix(
            content = block.content,
            format = block.format,
            heightPx = barsHeight,
            moduleWidthPx = block.moduleWidthPx
        )

        return matrixResult.fold(
            onSuccess = { matrix ->
                val barsBmp = BarcodeGenerator.bitMatrixToBitmap(matrix)
                if (!showCaption) return@fold barsBmp

                val captionPaint = Paint().apply {
                    color = Color.BLACK
                    typeface = Typeface.MONOSPACE
                    textSize = (captionHeight - 2).coerceAtLeast(9).toFloat()
                    isAntiAlias = false
                }
                val captionText = block.content.trim()
                val textWidth = ceil(captionPaint.measureText(captionText)).toInt() + 4
                val finalWidth = max(barsBmp.width, textWidth)
                val finalBitmap = Bitmap.createBitmap(finalWidth, maxHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(finalBitmap)
                canvas.drawColor(Color.WHITE)

                val barsX = (finalWidth - barsBmp.width) / 2f
                canvas.drawBitmap(barsBmp, barsX, 0f, null)

                val textX = (finalWidth - captionPaint.measureText(captionText)) / 2f
                val fm = captionPaint.fontMetrics
                val textBaseline = barsBmp.height + 2f - fm.ascent
                canvas.drawText(captionText, textX, textBaseline.coerceAtMost(maxHeight - 1f), captionPaint)
                finalBitmap
            },
            onFailure = { err ->
                warnings.add("Штрихкод (${block.format.displayName}): ${err.message}")
                null
            }
        )
    }

    private fun renderImageBlock(
        block: LabelBlock.ImageBlock,
        maxHeight: Int,
        warnings: MutableList<String>
    ): Bitmap? {
        if (block.pngBase64.isBlank()) {
            warnings.add("Картинка «${block.name}» ещё не выбрана")
            return null
        }
        val rawBytes = runCatching { Base64.decode(block.pngBase64, Base64.DEFAULT) }.getOrNull()
        val decoded = rawBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (decoded == null) {
            warnings.add("Не удалось декодировать картинку «${block.name}»")
            return null
        }

        val targetHeight = (maxHeight * block.scalePercent.coerceIn(30, 100) / 100f)
            .roundToInt()
            .coerceIn(12, maxHeight)
        val aspect = decoded.width.toFloat() / decoded.height.coerceAtLeast(1)
        val targetWidth = (targetHeight * aspect).roundToInt().coerceIn(8, 1200)

        val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        val argb = IntArray(targetWidth * targetHeight)
        scaled.getPixels(argb, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        val mono = Dithering.toMonochrome(
            argbPixels = argb,
            width = targetWidth,
            height = targetHeight,
            mode = block.ditherMode,
            threshold = block.threshold,
            invert = block.invert
        )

        val outArgb = IntArray(mono.size) { idx ->
            if (mono[idx]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(outArgb, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    }

    private fun resolveTypeface(font: FontChoice, bold: Boolean, italic: Boolean): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return when (font) {
            FontChoice.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, style)
            FontChoice.SERIF -> Typeface.create(Typeface.SERIF, style)
            FontChoice.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, style)
            FontChoice.CONDENSED -> Typeface.create("sans-serif-condensed", style)
        }
    }

    /**
     * Loads an image selected from gallery, scales it to a reasonable max height (256px)
     * so templates stay lightweight, and returns a Base64-encoded PNG.
     */
    fun importImageUriToBase64(context: Context, uri: Uri, maxHeightPx: Int = 256): Result<String> =
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Не удалось открыть выбранное изображение")
            val original = stream.use { BitmapFactory.decodeStream(it) }
                ?: error("Формат изображения не поддерживается")

            val resized = if (original.height > maxHeightPx || original.width > maxHeightPx * 4) {
                val scale = (maxHeightPx.toFloat() / original.height)
                    .coerceAtMost((maxHeightPx * 4f) / original.width)
                    .coerceAtMost(1f)
                val newW = (original.width * scale).roundToInt().coerceAtLeast(1)
                val newH = (original.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(original, newW, newH, true)
            } else {
                original
            }

            // Composite onto white so PNG transparency doesn't turn black unexpectedly
            val flattened = Bitmap.createBitmap(resized.width, resized.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flattened)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(resized, null, Rect(0, 0, resized.width, resized.height), null)

            val baos = ByteArrayOutputStream()
            flattened.compress(Bitmap.CompressFormat.PNG, 100, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }
}
