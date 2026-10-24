package com.alexgabor.lib.documentaccess

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Picks through the Storage Access Framework, and keeps read access — only read — to what was picked
 * across restarts by persisting the grant.
 */
internal object AndroidDocumentPicker : DocumentPicker {

    @Composable
    override fun rememberLauncher(onPicked: (PickedDocument?) -> Unit): DocumentPickerLauncher {
        val resolver = LocalContext.current.contentResolver
        val callback by rememberUpdatedState(onPicked)

        val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            callback(uri?.let { resolver.picked(it, isFolder = false) })
        }
        val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            callback(uri?.let { resolver.picked(it, isFolder = true) })
        }

        return remember(file, folder) {
            object : DocumentPickerLauncher {
                override fun pickFile(mimeTypes: List<String>) = file.launch(mimeTypes.toTypedArray())
                override fun pickFolder() = folder.launch(null)
            }
        }
    }
}

private fun ContentResolver.picked(uri: Uri, isFolder: Boolean): PickedDocument? {
    try {
        takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // The provider offered no persistable grant, so the pick would stop working on restart.
        return null
    }
    // A tree URI names no document by itself; its display name is on the tree's root document.
    val document = if (isFolder) {
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
    } else {
        uri
    }
    val name = displayName(document) ?: uri.lastPathSegment.orEmpty()
    return PickedDocument(DocumentRef(root = uri.toString(), child = null, isFolder = isFolder), name)
}

private fun ContentResolver.displayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
