plugins {
    alias(libs.plugins.sidequests.desktop.app)
}

dependencies {
    implementation(projects.design.riso)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.alexgabor.design.riso.recorder.MainKt"

        // Where the recordings land, and where their frames are staged. Passed in rather than
        // resolved from the working directory, which is not the same on every way of invoking this.
        args(
            rootProject.layout.projectDirectory.dir("design/riso/docs").asFile.absolutePath,
            layout.buildDirectory.dir("recordings").get().asFile.absolutePath,
        )
    }
}
