plugins {
    alias(libs.plugins.sidequests.android.app)
}

android {
    namespace = "com.alexgabor.pacer"

    defaultConfig {
        applicationId = "com.alexgabor.pacer"
        versionCode = 2
        versionName = "1.1.0"
    }
}

dependencies {
    implementation(projects.pacer.sharedApp)
    implementation(projects.lib.launch)
    implementation(libs.koin.android)
}
