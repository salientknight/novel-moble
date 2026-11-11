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

data class ProjectItem(
    val handle: String,
    val parent: String,
    val name: String,
    val type: String,
    val category: String,
    val order: Int
)

data class ProjectSnapshot(
    val name: String,
    val items: List<ProjectItem>,
    val contentFolder: DocumentFile
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ProjectBrowser(
                    openProject = ::readProject,
                    readDocument = ::readDocument,
                    rememberFolder = { uri ->
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                )
            }
        }
    }

    private fun readProject(uri: Uri): ProjectSnapshot {
        val root = DocumentFile.fromTreeUri(this, uri)
            ?: error("Cannot access selected folder")
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

    private fun readDocument(project: ProjectSnapshot, item: ProjectItem): String {
        require(item.type == "FILE")
        val file = project.contentFolder.findFile("${item.handle}.md")
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
    rememberFolder: (Uri) -> Unit
) {
    var project by remember { mutableStateOf<ProjectSnapshot?>(null) }
    var selected by remember { mutableStateOf<ProjectItem?>(null) }
    var document by remember { mutableStateOf("") }
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
                Text("Open novelWriter project folder")
            }
            Text("Read-only preview: no files will be modified.")
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
                    Button(onClick = { selected = null }) { Text("Back to project") }
                    Text(selected!!.name, style = MaterialTheme.typography.titleMedium)
                    LazyColumn {
                        item { Text(document) }
                    }
                }
            }
        }
    }
}
