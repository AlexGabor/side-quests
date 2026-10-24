package com.alexgabor.sidequests.common

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/**
 * A Kotlin Multiplatform library for Android, iOS and the JVM, and for the browser when [web] is set.
 *
 * The JVM target is there either way: it is what a desktop app runs on, and where an app that only
 * ships on phones still runs its unit tests fast.
 */
internal fun Project.configureMultiplatformLibrary(web: Boolean) {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    apply(plugin = "com.android.kotlin.multiplatform.library")

    extensions.configure(KotlinMultiplatformExtension::class.java) {

        // Android target
        this.extensions.configure(KotlinMultiplatformAndroidLibraryTarget::class.java) {
            compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
            minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
        }

        jvm()

        iosArm64()
        iosSimulatorArm64()

        if (web) {
            wasmJs { browser() }
        }
    }

    tasks.withType(KotlinCompilationTask::class.java).configureEach {
        compilerOptions {
            if (this is KotlinJvmCompilerOptions) {
                jvmTarget.set(JvmTarget.fromTarget("17"))
            }
        }
    }
}

/** Compose on top of a library already set up by [configureMultiplatformLibrary]. */
internal fun Project.configureComposeLibrary() {
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
