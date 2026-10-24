plugins {
    alias(libs.plugins.sidequests.compose.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.lib.documentaccess.api"
    }

    sourceSets {
        commonMain.dependencies {
            // An opened document is a ByteSource, so it goes straight to the metadata parsers.
            api(projects.lib.audiometadata)
            implementation(projects.lib.coroutine.dispatchers)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
