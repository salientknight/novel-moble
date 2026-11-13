package com.salientknight.novelmobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import org.json.JSONObject
import org.json.JSONArray

data class ProjectItem(
    val handle: String,
    val parent: String,
    val name: String,
    val type: String,
    val category: String,
    val order: Int,
    val path: String = ""
)

data class ProjectSnapshot(
    val name: String,
    val items: List<ProjectItem>,
    val contentFolder: DocumentFile,
    val root: DocumentFile = contentFolder,
    val nexus: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ProjectBrowser(
                    openProject = ::readProject,
                    readDocument = ::readDocument,
                    saveDocument = ::saveDocument,
                    rememberFolder = { uri ->
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                )
            }
        }
    }

    private fun readProject(uri: Uri): ProjectSnapshot {
        val root = DocumentFile.fromTreeUri(this, uri)
            ?: error("Cannot access selected folder")
        if (root.findFile("project.json") != null) return readNexusProject(root)
        val projectFile = root.findFile("nwProject.nwx")
            ?: error("Select the novelWriter project folder containing nwProject.nwx")
        val contentFolder = root.findFile("content")
            ?: error("Project content folder is missing")
        val parsed = contentResolver.openInputStream(projectFile.uri)?.use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, "UTF-8")
            val items = mutableListOf<ProjectItem>()
            var projectName = root.name ?: "novelWriter"
            var inProject = false
            var currentItem: ProjectItem? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "project" -> inProject = true
                        "item" -> {
                            currentItem = ProjectItem(
                                handle = parser.getAttributeValue(null, "handle") ?: "",
                                parent = parser.getAttributeValue(null, "parent") ?: "None",
                                name = "",
                                type = parser.getAttributeValue(null, "type") ?: "",
                                category = parser.getAttributeValue(null, "class") ?: "",
                                order = parser.getAttributeValue(null, "order")?.toIntOrNull() ?: 0
                            )
                        }
                        "name" -> {
                            val value = parser.nextText()
                            if (currentItem != null) {
                                currentItem = currentItem.copy(name = value)
                            } else if (inProject) {
                                projectName = value
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (parser.name) {
                        "item" -> {
                            currentItem?.let(items::add)
                            currentItem = null
                        }
                        "project" -> inProject = false
                    }
                }
                event = parser.next()
            }
            ProjectSnapshot(projectName, items, contentFolder)
        } ?: error("Cannot read nwProject.nwx")
        return parsed
    }

    private fun resolvePath(root: DocumentFile, relative: String): DocumentFile? {
        var current = root
        for (segment in relative.split('/').filter { it.isNotEmpty() }) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    private fun readNexusProject(root: DocumentFile): ProjectSnapshot {
        val manifest = root.findFile("project.json") ?: error("Missing project.json")
        val raw = contentResolver.openInputStream(manifest.uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot read project.json")
        val json = JSONObject(raw)
        require(json.optString("format") == "nexus-writer-1") { "Unsupported Nexus Writer project format" }
        val items = mutableListOf<ProjectItem>()
        var order = 0
        fun add(item: JSONObject, parent: String, kind: String, file: String = ""): String {
            val id = item.getString("id")
            items.add(ProjectItem(id, parent, item.optString("title", kind),
                if (file.isEmpty()) "FOLDER" else "FILE", kind, order++, file))
            return id
        }
        val books = json.optJSONArray("books") ?: JSONArray()
        for (bi in 0 until books.length()) {
            val book = books.getJSONObject(bi)
            val bookId = add(book, "None", "book")
            val chapters = book.optJSONArray("chapters") ?: JSONArray()
            for (ci in 0 until chapters.length()) {
                val chapter = chapters.getJSONObject(ci)
                val chapterId = add(chapter, bookId, "chapter", chapter.getString("path") + "/opening.md")
                val scenes = chapter.optJSONArray("scenes") ?: JSONArray()
                for (si in 0 until scenes.length()) {
                    val scene = scenes.getJSONObject(si)
                    add(scene, chapterId, "scene", scene.getString("path"))
                }
            }
        }
        val kinds = listOf("character", "note", "research", "image", "resource", "seed")
        val references = json.optJSONArray("references") ?: JSONArray()
        for (kind in kinds) {
            val groupId = "group-" + kind
            items.add(ProjectItem(groupId, "None", kind.replaceFirstChar { it.uppercase() } + "s",
                "FOLDER", "reference_group", order++))
            for (i in 0 until references.length()) {
                val entry = references.getJSONObject(i)
                if (entry.optString("kind") != kind) continue
                val chapterId = entry.optString("chapter_id")
                val parent = if (chapterId.isNotBlank() && chapterId != "null") chapterId else groupId
                add(entry, parent, kind, entry.getString("path"))
            }
        }
        return ProjectSnapshot(json.optString("title", root.name ?: "Nexus Writer"), items, root, root, true)
    }

    private fun saveDocument(project: ProjectSnapshot, item: ProjectItem, original: String, edited: String) {
        val file = if (project.nexus) resolvePath(project.root, item.path)
                   else project.contentFolder.findFile(item.handle + ".md")
            ?: error("Document missing")
        val current = contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot read document")
        check(current == original) { "Document changed externally. Save refused." }
        val backup = java.io.File(filesDir, "recovery/" + item.handle + "-" + System.currentTimeMillis() + ".md")
        check(backup.parentFile!!.exists() || backup.parentFile!!.mkdirs())
        backup.writeText(current)
        contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { it.write(edited) }
            ?: error("Cannot write document")
    }

    private fun readDocument(project: ProjectSnapshot, item: ProjectItem): String {
        require(item.type == "FILE")
        val file = if (project.nexus) resolvePath(project.root, item.path)
                   else project.contentFolder.findFile("${item.handle}.md")
            ?: error("Document ${item.name} is missing")
        return contentResolver.openInputStream(file.uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        } ?: error("Cannot read ${item.name}")
    }
}

@Composable
private fun ProjectBrowser(
    openProject: (Uri) -> ProjectSnapshot,
    readDocument: (ProjectSnapshot, ProjectItem) -> String,
    saveDocument: (ProjectSnapshot, ProjectItem, String, String) -> Unit,
    rememberFolder: (Uri) -> Unit
) {
    var project by remember { mutableStateOf<ProjectSnapshot?>(null) }
    var selected by remember { mutableStateOf<ProjectItem?>(null) }
    var document by remember { mutableStateOf("") }
    var edited by remember { mutableStateOf("") }
    var prose by remember { mutableStateOf(ProseFormat.parse("")) }
    var sourceMode by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                rememberFolder(uri)
                project = openProject(uri)
                selected = null
                document = ""
                error = null
            } catch (exception: Exception) {
                error = exception.message ?: "Could not open project"
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Novel Mobile", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { picker.launch(null) }) {
                Text("Open Nexus Writer or novelWriter project")
            }
            Text("Edit prose and tap Save. Complex formatted documents open in source mode to protect their markup.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            project?.let { snapshot ->
                Text(snapshot.name, style = MaterialTheme.typography.titleLarge)
                if (selected == null) {
                    val ordered = remember(snapshot) {
                        val children = snapshot.items.groupBy { it.parent }
                        fun walk(parent: String, depth: Int): List<Pair<ProjectItem, Int>> =
                            children[parent].orEmpty().sortedBy { it.order }.flatMap { item ->
                                listOf(item to depth) + walk(item.handle, depth + 1)
                            }
                        walk("None", 0)
                    }
                    LazyColumn {
                        items(ordered) { (item, depth) ->
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = item.type == "FILE") {
                                        try {
                                            document = readDocument(snapshot, item)
                                            prose = ProseFormat.parse(document)
                                            sourceMode = snapshot.nexus || !prose.plainMode
                                            edited = if (sourceMode) document else prose.prose
                                            selected = item
                                            error = null
                                        } catch (exception: Exception) {
                                            error = exception.message
                                        }
                                    }
                                    .padding(start = (depth * 12).dp, top = 8.dp, bottom = 8.dp)
                            ) {
                                Text(item.name)
                                Text(
                                    "${item.category} • ${item.type}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                } else {
                    Button(onClick = { if ((if (sourceMode) edited else prose.rebuild(edited)) == document) selected = null else error = "Save changes before leaving" }) { Text("Back to project") }
                    Button(onClick = {
                        try {
                            val output = if (sourceMode) edited else prose.rebuild(edited)
                            saveDocument(snapshot, selected!!, document, output)
                            document = output
                            prose = ProseFormat.parse(output)
                            edited = if (sourceMode) output else prose.prose
                            error = "Saved with local recovery backup"
                        } catch (e: Exception) { error = e.message ?: "Save failed" }
                    }, enabled = (if (sourceMode) edited else prose.rebuild(edited)) != document) { Text("Save") }
                    Text(selected!!.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (snapshot.nexus) "Markdown mode: Nexus Writer files are preserved exactly." else if (sourceMode) "Source mode: this document contains formatting that must be preserved." else "Prose mode: metadata and structural heading are hidden.")
                    OutlinedTextField(value = edited, onValueChange = { edited = it }, modifier = Modifier.fillMaxSize(), label = { Text("Document") })
                }
            }
        }
    }
}
