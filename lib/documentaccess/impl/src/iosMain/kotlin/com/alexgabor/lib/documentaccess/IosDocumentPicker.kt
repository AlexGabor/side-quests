package com.alexgabor.lib.documentaccess

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.UniformTypeIdentifiers.UTTypeText
import platform.darwin.NSObject

/**
 * Picks through `UIDocumentPickerViewController`, opened in place rather than copied, and keeps a
 * bookmark of what was picked so it can be reached again after a restart.
 */
internal object IosDocumentPicker : DocumentPicker {

    @Composable
    override fun rememberLauncher(onPicked: (PickedDocument?) -> Unit): DocumentPickerLauncher {
        val callback by rememberUpdatedState(onPicked)
        return remember { IosLauncher { callback(it) } }
    }
}

private class IosLauncher(private val onPicked: (PickedDocument?) -> Unit) : DocumentPickerLauncher {

    // The picker holds its delegate weakly, so the launcher keeps it alive while the picker is up.
    private var delegate: Delegate? = null

    override fun pickFile(mimeTypes: List<String>) =
        present(mimeTypes.map(::contentType).distinct(), isFolder = false)

    override fun pickFolder() = present(listOf(UTTypeFolder), isFolder = true)

    private fun present(types: List<UTType>, isFolder: Boolean) {
        val presenter = topViewController() ?: return onPicked(null)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = false)
        picker.allowsMultipleSelection = false
        val delegate = Delegate { url ->
            this.delegate = null
            onPicked(url?.let { picked(it, isFolder) })
        }
        this.delegate = delegate
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    private class Delegate(private val onResult: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {

        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            onResult(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onResult(null)
        }
    }
}

/** A bookmark of [url], made inside its security scope so the bookmark carries the scope with it. */
private fun picked(url: NSURL, isFolder: Boolean): PickedDocument? {
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        val bookmark = Bookmarks.create(url) ?: return null
        return PickedDocument(
            ref = DocumentRef(root = bookmark, child = null, isFolder = isFolder),
            name = url.lastPathComponent.orEmpty(),
        )
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

/** Wildcards have no exact type, so the common ones map to their parent type. */
private fun contentType(mimeType: String): UTType = when (mimeType) {
    "*/*" -> UTTypeItem
    "audio/*" -> UTTypeAudio
    "video/*" -> UTTypeMovie
    "image/*" -> UTTypeImage
    "text/*" -> UTTypeText
    else -> UTType.typeWithMIMEType(mimeType) ?: UTTypeItem
}

/** Presented from the topmost controller, as one that is already presenting refuses to present again. */
private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
    var top = window?.rootViewController ?: return null
    while (true) top = top.presentedViewController ?: return top
}
