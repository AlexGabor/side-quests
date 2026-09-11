plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
    alias(libs.plugins.kotlinSerialization)
}


kotlin {
    android {
        namespace = "com.alexgabor.design.navigation"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.navigation3.ui)
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // Composing for real needs something to render into, which on the JVM means the skiko
        // build for whichever machine is running the tests.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}