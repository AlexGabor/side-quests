plugins {
    alias(libs.plugins.sidequests.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.pacer.core.settings.test"
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.pacer.core.settings.api)
            implementation(libs.koin.core)
        }
    }
}
