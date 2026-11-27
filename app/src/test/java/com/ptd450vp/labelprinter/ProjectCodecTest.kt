package com.ptd450vp.labelprinter

import com.ptd450vp.labelprinter.data.ProjectCodec
import com.ptd450vp.labelprinter.model.LabelBlock
import com.ptd450vp.labelprinter.model.LabelDocument
import com.ptd450vp.labelprinter.model.LabelProject
import com.ptd450vp.labelprinter.model.PrintOptions
import com.ptd450vp.labelprinter.model.TapeWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCodecTest {

    private fun sampleProject() = LabelProject(
        name = "Кабельная бирка",
        document = LabelDocument(
            tapeWidth = TapeWidth.MM_12,
            blocks = listOf(
                LabelBlock.TextBlock(id = "t1", text = "Тестовая этикетка"),
                LabelBlock.QrBlock(id = "q1", content = "https://example.org"),
                LabelBlock.ImageBlock(id = "i1", pngBase64 = "iVBORw0KGgo=")
            ),
            showBorder = true
        ),
        printOptions = PrintOptions(copies = 3, chainPrint = false, mirrorVertical = true)
    )

    @Test
    fun roundTripKeepsDocumentPrintOptionsAndBlockTypes() {
        val original = sampleProject()
        val decoded = ProjectCodec.decode(ProjectCodec.encode(original))

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.formatVersion, decoded.formatVersion)
        assertEquals(original.document.tapeWidth, decoded.document.tapeWidth)
        assertEquals(original.document.showBorder, decoded.document.showBorder)
        assertEquals(3, decoded.document.blocks.size)
        assertTrue(decoded.document.blocks[0] is LabelBlock.TextBlock)
        assertTrue(decoded.document.blocks[1] is LabelBlock.QrBlock)
        assertTrue(decoded.document.blocks[2] is LabelBlock.ImageBlock)
        assertEquals(3, decoded.printOptions.copies)
        assertEquals(false, decoded.printOptions.chainPrint)
        assertEquals(true, decoded.printOptions.mirrorVertical)
    }

    @Test
    fun rejectsUnsupportedVersionAndBlankName() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCodec.decode(ProjectCodec.encode(sampleProject().copy(formatVersion = 99)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCodec.decode(ProjectCodec.encode(sampleProject().copy(name = "  ")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProjectCodec.decode("{ not json")
        }
    }

    @Test
    fun buildsSafeFileName() {
        assertEquals("label.pt450.json", ProjectCodec.fileNameFor("///"))
        val name = ProjectCodec.fileNameFor("Офис: бирка #1/2")
        assertTrue(name.endsWith(".pt450.json"))
        assertTrue(
            "Имя файла не должно содержать ':' или '/' — $name",
            ':' !in name && '/' !in name
        )
    }
}
