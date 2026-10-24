package com.alexgabor.bookplate.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alexgabor.design.riso.RisoTheme
import com.alexgabor.design.riso.attributes.Body
import com.alexgabor.design.riso.attributes.Heading3
import com.alexgabor.design.riso.components.Button
import com.alexgabor.design.riso.risograph.inks.risoInk
import com.alexgabor.lib.audiometadata.AudioMetadataReader
import com.alexgabor.lib.documentaccess.DocumentAccess
import com.alexgabor.lib.documentaccess.DocumentPicker
import com.alexgabor.lib.documentaccess.PickedDocument
import com.alexgabor.lib.documentaccess.Reachability
import com.alexgabor.lib.documentaccess.read
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// TEMPORARY (M7 debug entry): proves `lib/documentaccess` on the phones until the real Home lands.
// M9 deletes this folder and its one call in `Placeholders.kt`.

internal data class DebugFile(val relativePath: String, val size: Long?, val duration: Duration?)

internal data class DebugPick(
    val name: String,
    val isFolder: Boolean,
    val reachability: Reachability,
    val audioFiles: List<DebugFile>,
)

/**
 * Picks kept in [store], each listed through [access] and every audio file in it parsed for its
 * duration. Nothing is saved with the instance state: the picks are read back from [store], and the
 * files from the platform, whenever the holder is created.
 */
internal class DocumentAccessDebugState(
    private val scope: CoroutineScope,
    private val access: DocumentAccess,
    private val store: DebugPickStore,
) {

    val picks: State<List<DebugPick>>
        field = mutableStateOf(emptyList<DebugPick>())

    val loading: State<Boolean>
        field = mutableStateOf(false)

    private var job: Job? = null

    init {
        refresh()
    }

    fun onPicked(pick: PickedDocument?) {
        if (pick == null) return
        scope.launch {
            store.add(pick)
            refresh()
        }
    }

    fun clear() {
        scope.launch {
            store.clear()
            refresh()
        }
    }

    fun refresh() {
        job?.cancel()
        job = scope.launch {
            loading.value = true
            picks.value = store.picks.first().map { describe(it) }
            loading.value = false
        }
    }

    private suspend fun describe(pick: PickedDocument): DebugPick {
        val reachability = access.reachability(pick.ref)
        val files = if (reachability == Reachability.Reachable) {
            runCatching { access.list(pick.ref) }.getOrDefault(emptyList()).mapNotNull { entry ->
                val metadata = runCatching { access.read(entry.ref) { AudioMetadataReader.read(it) } }.getOrNull()
                metadata?.let { DebugFile(entry.relativePath, entry.size, it.duration) }
            }
        } else {
            emptyList()
        }
        return DebugPick(pick.name, pick.ref.isFolder, reachability, files)
    }
}

@Composable
internal fun rememberDocumentAccessDebugState(
    access: DocumentAccess = koinInject(),
    store: DebugPickStore = koinInject(),
): DocumentAccessDebugState {
    val scope = rememberCoroutineScope()
    return remember(access, store) { DocumentAccessDebugState(scope, access, store) }
}

@Composable
internal fun DocumentAccessDebug(
    modifier: Modifier = Modifier,
    picker: DocumentPicker = koinInject(),
    state: DocumentAccessDebugState = rememberDocumentAccessDebugState(),
) {
    val launcher = picker.rememberLauncher(state::onPicked)
    val picks by state.picks
    val loading by state.loading
    val ink = Modifier.risoInk(RisoTheme.colors.content)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Heading3(text = "Debug: document access", modifier = ink)
        Body(text = "Temporary, removed in M9", modifier = ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(text = "Pick file", onClick = { launcher.pickFile(listOf("audio/*")) })
            Button(text = "Pick folder", onClick = launcher::pickFolder)
            Button(text = "Refresh", onClick = state::refresh)
            Button(text = "Clear", onClick = state::clear)
        }
        if (loading) Body(text = "Reading…", modifier = ink)
        picks.forEach { pick ->
            val kind = if (pick.isFolder) "Folder" else "File"
            Body(text = "$kind: ${pick.name} (${pick.reachability})", modifier = ink)
            pick.audioFiles.forEach { file ->
                Body(
                    text = "${file.relativePath} · ${file.size?.let(::formatSize) ?: "?"} · ${file.duration?.let(::formatDuration) ?: "?"}",
                    modifier = ink.padding(start = 16.dp),
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${bytes / 100_000 / 10.0} MB"
    bytes >= 1_000 -> "${bytes / 100 / 10.0} KB"
    else -> "$bytes B"
}

internal fun formatDuration(duration: Duration): String = duration.toComponents { hours, minutes, seconds, _ ->
    if (hours > 0) "$hours:${minutes.pad()}:${seconds.pad()}" else "$minutes:${seconds.pad()}"
}

private fun Int.pad(): String = toString().padStart(2, '0')
