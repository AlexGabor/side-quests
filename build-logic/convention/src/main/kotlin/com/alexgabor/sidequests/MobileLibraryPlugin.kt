package com.alexgabor.sidequests

import com.alexgabor.sidequests.common.configureMultiplatformLibrary
import org.gradle.api.Plugin
import org.gradle.api.Project


/**
 * For apps that only ship on phones: Android and iOS, plus the JVM so tests run as plain unit tests.
 * Nothing is built for the browser.
 */
abstract class MobileLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureMultiplatformLibrary(web = false)
    }
}
