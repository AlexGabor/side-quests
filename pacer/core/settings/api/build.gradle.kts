plugins {
    alias(libs.plugins.sidequests.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.pacer.core.settings.api"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
    }
}
