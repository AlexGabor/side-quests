package com.alexgabor.lib.documentaccess

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val documentAccessModule: Module = module {
    single<DocumentAccess> { AndroidDocumentAccess(androidContext().contentResolver) }
    single<DocumentPicker> { AndroidDocumentPicker }
}
