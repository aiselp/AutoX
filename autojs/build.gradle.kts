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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "MIN_SDK_VERSION", versions.mini.toString())
        buildConfigField("int", "VERSION_CODE", versions.appVersionCode.toString())
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
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = compose_version
    }

    lint.abortOnError = false
    sourceSets {
        named("main") {
            res.srcDirs("src/main/res", "src/main/res-i18n")
        }
    }
    namespace = "com.stardust.autojs"
}

dependencies {
    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.rules)
    debugImplementation(libs.leakcanary.android)
    implementation(libs.leakcanary.robject.watcher.android)
    testImplementation(libs.junit)

    implementation(libs.coil.compose)
    implementation(libs.glide)
    implementation(libs.documentfile)
    implementation(libs.preference.ktx)
    implementation(libs.javet.android.node)
    api(libs.rxjava3.rxandroid)

    api(libs.ktsh)
    api(libs.bundles.shizuku)
    api("net.lingala.zip4j:zip4j:1.3.2")
    api("com.afollestad.material-dialogs:core:0.9.2.3")
    implementation(libs.material)
    api("com.github.hyb1996:EnhancedFloaty:0.31")
    api("com.makeramen:roundedimageview:2.3.0")
    // OkHttp
    api(libs.okhttp)
    // Gson
    api(libs.google.gson)
    api(project(path = ":common"))
    api(project(path = ":automator"))
    api(project(path = ":LocalRepo:libtermexec"))
    api(project(path = ":LocalRepo:emulatorview"))
    api(project(path = ":LocalRepo:term"))
    implementation("com.hzy:libp7zip:1.7.0")
    api(project(":paddleocr"))
    api(libs.mozilla.rhino)
    api(libs.mozilla.rhino.xml)
    api(libs.mozilla.rhino.tools)
    implementation(libs.opencv)
    // libs
    implementation(libs.byte.buddy.android)
    implementation("cz.adaptech:tesseract4android:4.1.1")
    implementation(libs.bundles.mlkit)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
}
tasks.register<Exec>("buildV7Api") {
    group = "build"
    val v7ApiDir = File(projectDir, "src/main/js/v7-api")
    val v7ModuleDir = File(projectDir, "src/main/assets/v7modules")
    workingDir = v7ApiDir
    execCommand("node build.mjs")
    doLast {
        copy {
            delete(v7ModuleDir)
            from(File(v7ApiDir, "dist"))
            into(v7ModuleDir)
        }
        delete(fileTree(v7ModuleDir) {
            include("**/*.ts")
        })
    }
}

tasks.register<Exec>("buildV6Api") {
    group = "build"
    val v6ApiDir = File(projectDir, "src/main/js/v6-api")
    val v6ModuleDir = File(projectDir, "src/main/assets/v6modules")
    workingDir = v6ApiDir
    execCommand("node build.mjs")
    doLast {
        copy {
            delete(v6ModuleDir)
            from(File(v6ApiDir, "dist"))
            into(v6ModuleDir)
        }
        delete(fileTree(v6ModuleDir) {
            include("**/*.ts")
        })
    }
}

tasks.register("buildJsModule") {
    group = "build"
    dependsOn("buildV6Api", "buildV7Api")
}
