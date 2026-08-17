import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.sidequests.desktop.app)
}

dependencies {
    implementation(projects.pacer.shared)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.alexgabor.pacer.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Pacer"
            packageVersion = "1.0.0"
        }
    }
}
