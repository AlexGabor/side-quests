plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
}

kotlin {

    android {
       namespace = "com.alexgabor.stamp.shared"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.design.riso)
        }
    }
}
