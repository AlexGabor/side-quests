plugins {
    alias(libs.plugins.sidequests.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.lib.share.test"
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.lib.share.api)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
