plugins {
    alias(libs.plugins.sidequests.web.app)
}

kotlin {
    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.pacer.shared)
            implementation(projects.lib.launch)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
