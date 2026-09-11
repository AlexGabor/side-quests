package com.alexgabor.sidequests

import com.alexgabor.sidequests.common.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension


abstract class ComposeMultiplatformLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "sidequests.multiplatform.library")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")
            apply(plugin = "org.jetbrains.compose")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    implementation(libs.findLibrary("compose-runtime").get())
                    implementation(libs.findLibrary("compose-foundation").get())
                    implementation(libs.findLibrary("compose-ui").get())
                    implementation(libs.findLibrary("compose-uiToolingPreview").get())
                }
                sourceSets.commonTest.dependencies {
                    implementation(libs.findLibrary("compose-uiTest").get())
                }
            }

            dependencies {
                "androidRuntimeClasspath"(libs.findLibrary("compose-uiTooling").get())
            }
        }
    }
}
