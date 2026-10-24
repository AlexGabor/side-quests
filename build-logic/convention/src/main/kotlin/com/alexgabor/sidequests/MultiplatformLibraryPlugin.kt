package com.alexgabor.sidequests

import com.alexgabor.sidequests.common.configureMultiplatformLibrary
import org.gradle.api.Plugin
import org.gradle.api.Project


/** Android, iOS, JVM and the browser. See [MobileLibraryPlugin] for a library without the browser. */
abstract class MultiplatformLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureMultiplatformLibrary(web = true)
    }
}
