plugins {
    alias(libs.plugins.sidequests.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.lib.appstateurl.test"
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.lib.appstateurl.api)
            implementation(libs.koin.core)
        }
    }
}
