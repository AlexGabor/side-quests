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
    // Within skia they then split again, on one question: whether the paper's surface
    // can be baked into a texture once instead of computed for every pixel of every
    // frame. It costs about a hundred noise reads per pixel, so where it can be baked
    // it must be — only the browser cannot, having no offscreen surface to bake into
    // that is not a CPU raster, and it pays per frame in `inlinePaperMain` instead.
    //
    // How the bake is drawn then differs once more, which is all `metalBake` carries:
    // iOS renders it on the GPU through a Metal context of its own, while the desktop
    // JVM has no reachable GPU surface and falls back to a raster one. That is slow
    // enough to need the settle-and-stand-in dance in `bakedPaperMain`, but it happens
    // once per layout rather than once per frame.
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                withIos()
                withWasmJs()

                // Named rather than left to the template's own `iosMain`, which hangs
                // off `appleMain` and so cannot see `skikoMain` at all.
                group("bakedPaper") {
                    withJvm()
                    withIos()

                    group("metalBake") {
                        withIos()
                    }
                }

                group("inlinePaper") {
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
            implementation(libs.compose.uiToolingPreview)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}