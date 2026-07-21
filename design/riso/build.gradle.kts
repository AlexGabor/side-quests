plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
}

compose.resources {
    packageOfResClass = "com.alexgabor.design.riso"
    generateResClass = always
}

kotlin {
    android {
        namespace = "com.alexgabor.design.riso"

        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            api(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.uiToolingPreview)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}