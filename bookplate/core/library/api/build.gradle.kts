plugins {
    alias(libs.plugins.sidequests.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.bookplate.core.library.api"
    }

    sourceSets {
        commonMain.dependencies {
            // A book keeps the reference the user picked, and import starts from a pick.
            api(projects.lib.documentaccess.api)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
