plugins {
    alias(libs.plugins.sidequests.web.app)
}

kotlin {
    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.pacer.shared)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
