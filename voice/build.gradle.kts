import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// pKeyboard Voice: optional add-on APK with voice input (RECORD_AUDIO) and the online translation
// fallback (INTERNET), so the keyboard itself needs neither permission. See shared/voiceapi.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Same versioning and release key as the keyboard: the signature permission between the two APKs
// only works when both are signed with the same key.
apply(from = rootProject.file("gradle/android-common.gradle"))
val appVersionName: String by extra

android {
    namespace = "x.vladgba.keyboard.voice"
    compileSdk = 35

    defaultConfig {
        applicationId = "x.vladgba.keyboard.voice"
        minSdk = 21
        targetSdk = 35
        // android-common names apps after the root project; the add-on has its own label.
        resValue("string", "app_name", "pKeyboard Voice")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "@pKeyboard Voice")
        }
    }

    sourceSets.getByName("main").java.srcDir(rootProject.file("shared/voiceapi"))

    buildFeatures {
        resValues = true
        buildConfig = false
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += listOf("META-INF/*.kotlin_module", "META-INF/*.version", "kotlin/**", "DebugProbesKt.bin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

base.archivesName.set("pKeyboardVoice-$appVersionName")

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
    }
}

// No library dependencies: Android framework + Kotlin stdlib only, like the keyboard.
dependencies { }
