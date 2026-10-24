package com.alexgabor.bookplate.debug

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

// TEMPORARY (M7 debug entry): removed in M9.
internal actual val debugPickStoreModule: Module = module {
    single<DebugPickStore> {
        DataStoreDebugPickStore(
            PreferenceDataStoreFactory.createWithPath {
                val directory = NSSearchPathForDirectoriesInDomains(
                    directory = NSApplicationSupportDirectory,
                    domainMask = NSUserDomainMask,
                    expandTilde = true,
                ).first() as String
                "$directory/$DEBUG_PICKS_FILE".toPath()
            },
        )
    }
}
