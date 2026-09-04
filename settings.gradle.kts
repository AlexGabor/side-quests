rootProject.name = "SideQuests"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Not FAIL_ON_PROJECT_REPOS: the wasm target builds through webpack, and Kotlin fetches its own
    // Node and Yarn by adding repositories from the project side. Declaring the same ones here does
    // not satisfy the strict mode — it objects to the plugin adding any at all — and the alternative,
    // pointing the build at a Node already on the machine, would make it depend on whichever version
    // happens to be installed. Settings repositories still win for everything else.
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()

        // Where Kotlin's own Node and Yarn come from. With settings repositories taking precedence
        // the plugin's own declarations are ignored, so these have to be spelled out here or the
        // toolchain is looked for on Maven Central and not found. Both are narrowed to the single
        // module they serve.
        ivy("https://nodejs.org/dist") {
            name = "Node.js distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

include(":pacer:androidApp")
include(":pacer:desktopApp")
include(":pacer:shared")
include(":pacer:webApp")
include(":stamp:androidApp")
include(":stamp:shared")
include(":design:riso")
include(":design:risoDemo")
include(":design:risoRecorder")
include(":design:navigation")
