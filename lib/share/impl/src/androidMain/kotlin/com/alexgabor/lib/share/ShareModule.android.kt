package com.alexgabor.lib.share

import android.content.Context
import android.content.Intent
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val shareModule: Module = module {
    single<Share> { AndroidShare(androidContext()) }
}

/**
 * Shares through the system chooser.
 *
 * Bound to the application context rather than an activity, so the chooser has to be started as a
 * new task — without the flag, starting an activity from outside one throws.
 */
internal class AndroidShare(private val context: Context) : Share {

    override val isAvailable: Boolean = true

    override fun text(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, null)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
