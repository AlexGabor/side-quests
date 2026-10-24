import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.sidequests.compose.mobile.library)
}

kotlin {
    android {
        namespace = "com.alexgabor.design.riso.demo.shared"
    }

    // Static, so the design system it depends on is linked in rather than needing a second
    // framework alongside it — Swift only ever calls `MainViewController()`.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "RisoDemoShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.design.riso)
        }
    }
}
