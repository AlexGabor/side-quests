package com.alexgabor.sidequests

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import com.alexgabor.sidequests.common.libs
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask


abstract class ComposeMultiplatformLibraryPlugin : Plugin<Project> {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
            apply(plugin = "com.android.kotlin.multiplatform.library")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")
            apply(plugin = "org.jetbrains.compose")

            extensions.configure(KotlinMultiplatformExtension::class.java) {

                // Android target
                this.extensions.configure(KotlinMultiplatformAndroidLibraryTarget::class.java) {
                    compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
                    minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
                }

                jvm()

                iosArm64()
                iosSimulatorArm64()

                wasmJs { browser() }
            }

            tasks.withType(KotlinCompilationTask::class.java).configureEach {
                compilerOptions {
                    if (this is KotlinJvmCompilerOptions) {
                        jvmTarget.set(JvmTarget.fromTarget("17"))
                    }
                }
            }
        }
    }
}
