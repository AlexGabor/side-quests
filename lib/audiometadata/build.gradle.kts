plugins {
    alias(libs.plugins.sidequests.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.lib.audiometadata"
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
