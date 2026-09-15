plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.telegram.messenger"
    compileSdk = 35

    defaultConfig {
        minSdk = 28

        // Mirrors upstream Telegram BuildConfig fields actually read by ported
        // helpers (Utilities, SharedConfig, etc.). Ported code stays unchanged
        // because the namespace matches upstream, so BuildConfig lands in the
        // same package as those helpers.
        buildConfigField(
            "boolean",
            "BUILD_HOST_IS_WINDOWS",
            System.getProperty("os.name").lowercase().contains("windows").toString(),
        )
        buildConfigField("boolean", "DEBUG_VERSION", "false")
        buildConfigField("boolean", "DEBUG_PRIVATE_VERSION", "false")
        buildConfigField("boolean", "BUNDLE", "false")
        buildConfigField("String", "BUILD_VERSION_STRING", "\"0.1.0\"")
        buildConfigField("String", "APP_CENTER_HASH", "\"\"")
        buildConfigField("int", "VERSION_NUM", "0")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "5000"))
    }
}

dependencies {
    // Telegram ported helpers use AndroidX widgets, animations, etc.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.browser)
    implementation(libs.jsoup)
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    testImplementation(libs.junit4)
    testImplementation("org.json:json:20240303")
}
