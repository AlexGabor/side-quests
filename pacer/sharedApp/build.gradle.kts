import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    
    android {
       namespace = "com.alexgabor.pacer.sharedApp"
    }

    // The iOS targets themselves come from the convention plugin; only the binary they produce is
    // this module's business. Static, so the design system it depends on is linked in rather than
    // needing a second framework alongside it — Swift only ever calls `MainViewController()`.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "PacerShared"
            isStatic = true
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(projects.pacer.core.settings.impl)
            implementation(projects.pacer.feature.home)
            implementation(projects.pacer.feature.settings)
            implementation(projects.design.riso)
            implementation(projects.design.navigation)
            api(projects.lib.launch)
            implementation(projects.lib.extension.compose)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Only to prove the navigation keys survive a serialization round trip; the app itself
            // persists them through savedstate, not json.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // Composing for real needs something to render into, which on the JVM means the skiko
        // build for whichever machine is running the tests.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}