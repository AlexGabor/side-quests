import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.alexgabor.sidequests.buildlogic"

// Configure the build-logic plugins to target JDK 17
// This matches the JDK used to build the project, and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

dependencies {
    compileOnly(libs.android.tools.common)
    compileOnly(libs.android.tools.gradlePluginApi)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    compileOnly(libs.kotlin.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.jvm.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("AndroidAppPlugin") {
            id = libs.plugins.sidequests.android.app.get().pluginId
            implementationClass = "com.alexgabor.sidequests.AndroidAppPlugin"
        }
        register("ComposeMultiplatformLibraryPlugin") {
            id = libs.plugins.sidequests.compose.multiplatform.library.get().pluginId
            implementationClass = "com.alexgabor.sidequests.ComposeMultiplatformLibraryPlugin"
        }
    }
}