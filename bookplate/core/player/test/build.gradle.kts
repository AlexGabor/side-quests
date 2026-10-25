plugins {
    alias(libs.plugins.sidequests.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.bookplate.core.player.test"
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.bookplate.core.player.api)
            implementation(projects.lib.coroutine.dispatchers)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.bookplate.core.library.test)
            implementation(projects.lib.coroutine.test)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
