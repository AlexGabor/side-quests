package com.alexgabor.lib.documentaccess

import androidx.compose.runtime.Composable

/**
 * The platform picker for files and folders: the Storage Access Framework on Android, the document
 * picker on iOS. Picked items are opened in place, never copied, and the app keeps read access to them.
 */
interface DocumentPicker {

    /**
     * A launcher bound to the calling composition. [onPicked] gets the pick, or `null` when the user
     * cancels.
     */
    @Composable
    fun rememberLauncher(onPicked: (PickedDocument?) -> Unit): DocumentPickerLauncher
}

interface DocumentPickerLauncher {

    /** Asks for one file, of one of [mimeTypes] (wildcards such as `audio/` plus `*` allowed). */
    fun pickFile(mimeTypes: List<String> = listOf("*/*"))

    /** Asks for one folder. */
    fun pickFolder()
}
