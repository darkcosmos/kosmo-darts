import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.darkcosmos.kosmodarts.capture"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.darkcosmos.kosmodarts.capture"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // No toolchain: compile with the installed JDK and emit bytecode that Android supports.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric UI tests need the merged resources and manifest of the app.
            isIncludeAndroidResources = true
            // Official Robolectric setup for Java 17 and later (https://robolectric.org/getting-started/): lets the
            // Robolectric test JVM reach the OpenJDK internals it needs. Test JVM only; nothing ships in the APK.
            all {
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                )
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Roborazzi screenshots are recorded (never compared) by the debug unit test task that the full build already runs,
// into one stable directory. It is a declared output, so a build cache hit restores the images too.
val screenshotDir: File = layout.buildDirectory.dir("outputs/roborazzi").get().asFile
tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") {
        systemProperty("roborazzi.test.record", "true")
        // Relative to the module directory (the test working directory), so the build cache stays relocatable.
        systemProperty("kosmodarts.screenshot.dir", screenshotDir.relativeTo(projectDir).invariantSeparatorsPath)
        outputs.dir(screenshotDir)
        // Start from an empty directory so that images of removed tests do not linger.
        doFirst { screenshotDir.deleteRecursively() }
    }
}

dependencies {
    implementation(project(":game-engine"))

    constraints {
        // Transitive through camera-video -> media3: imposes the release that fixes GHSA-xxph-c9ww-hj94 (high).
        implementation(libs.guava) {
            because("GHSA-xxph-c9ww-hj94: guava before 33.7.2 is vulnerable; CameraX brings 33.3.1-android")
        }
    }

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    // Compose UI test brings Espresso 3.5.1, which calls an InputManager API removed in recent SDKs.
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
}
