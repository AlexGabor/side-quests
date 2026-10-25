plugins {
    alias(libs.plugins.sidequests.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.bookplate.core.player.api"
    }

    sourceSets {
        commonMain.dependencies {
            // Books are identified and chaptered the way the library stores them.
            api(projects.bookplate.core.library.api)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
