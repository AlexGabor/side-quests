plugins {
    alias(libs.plugins.sidequests.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.bookplate.core.library.test"
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.bookplate.core.library.api)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.lib.coroutine.test)
            implementation(libs.kotlin.test)
        }
    }
}
