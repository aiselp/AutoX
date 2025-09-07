plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("de.undercouch.download") version "5.6.0"
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(versions.javaVersionInt))
    }
}
android {
    namespace = "com.aiselp.autojs.codeeditor"
    compileSdk = versions.compile

    defaultConfig {
        minSdk = versions.mini

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = compose_version
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.androidx.webkit)
    implementation(libs.google.gson)
    implementation(libs.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(project(":autojs"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

tasks.register("downloadEditor") {
    val tag = "v0.4.0"
    val uri = "https://github.com/aiselp/vscode-mobile/releases/download/${tag}/dist.zip"
    val assetsDir = File(projectDir, "/src/main/assets/codeeditor")
    val versionFile = File(assetsDir, "version.txt")
    doFirst {
        logger.log(LogLevel.LIFECYCLE, "start downloadEditor")
        assetsDir.mkdirs()
        if (versionFile.isFile && versionFile.readText() == tag) {
            logger.log(LogLevel.LIFECYCLE, "skip download")
            return@doFirst
        }
        download.run {
            src(uri)
            dest(File(assetsDir, "dist.zip"))
        }
        versionFile.writeText(tag)
    }
}
tasks.findByName("preBuild")?.dependsOn("downloadEditor")
tasks.findByName("preDebugBuild")?.dependsOn("downloadEditor")