import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
}

val buildRevision = providers.environmentVariable("GITHUB_SHA")
    .orElse(providers.exec {
        commandLine("git", "rev-parse", "--short=12", "HEAD")
    }.standardOutput.asText.map { it.trim() })
    .get()
    .take(12)

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.miuix.ui)
            implementation(libs.miuix.preference)
            implementation(libs.miuix.icons)
            implementation(libs.miuix.blur)
            implementation(libs.miuix.squircle)
            implementation(libs.androidx.lifecycle.runtime)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.refreshrate.control"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.refreshrate.control"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
        buildConfigField("String", "BUILD_REVISION", "\"$buildRevision\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
