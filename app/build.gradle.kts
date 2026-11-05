plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "cafe.miafetta.tsuki"
    compileSdk = 37

    defaultConfig {
        applicationId = "cafe.miafetta.tsuki"
        minSdk = 33
        targetSdk = 37
        versionCode = 80
        versionName = "2.0.0"
        manifestPlaceholders["debugToolsEnabled"] = "false"
        testInstrumentationRunner = if (providers.gradleProperty("releaseProbe").isPresent)
            "cafe.miafetta.tsuki.widget.ReleasePerformanceInstrumentation"
        else "cafe.miafetta.tsuki.widget.WidgetRenderInstrumentation"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Xiaomi caches native layouts across upgrades; resource IDs must keep their meaning.
        additionalParameters += listOf("--stable-ids", file("widget-resource-ids.txt").absolutePath)
    }

    buildTypes {
        getByName("debug") { manifestPlaceholders["debugToolsEnabled"] = "true" }
        getByName("release") {
            isMinifyEnabled = true
            // shortcut: resource shrinking breaks the HyperOS 4 launcher; enable only after real-host validation.
            //noinspection NotShrinkingResources
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.all {
            it.systemProperty("tsuki.projectDir", project.projectDir.absolutePath)
            // XML contract tests must rerun when their resource inputs change.
            it.inputs.files(fileTree("src/main/res"), file("src/main/AndroidManifest.xml"), file("build.gradle.kts"),
                file("widget-resource-ids.txt"))
        }
    }

    lint {
        warningsAsErrors = true
    }
}

tasks.matching { it.name.startsWith("process") && it.name.endsWith("Resources") }.configureEach {
    inputs.file("widget-resource-ids.txt")
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-nav:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-squircle:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    // Debug previews only. Tooling must match the resolved Compose UI version; upgrade both together after device
    // verification and then drop the two GradleDependency suppressions.
    //noinspection GradleDependency
    debugImplementation("androidx.compose.ui:ui-tooling-preview:1.12.0")
    //noinspection GradleDependency
    debugImplementation("androidx.compose.ui:ui-tooling:1.12.0") {
        // Miuix previews do not use tooling's optional Material3 adapter.
        exclude(group = "androidx.compose.material3", module = "material3")
        exclude(group = "androidx.compose.material3", module = "material3-android")
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}

