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

// Still snapshots of one test page, for comparing the print before and after a change. See Snapshots.kt.
tasks.register<JavaExec>("snapshots") {
    group = "riso"
    mainClass = "com.alexgabor.design.riso.recorder.SnapshotsKt"
    classpath = sourceSets.main.get().runtimeClasspath
    args(layout.buildDirectory.dir("snapshots").get().asFile.absolutePath)
}
