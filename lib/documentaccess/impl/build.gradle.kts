plugins {
    alias(libs.plugins.sidequests.compose.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.lib.documentaccess.impl"
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            api(projects.lib.documentaccess.api)
            api(libs.koin.core)
            implementation(projects.lib.coroutine.dispatchers)
        }
    }
}
