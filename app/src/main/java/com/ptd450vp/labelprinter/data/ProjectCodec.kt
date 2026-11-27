package com.ptd450vp.labelprinter.data

import com.ptd450vp.labelprinter.model.LabelProject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Pure serialization helpers for project files (unit-testable without Android). */
object ProjectCodec {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(project: LabelProject): String = json.encodeToString(project)

    fun decode(text: String): LabelProject {
        val project = try {
            json.decodeFromString<LabelProject>(text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Неверный формат файла проекта", e)
        }
        require(project.formatVersion == LabelProject.FORMAT_VERSION) {
            "Неподдерживаемая версия проекта: ${project.formatVersion}"
        }
        require(project.name.isNotBlank()) {
            "В файле проекта не указано название"
        }
        return project
    }

    /** Safe, human-readable suggestion for Android's "Save as" dialog. */
    fun fileNameFor(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[^A-Za-z0-9А-Яа-яЁё._-]"), "_")
            .replace(Regex("_{2,}"), "_")
            .take(48)
            .trim('_', '.')
        return "${cleaned.ifEmpty { "label" }}.pt450.json"
    }
}
