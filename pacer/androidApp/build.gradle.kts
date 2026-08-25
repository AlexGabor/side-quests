plugins {
    alias(libs.plugins.sidequests.android.app)
}

android {
    namespace = "com.alexgabor.pacer"

    defaultConfig {
        applicationId = "com.alexgabor.pacer"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(projects.pacer.shared)
}
