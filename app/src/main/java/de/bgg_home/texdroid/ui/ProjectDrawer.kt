package de.bgg_home.texdroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import de.bgg_home.texdroid.R
import de.bgg_home.texdroid.storage.ProjectEntry

/**
 * Projekt-Sidebar (QW 4.1): zeigt den gewählten Ordner als Dateibaum. Ordner
 * zuerst, dann Dateien; `.tex` hervorgehoben. Tippen öffnet die Datei bzw.
 * navigiert in Unterordner. Die aktuell geöffnete Datei ist markiert.
 */
@Composable
fun ProjectDrawer(
    folderName: String?,
    canGoUp: Boolean,
    entries: List<ProjectEntry>,
    currentUri: Uri?,
    onOpenFolder: () -> Unit,
    onUp: () -> Unit,
    onEntryClick: (ProjectEntry) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
        ) {
            Text(
                folderName ?: stringResource(R.string.project_none),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenFolder) {
                Icon(Icons.Filled.FolderOpen, contentDescription = stringResource(R.string.open_folder))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        if (folderName == null) {
            Text(
                stringResource(R.string.project_drawer_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            return
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            if (canGoUp) {
                item {
                    EntryRow(
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                        label = "..",
                        selected = false,
                        onClick = onUp,
                    )
                }
            }
            items(entries, key = { it.uri.toString() }) { entry ->
                EntryRow(
                    icon = {
                        Icon(
                            when {
                                entry.isDir -> Icons.Filled.Folder
                                entry.isTex -> Icons.Filled.Description
                                else -> Icons.Filled.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = if (entry.isTex) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    },
                    label = entry.name,
                    selected = entry.uri == currentUri,
                    onClick = { onEntryClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            icon()
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
