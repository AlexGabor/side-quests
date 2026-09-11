plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.pacer.feature.settings"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.pacer.core.settings.api)
            implementation(projects.design.riso)
            implementation(projects.lib.extension.compose)
            implementation(libs.koin.compose)

            // Normaly this should be a test dependency, but it is needed in previews. It will be shrinked away.
            implementation(projects.pacer.core.settings.test)
        }
    }
}
