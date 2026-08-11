plugins {
    alias(libs.plugins.sidequests.android.app)
}

android {
    namespace = "com.alexgabor.design.riso.demo"

    defaultConfig {
        applicationId = "com.alexgabor.design.riso.demo"
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(projects.design.riso)
}
