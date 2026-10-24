package com.alexgabor.lib.documentaccess

import org.koin.dsl.binds
import org.koin.dsl.module

/** Binds one [FakeDocumentAccess] as itself, [DocumentAccess] and [DocumentPicker]. */
val fakeDocumentAccessModule = module {
    single { FakeDocumentAccess() } binds arrayOf(DocumentAccess::class, DocumentPicker::class)
}
