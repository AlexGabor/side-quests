plugins {
    alias(libs.plugins.sidequests.android.app)
}

android {
    namespace = "com.alexgabor.stamp"

    defaultConfig {
        applicationId = "com.alexgabor.stamp"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(projects.stamp.shared)
}
