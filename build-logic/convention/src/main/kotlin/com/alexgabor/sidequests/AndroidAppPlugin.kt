package com.alexgabor.sidequests

import com.alexgabor.sidequests.common.configureAndroidCompose
import com.alexgabor.sidequests.common.configureKotlinAndroid
import com.alexgabor.sidequests.common.libs
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import java.io.File
import java.io.StringReader
import java.util.Properties


abstract class AndroidAppPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")

            val extension = extensions.getByType<ApplicationExtension>()
            extension.defaultConfig.targetSdk = libs.findVersion("android-targetSdk")
                .get().requiredVersion.toInt()
            configureAndroidCompose(extension)
            configureKotlinAndroid(extension)
            configureBuildTypes(extension)
            configureReleaseSigning(extension)
        }
    }
}

private fun configureBuildTypes(applicationExtension: ApplicationExtension) {
    applicationExtension.apply {
        buildTypes {
            getByName("debug") {
                applicationIdSuffix = ".debug"
                isDebuggable = true
                isMinifyEnabled = false
            }

            getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true

                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
            }
        }
    }
}

private fun Project.configureReleaseSigning(applicationExtension: ApplicationExtension) {
    val credentials = releaseSigningCredentials()

    if (credentials == null) {
        warnReleaseWillBeUnsigned()
        return
    }

    applicationExtension.apply {
        // `signingConfigs` is declared as `NamedDomainObjectContainer<out ApkSigningConfig>`, and
        // the out-projection rules out the configuring overload of `create` — hence the `apply`.
        val release = signingConfigs.create("release").apply {
            storeFile = credentials.storeFile
            storePassword = credentials.storePassword
            keyAlias = credentials.keyAlias
            keyPassword = credentials.keyPassword
        }

        // Debug keeps the default debug keystore: it is what makes a debug build installable
        // alongside the release one without either party knowing anything about the other.
        buildTypes.getByName("release").signingConfig = release
    }
}

/**
 * Says so, once, when a release artifact is about to be packaged without a key.
 *
 * Hung off the packaging tasks rather than logged while configuring, for two reasons: only the app
 * actually being built should say anything — configuration runs for all of them — and a warning
 * emitted during configuration is not replayed on a configuration cache hit, which is to say it
 * would go quiet on every run after the first, having warned exactly once about a problem that has
 * not gone anywhere.
 */
private fun Project.warnReleaseWillBeUnsigned() {
    val message = "$path: no signing credentials found, this release build is UNSIGNED and Play " +
            "will reject it. Add keys/keystore.properties or set SIDEQUESTS_KEYSTORE_FILE, " +
            "SIDEQUESTS_KEYSTORE_PASSWORD, SIDEQUESTS_KEY_ALIAS and SIDEQUESTS_KEY_PASSWORD."

    tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }
        .configureEach { doFirst { logger.lifecycle(message) } }
}

private class SigningCredentials(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

/**
 * Resolves the upload key's credentials from `keys/keystore.properties`.
 */
@Suppress("UnstableApiUsage")
private fun Project.releaseSigningCredentials(): SigningCredentials? {
    val keysDir = isolated.rootProject.projectDirectory.dir("keys")

    val properties = providers.fileContents(keysDir.file("keystore.properties")).asText.orNull
        ?.let { text -> Properties().apply { load(StringReader(text)) } }

    fun value(propertyKey: String, environmentKey: String): String? =
        properties?.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
            ?: providers.environmentVariable(environmentKey).orNull?.takeIf { it.isNotBlank() }

    val storePath = value("storeFile", "SIDEQUESTS_KEYSTORE_FILE") ?: return null
    val storePassword = value("storePassword", "SIDEQUESTS_KEYSTORE_PASSWORD") ?: return null
    val keyAlias = value("keyAlias", "SIDEQUESTS_KEY_ALIAS") ?: return null
    val keyPassword = value("keyPassword", "SIDEQUESTS_KEY_PASSWORD") ?: return null

    // Relative to `keys/`, so the usual entry is a bare filename; an absolute path still works for
    // a keystore kept outside the repo entirely.
    val storeFile = File(storePath).takeIf { it.isAbsolute } ?: keysDir.file(storePath).asFile

    if (!storeFile.exists()) {
        // Credentials that name a keystore which is not there is a mistake to report, not a reason
        // to fall back to an unsigned build and let Play be the one to complain.
        throw GradleException(
            "Signing credentials point at a keystore that does not exist: ${storeFile.absolutePath}"
        )
    }

    return SigningCredentials(storeFile, storePassword, keyAlias, keyPassword)
}

internal fun Project.configurePackaging(
    applicationExtension: ApplicationExtension,
) {
    applicationExtension.apply {
        packaging {
            resources {
                excludes.add("META-INF/AL2.0")
                excludes.add("META-INF/LGPL2.1")
            }
        }
    }
}