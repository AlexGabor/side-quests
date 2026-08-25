package com.alexgabor.sidequests

import com.alexgabor.sidequests.common.configureAndroidCompose
import com.alexgabor.sidequests.common.configureKotlinAndroid
import com.alexgabor.sidequests.common.libs
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import kotlin.apply


abstract class AndroidAppPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")

            val extension = extensions.getByType<ApplicationExtension>()
            extension.defaultConfig.targetSdk = libs.findVersion("android-targetSdk")
                .get().requiredVersion.toInt()
            configureAndroidCompose(extension)
            configureKotlinAndroid(extension)
            configureBuildTypes(extension)
        }
    }
}

private fun configureBuildTypes(applicationExtension: ApplicationExtension) {
    applicationExtension.apply {
        buildTypes {
            getByName("debug") {
                applicationIdSuffix = ".debug"
                isDebuggable = true
                isMinifyEnabled = false
            }

            getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true

                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
            }
        }
    }
}

internal fun Project.configurePackaging(
    applicationExtension: ApplicationExtension,
) {
    applicationExtension.apply {
        packaging {
            resources {
                excludes.add("META-INF/AL2.0")
                excludes.add("META-INF/LGPL2.1")
            }
        }
    }
}