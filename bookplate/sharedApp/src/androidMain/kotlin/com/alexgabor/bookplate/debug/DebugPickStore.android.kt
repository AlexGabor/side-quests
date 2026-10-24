package com.alexgabor.bookplate.debug

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

// TEMPORARY (M7 debug entry): removed in M9.
internal actual val debugPickStoreModule: Module = module {
    single<DebugPickStore> {
        DataStoreDebugPickStore(
            PreferenceDataStoreFactory.createWithPath {
                File(androidContext().filesDir, "datastore/$DEBUG_PICKS_FILE").absolutePath.toPath()
            },
        )
    }
}
