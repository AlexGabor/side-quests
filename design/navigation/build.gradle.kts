plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
}


kotlin {
    android {
        namespace = "com.alexgabor.design.navigation"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.navigation3.ui)
            implementation(libs.compose.uiToolingPreview)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}