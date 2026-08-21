import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.sidequests.compose.multiplatform.library)
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}