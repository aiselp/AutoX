plugins {
    id("com.android.library")
    id("kotlin-android")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(versions.javaVersionInt))
    }
}

android {
    namespace = "org.autojs.autoxjs.mcp"
    compileSdk = versions.compile

    defaultConfig {
        minSdk = versions.mini
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.gson)
    implementation(libs.bundles.ktor)
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.preference.ktx)
    implementation(project(":autojs"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
