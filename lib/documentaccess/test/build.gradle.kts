plugins {
    alias(libs.plugins.sidequests.compose.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.lib.documentaccess.test"
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.lib.documentaccess.api)
            implementation(projects.lib.coroutine.dispatchers)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.lib.coroutine.test)
            implementation(libs.kotlin.test)
        }
        // The picker test composes, which on the JVM needs the skiko build for this machine.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
