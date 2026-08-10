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

    buildTypes {
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
        }
    }
}

dependencies {
    implementation(projects.pacer.shared)
    // For the debug-only launcher icon bake: :pacer:shared keeps riso as an implementation
    // dependency, so the press is not on this module's classpath otherwise.
    implementation(projects.design.riso)
}
