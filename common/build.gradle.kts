//import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.compose)
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        named("release") {
            isMinifyEnabled = false
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
        }
    }
    lint.abortOnError = false
    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res", "src/main/res-i18n")
        }
    }
    namespace = "com.stardust"
}

dependencies {
    implementation(platform(libs.compose.bom))
    api(libs.activity.compose)
    api(libs.compose.ui)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    api(libs.compose.material3.window.size)
    api(libs.compose.material3.adaptive.navigation.suite)
    api(libs.androidx.webkit)
    api(libs.commons.exec)
    api(libs.androidx.datastore)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    api("com.github.hyb1996:settingscompat:1.1.5")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.appcompat)
    api(kotlin("reflect", version = "1.7.10"))

    //MQTT
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.1.0")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
}
