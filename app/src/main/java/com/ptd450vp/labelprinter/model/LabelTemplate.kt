package com.ptd450vp.labelprinter.model

import kotlinx.serialization.Serializable

@Serializable
data class LabelTemplate(
    val id: String,
    val name: String,
    val description: String,
    val builtIn: Boolean,
    val document: LabelDocument
)
