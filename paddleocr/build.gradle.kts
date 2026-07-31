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
    compileSdk = versions.compile

    defaultConfig {
        minSdk = versions.mini
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"

        ndkVersion = "29.0.14206865"

        externalNativeBuild {
            cmake {
                arguments("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    /*
    buildFeatures {
        prefab = true
    }

    packaging {
        jniLibs {
            excludes += "** /libc++_shared.so"
        }
    }
    */

    externalNativeBuild {
        cmake {
            path = File("src/main/jni/CMakeLists.txt")
        }
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android.txt"),
                    "proguard-rules.pro"
                )
            )
        }
    }

    namespace = "org.autojs.autoxjs.paddleocr"
}

dependencies {
    //implementation(libs.okhttp)
    implementation(libs.core.ktx)

    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

repositories {
    mavenCentral()
}