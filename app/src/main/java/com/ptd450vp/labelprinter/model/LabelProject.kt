package com.ptd450vp.labelprinter.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A saved label project: full document + print settings, self-contained (images are base64 inside [document]).
 */
@Serializable
data class LabelProject(
    val formatVersion: Int = FORMAT_VERSION,
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val document: LabelDocument,
    val printOptions: PrintOptions = PrintOptions()
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}
