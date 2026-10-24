package com.alexgabor.lib.documentaccess

import org.koin.core.module.Module
import org.koin.dsl.module

actual val documentAccessModule: Module = module {
    single<DocumentAccess> { IosDocumentAccess() }
    single<DocumentPicker> { IosDocumentPicker }
}
