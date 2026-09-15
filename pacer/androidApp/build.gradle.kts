import com.github.triplet.gradle.androidpublisher.ReleaseStatus

plugins {
    alias(libs.plugins.sidequests.android.app)
    alias(libs.plugins.playPublisher)
}

android {
    namespace = "com.alexgabor.pacer"

    defaultConfig {
        applicationId = "com.alexgabor.pacer"
        versionCode = 2
        versionName = "1.1.0"
    }
}

play {
    // Credentials are read from ANDROID_PUBLISHER_CREDENTIALS, so none are named here. The version
    // code stays a manual bump: a clash with one Play already has fails the upload rather than being
    // resolved behind our back.
    defaultToAppBundles = true
    track = "internal"
    releaseStatus = ReleaseStatus.COMPLETED
}

dependencies {
    implementation(projects.pacer.sharedApp)
    implementation(projects.lib.launch)
    implementation(libs.koin.android)
}
