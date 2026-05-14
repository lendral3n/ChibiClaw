plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

android {
    namespace = "com.chibiclaw"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chibiclaw"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "4.0.0-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("STORE_PASSWORD").orEmpty()
                keyAlias = System.getenv("KEY_ALIAS").orEmpty()
                keyPassword = System.getenv("KEY_PASSWORD").orEmpty()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true   // Phase 3: Shizuku UserService AIDL
    }

    // Phase 9: Room schema export — schemas/ dir di-commit untuk migration history.
    // Skip androidTest asset wiring (test framework Phase 9 manual saja);
    // ksp arg `room.schemaLocation` di atas yang generate file json.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
            freeCompilerArgs.addAll("-Xannotation-default-target=param-property")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/NOTICE.txt",
                "META-INF/LICENSE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module"
            )
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.work.hilt)
    ksp(libs.work.hilt.compiler)

    // Room + SQLCipher
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // EncryptedSharedPreferences
    implementation(libs.security.crypto)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization + datetime
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Logging
    implementation(libs.timber)

    // Phase 2: OkHttp untuk ElevenLabs TTS streaming
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Phase 3: Shizuku (ADB-level privileged exec)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Phase 1: LiteRT-LM (Gemma local) + ONNX Runtime (embedding)
    // Pinned 0.11.0 stable (Maven Central latest per 2026-05-04).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")
    implementation(libs.onnxruntime.android)

    // Phase 5: ML Kit OCR + Play Services Location (vision tools + world_get_location)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.play.services.location)

    // Phase 6: cron-utils (standing instruction time trigger)
    implementation(libs.cron.utils)

    // Phase 6: cron-utils (standing instruction time trigger)
    // implementation(libs.cron.utils)

    // Testing (Phase 9 manual; minimal stubs untuk Hilt compile)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
