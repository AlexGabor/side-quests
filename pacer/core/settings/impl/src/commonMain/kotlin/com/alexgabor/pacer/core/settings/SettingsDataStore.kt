package com.alexgabor.pacer.core.settings

import org.koin.core.module.Module


internal const val SETTINGS_FILE_NAME = "pacer.preferences_pb"

internal expect val settingsStoreModule: Module
