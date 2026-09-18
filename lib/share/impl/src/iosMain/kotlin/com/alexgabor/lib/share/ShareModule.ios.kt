package com.alexgabor.lib.share

import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

actual val shareModule: Module = module {
    single<Share> { IosShare() }
}

/**
 * Shares through the activity sheet, presented over whatever is on top.
 *
 * Presented from the topmost controller because one that is already presenting refuses, quietly,
 * to present again. On iPad the sheet is a popover and needs something to point at, or UIKit
 * throws; the whole view is as good an anchor as any.
 */
internal class IosShare : Share {

    override val isAvailable: Boolean = true

    @OptIn(ExperimentalForeignApi::class)
    override fun text(text: String) {
        val presenter = topViewController() ?: return
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        sheet.popoverPresentationController?.apply {
            sourceView = presenter.view
            sourceRect = presenter.view.bounds
        }
        presenter.presentViewController(sheet, animated = true, completion = null)
    }

    private fun topViewController(): UIViewController? {
        val window = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() }
        var top = window?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: return top
    }
}
