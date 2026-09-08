plugins {
    alias(libs.plugins.sidequests.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.pacer.core.settings.impl"
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            api(projects.pacer.core.settings.api)
            api(libs.koin.core)
            implementation(libs.androidx.datastore.preferencesCore)
            implementation(libs.okio)
        }
        // `WebOpfsStorage` is the one storage the preferences factory will not build for you: its
        // `createWithPath` hardcodes session storage on the web, which is emptied when the tab
        // closes. Naming it here is what puts settings on the Origin Private File System instead.
        wasmJsMain.dependencies {
            implementation(libs.androidx.datastore.coreOkio)
        }
    }
}
