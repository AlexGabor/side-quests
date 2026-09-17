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
            implementation(projects.lib.coroutine.dispatchers)
            implementation(libs.androidx.datastore.preferencesCore)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(projects.lib.coroutine.test)
            implementation(libs.kotlin.test)
        }
        wasmJsMain.dependencies {
            implementation(libs.androidx.datastore.coreOkio)
        }
    }
}
