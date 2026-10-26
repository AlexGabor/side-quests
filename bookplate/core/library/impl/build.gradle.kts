plugins {
    alias(libs.plugins.sidequests.mobile.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "com.alexgabor.bookplate.core.library.impl"
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            api(projects.bookplate.core.library.api)
            api(libs.koin.core)
            implementation(projects.lib.audiometadata)
            implementation(projects.lib.coroutine.dispatchers)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(projects.lib.coroutine.test)
            implementation(projects.lib.documentaccess.test)
            implementation(libs.kotlin.test)
        }
        // The lib/audiometadata fixtures, read in place rather than copied.
        jvmTest {
            dependencies {
                // Reads the exported schema to build a version 1 database for the migration test.
                implementation(libs.kotlinx.serialization.json)
            }
            resources.srcDir(rootProject.file("lib/audiometadata/src/jvmTest/resources"))
        }
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, libs.room.compiler)
    }
}
