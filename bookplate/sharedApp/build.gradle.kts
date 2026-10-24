import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.sidequests.compose.mobile.library)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {

    android {
        namespace = "com.alexgabor.bookplate.sharedApp"
    }

    // Static, so the design system it depends on is linked in rather than needing a second
    // framework alongside it — Swift only ever calls `MainViewController()`.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "BookplateShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.design.riso)
            implementation(projects.design.navigation)
            implementation(projects.bookplate.core.library.impl)
            implementation(projects.bookplate.feature.home)
            implementation(projects.lib.appstateurl.impl)
            implementation(projects.lib.documentaccess.impl)
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(projects.bookplate.core.library.test)
            implementation(projects.lib.documentaccess.test)
            implementation(libs.kotlin.test)
            // Only to prove the navigation keys survive a serialization round trip; the app itself
            // persists them through savedstate, not json.
            implementation(libs.kotlinx.serialization.json)
        }

        // Composing for real needs something to render into, which on the JVM means the skiko
        // build for whichever machine is running the tests.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
