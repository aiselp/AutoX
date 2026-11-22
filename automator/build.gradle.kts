plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(versions.javaVersionInt))
    }
}
android {
    compileSdk = versions.compile

    defaultConfig {
        minSdk = versions.mini
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    lint.abortOnError = false
    buildTypes {
        named("release") {
            isMinifyEnabled = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
        }
    }
    sourceSets {
        named("main") {
            jniLibs.directories.add("/libs")
        }
    }
    namespace = "com.stardust.automator"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    androidTestImplementation(libs.espresso.core)
    testImplementation(libs.junit)
    api(libs.appcompat)
    implementation(libs.rxjava3.rxandroid)
    implementation(libs.kotlinx.coroutines.android)
    api(project(":common"))
}
