package com.alexgabor.bookplate.core.library

import org.koin.core.module.Module

/** Binds the platform's [LibraryDatabase] and [CoverStore]. */
internal expect val libraryStorageModule: Module
