plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.lib.extension.compose"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.uiToolingPreview)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}