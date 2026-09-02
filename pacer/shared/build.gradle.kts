import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    
    android {
       namespace = "com.alexgabor.pacer.shared"
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
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        commonMain.dependencies {
            implementation(projects.design.riso)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.androidx.datastore.preferencesCore)
            implementation(libs.okio)
        }
        // `WebOpfsStorage` is the one storage the preferences factory will not build for you: its
        // `createWithPath` hardcodes session storage on the web, which is emptied when the tab
        // closes. Naming it here is what puts settings on the Origin Private File System instead.
        wasmJsMain.dependencies {
            implementation(libs.androidx.datastore.coreOkio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.uiTest)
        }
        // Composing a slider for real needs something to render into, which on the JVM means the
        // skiko build for whichever machine is running the tests.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}