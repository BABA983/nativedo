plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.paparazzi) apply false
}

tasks.register("runUnitAndScreenshotTests") {
    group = "verification"
    description = "Runs app unit tests and verifies Paparazzi golden images."
    dependsOn(":app:verifyPaparazziDebug")
}
