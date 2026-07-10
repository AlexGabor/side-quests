package com.alexgabor.sidequests.common


import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Configure Compose-specific options
 */
internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        buildFeatures.apply {
            compose = true
        }

        dependencies {
            "implementation"(libs.findLibrary("androidx-activity-compose").get())
            "implementation"(libs.findLibrary("compose-uiToolingPreview").get())
            "debugImplementation"(libs.findLibrary("compose-uiTooling").get())
        }
    }
}