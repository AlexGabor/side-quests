package com.alexgabor.sidequests

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension


abstract class WebAppPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")
            apply(plugin = "org.jetbrains.compose")

            extensions.configure(KotlinMultiplatformExtension::class.java) {
                // wasmJs only. Compose treats it as the primary web target and the Kotlin/JS IR
                // target is markedly slower — which tells most against exactly the shader work this
                // app is made of.
                wasmJs {
                    outputModuleName.set(project.name)
                    browser()
                    binaries.executable()
                }
            }
        }
    }
}
