@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
}

compose.resources {
    packageOfResClass = "com.alexgabor.design.riso"
    generateResClass = always
}

kotlin {
    android {
        namespace = "com.alexgabor.design.riso"

        androidResources { enable = true }
    }

    // Everything that is not Android renders through skia, and reaches it through the
    // same `org.jetbrains.skia` API — the desktop JVM, iOS and the browser included. So
    // the shader work those share is written once, in `skikoMain`, rather than
    // duplicated per target. Android stays outside the group because it reaches skia
    // through `android.graphics` instead, which is a different API for the same engine.
    //
    // Every platform bakes the paper's surface into small repeating tiles, once per shape
    // of sheet and density, and most of the time loads a shipped or cached tile instead.
    // Where the bake lands is all that splits skia again: iOS renders it on the GPU
    // through a Metal context of its own (`metalBake`), while the desktop JVM and the
    // browser have no GPU surface they can reach and render it on a raster one
    // (`rasterBake`) — slow, but a tile is small and the bake is rare.
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                withIos()
                withWasmJs()

                // Named rather than left to the template's own `iosMain`, which hangs
                // off `appleMain` and so cannot see `skikoMain` at all.
                group("metalBake") {
                    withIos()
                }

                group("rasterBake") {
                    withJvm()
                    withWasmJs()
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.components.resources)
            api(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // Composing and baking for real needs something to render into, which on the JVM
        // means the skiko runtime for this machine.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
// Regenerates the paper tiles shipped in composeResources, from the bake as it stands. Run after
// anything that changes a tile's pixels, with SURFACE_VERSION bumped. See ShippedTilesTest.
tasks.register<Test>("bakeTiles") {
    group = "riso"
    description = "Regenerates the paper tiles shipped with the library."
    val jvmTest = tasks.named<Test>("jvmTest")
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    workingDir = projectDir
    filter { includeTestsMatching("*ShippedTilesTest") }
    systemProperty("riso.tiles.write", "true")
    testLogging { showStandardStreams = true }
    outputs.upToDateWhen { false }
}
