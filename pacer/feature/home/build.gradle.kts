plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.pacer.feature.home"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.design.riso)
            api(projects.lib.appstateurl.api)
            implementation(libs.koin.compose)

            // Normaly this should be a test dependency, but it is needed in previews. It will be shrinked away.
            implementation(projects.lib.appstateurl.test)
        }
        commonTest.dependencies {
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
