package com.ptd450vp.labelprinter.data

import android.content.Context
import com.ptd450vp.labelprinter.model.LabelProject
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException

/** Stores the user's named projects as one JSON catalogue in app-private storage. */
class ProjectRepository(context: Context) {

    private val file = File(context.filesDir, "projects.json")

    fun list(): List<LabelProject> =
        load().sortedByDescending { it.updatedAt }

    fun save(project: LabelProject): LabelProject {
        val saved = project.copy(updatedAt = System.currentTimeMillis())
        write(load().filterNot { it.id == saved.id } + saved)
        return saved
    }

    fun delete(id: String) {
        write(load().filterNot { it.id == id })
    }

    private fun load(): List<LabelProject> {
        if (!file.exists()) return emptyList()
        return runCatching {
            ProjectCodec.json.decodeFromString<List<LabelProject>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private fun write(projects: List<LabelProject>) {
        val temp = File(file.parentFile, "projects.json.tmp")
        temp.writeText(ProjectCodec.json.encodeToString(projects))
        if (!temp.renameTo(file)) {
            throw IOException("Не удалось записать каталог проектов")
        }
    }
}
