plugins {
    alias(libs.plugins.sidequests.compose.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.bookplate.feature.home"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.design.riso)
            api(projects.bookplate.core.library.api)
            api(projects.bookplate.core.player.api)
            api(projects.lib.documentaccess.api)
            implementation(projects.lib.coroutine.dispatchers)
            implementation(libs.koin.compose)
            // Covers are files in app storage: read with okio, decoded by Compose resources.
            implementation(libs.okio)
            implementation(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(projects.bookplate.core.library.test)
            implementation(projects.bookplate.core.player.test)
            implementation(projects.lib.documentaccess.test)
            implementation(projects.lib.coroutine.test)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Composing for real needs something to render into, which on the JVM means the skiko
        // build for whichever machine is running the tests.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
