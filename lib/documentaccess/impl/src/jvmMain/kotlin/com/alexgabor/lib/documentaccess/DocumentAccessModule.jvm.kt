package com.alexgabor.lib.documentaccess

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module

actual val documentAccessModule: Module = module {
    single { NoDocumentAccess } binds arrayOf(DocumentAccess::class, DocumentPicker::class)
}

/**
 * The JVM only runs tests, and the apps that use this library ship on phones, so there is no picker and
 * nothing is ever granted. It exists so common code builds and links for the JVM.
 */
internal object NoDocumentAccess : DocumentAccess, DocumentPicker {

    override suspend fun list(ref: DocumentRef): List<DocumentEntry> = unavailable()

    override suspend fun open(ref: DocumentRef): DocumentSource = unavailable()

    override suspend fun reachability(ref: DocumentRef): Reachability = Reachability.AccessLost

    @Composable
    override fun rememberLauncher(onPicked: (PickedDocument?) -> Unit): DocumentPickerLauncher {
        val callback by rememberUpdatedState(onPicked)
        return remember {
            object : DocumentPickerLauncher {
                override fun pickFile(mimeTypes: List<String>) = callback(null)
                override fun pickFolder() = callback(null)
            }
        }
    }

    private fun unavailable(): Nothing =
        throw DocumentUnavailableException(Reachability.AccessLost, "No documents on the JVM")
}
