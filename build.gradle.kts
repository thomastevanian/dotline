// AGP 9 has built-in Kotlin, so the kotlin-android plugin is NOT applied anywhere.
// AGP bundles an older Kotlin Gradle plugin; pin the newer one explicitly (keep in sync
// with `kotlin` in gradle/libs.versions.toml, which drives the Compose compiler plugin).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
