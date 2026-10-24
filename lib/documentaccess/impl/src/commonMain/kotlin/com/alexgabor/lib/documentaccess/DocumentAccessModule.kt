package com.alexgabor.lib.documentaccess

import org.koin.core.module.Module

/** Binds the [DocumentAccess] and [DocumentPicker] of whichever platform this is built for. */
expect val documentAccessModule: Module
