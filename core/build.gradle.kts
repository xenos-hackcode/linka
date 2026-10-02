import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    // The androidLibrary {} block below counts as custom source-set registration as
    // far as the Kotlin Gradle plugin is concerned, which otherwise silently disables
    // automatic creation of the iosMain/iosTest intermediate source sets - without
    // this, referencing `iosMain` below fails with "KotlinSourceSet with name
    // 'iosMain' not found" (confirmed hitting this live 2026-08-15).
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "com.example.linka.core"
        compileSdk = 34
        minSdk = 24
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        // Registers a JVM-backed host test source set (androidHostTest) that commonTest
        // wires into, so `core`'s tests can run without an emulator/device.
        withHostTestBuilder {}.configure {}
    }

    // Declared even though nothing iOS can be compiled/linked on this Windows dev
    // machine (Apple targets need a macOS host) - Gradle simply skips the Apple
    // compile/link tasks here. One LinkaCore.xcframework bundles all three so Xcode
    // links the same artifact regardless of device vs. simulator vs. Intel simulator.
    val xcf = XCFramework("LinkaCore")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "LinkaCore"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: HttpClient/CoroutineScope/Flow/Json all appear in
            // :core's own public constructors and properties (KtorControlSocketClient,
            // buildControlHttpClient, LinkMessageJson), so consumers like the Android app
            // need these types on their compile classpath too.
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
