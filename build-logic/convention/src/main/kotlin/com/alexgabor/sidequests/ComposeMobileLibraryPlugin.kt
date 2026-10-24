package com.alexgabor.sidequests

import com.alexgabor.sidequests.common.configureComposeLibrary
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply


/** [MobileLibraryPlugin] with Compose. */
abstract class ComposeMobileLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "sidequests.mobile.library")
            configureComposeLibrary()
        }
    }
}
