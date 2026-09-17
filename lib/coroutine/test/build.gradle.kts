plugins {
    alias(libs.plugins.sidequests.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.lib.coroutine.test"
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.lib.coroutine.dispatchers)
            api(libs.kotlinx.coroutines.test)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
