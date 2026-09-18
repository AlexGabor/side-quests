plugins {
    alias(libs.plugins.sidequests.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.lib.share.impl"
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            api(projects.lib.share.api)
            api(libs.koin.core)
        }
    }
}
