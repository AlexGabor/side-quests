plugins {
    alias(libs.plugins.sidequests.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.lib.appstateurl.api"
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.lib.launch)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
