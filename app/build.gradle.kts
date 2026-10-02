import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing (2026-09-13) - real credentials live in local.properties
// (gitignored, same as sdk.dir), never hardcoded here. Falls back to null
// (no release signing config applied) if that file or these keys are
// missing, e.g. on a fresh checkout that hasn't set up its own keystore
// yet - a debug build still works either way, only `assembleRelease`
// actually needs this.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    // namespace stays as-is (com.example.linka) - it has to match the real
    // `package` declarations across every source file (com/example/linka,
    // plus com/example/xenoscontroller and com/example/phonelinkagent used
    // internally too), and changing it would mean renaming every one of
    // those files/directories for no real benefit. applicationId is the
    // actual public-facing identity (Play Store/APKPure listing, what
    // shows in Settings > Apps) and is completely independent of that -
    // this is the one that actually matters for distribution.
    namespace = "com.example.linka"
    compileSdk = 34

    defaultConfig {
        applicationId = "xenos.linka"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            val storeFileName = localProps.getProperty("RELEASE_STORE_FILE")
            if (storeFileName != null) {
                storeFile = rootProject.file(storeFileName)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
                // AGP skips v1 (JAR) signing by default once minSdk >= 24, since v2
                // alone covers every supported OS version - but APKPure's own upload
                // verification rejected a v2-only APK ("signature doesn't match")
                // even on a genuinely first-ever upload, so its pipeline apparently
                // still expects v1 present. Forcing all three costs nothing and
                // maximizes compatibility with whatever a given store's own checker
                // understands.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (localProps.getProperty("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    buildToolsVersion = "34.0.0"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core"))

    // For the app's own backend client (accounts, requests, global chat, groups - see
    // server/) - separate from :core's KtorControlSocketClient, which is a different
    // protocol (binary screen relay) with its own client setup. ktor-client-core comes
    // in transitively via :core's api dependency.
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    // Full icon set (Link/RequestPage/Public/Groups/Settings aren't in the small
    // default-bundled Icons.Default set) - version comes from the BOM above.
    implementation("androidx.compose.material:material-icons-extended")

    // Controller role needs both: scanning (embedded) to link to an existing agent, and
    // core (via QrCodeGenerator) to show this device's own pairing code as a controllee.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
