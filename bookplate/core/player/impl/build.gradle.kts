plugins {
    alias(libs.plugins.sidequests.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.bookplate.core.player.impl"
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.session)
            implementation(libs.androidx.lifecycle.process)
        }
        commonMain.dependencies {
            api(projects.bookplate.core.player.api)
            api(projects.lib.documentaccess.api)
            api(libs.koin.core)
            implementation(projects.lib.coroutine.dispatchers)
        }
        commonTest.dependencies {
            implementation(projects.bookplate.core.library.test)
            implementation(projects.lib.coroutine.test)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
