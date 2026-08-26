plugins {
    alias(libs.plugins.sidequests.desktop.app)
}

dependencies {
    implementation(projects.design.riso)
    implementation(projects.pacer.shared)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.alexgabor.recorder.MainKt"

        // The repository root the recordings are written into, and where their frames are staged.
        // Passed in rather than resolved from the working directory, which is not the same on every
        // way of invoking this.
        args(
            rootProject.layout.projectDirectory.asFile.absolutePath,
            layout.buildDirectory.dir("recordings").get().asFile.absolutePath,
        )

        // `-Ptakes=pacer` re-records one of them; every take runs without it.
        providers.gradleProperty("takes").orNull?.let { args(it) }
    }
}
