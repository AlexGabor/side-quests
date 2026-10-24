plugins {
    alias(libs.plugins.sidequests.android.app)
}

android {
    namespace = "com.alexgabor.bookplate"

    defaultConfig {
        applicationId = "com.alexgabor.bookplate"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(projects.bookplate.sharedApp)
    implementation(libs.koin.android)
}
