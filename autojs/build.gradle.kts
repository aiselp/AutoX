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
    debugImplementation(libs.leakcanary.robject.watcher.android)
    testImplementation(libs.junit)

    implementation(libs.coil)
    implementation(libs.coil.compose)
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
    implementation("com.hzy:libp7zip:1.7.0")
    api(project(":paddleocr"))
    api(libs.mozilla.rhino)
    api(libs.mozilla.rhino.xml)
    api(libs.mozilla.rhino.tools)
    implementation(libs.opencv)
    // libs
    implementation(libs.dalvik.dx)
    implementation(libs.tesseract4android.core)
    implementation(libs.camera.core)
    implementation(libs.bundles.mlkit)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
}
tasks.register("buildV7Api") {
    group = "build"
    val v7ApiDir = File(projectDir, "src/main/js/v7-api")
    val v7ModuleDir = File(projectDir, "src/main/assets/v7modules")

    // 只跟踪源码与构建配置。dist 是本任务自己生成的产物、node_modules 由 npm install
    // 维护，两者若纳入 inputs 会造成「执行改输入」的自反馈，任务永远无法 UP-TO-DATE。
    inputs.files(fileTree(v7ApiDir).matching {
        include("**/*")
        exclude("node_modules/**", "dist/**")
    })
    outputs.dir(v7ModuleDir)

    doLast {
        exec {
            workingDir = v7ApiDir
            commandLine("node", "build.mjs")
        }
        sync {
            from(File(v7ApiDir, "dist")) {
                include("**/*.js")
            }
            into(v7ModuleDir)
        }
    }
}

tasks.register("buildV6Api") {
    group = "build"
    val v6ApiDir = File(projectDir, "src/main/js/v6-api")
    val v6ModuleDir = File(projectDir, "src/main/assets/v6modules")

    // 同 buildV7Api：排除 dist 产物与 node_modules，避免输入包含任务自身产物。
    inputs.files(fileTree(v6ApiDir).matching {
        include("**/*")
        exclude("node_modules/**", "dist/**")
    })
    outputs.dir(v6ModuleDir)

    doLast {
        exec {
            workingDir = v6ApiDir
            commandLine("node", "build.mjs")
        }
        sync {
            from(File(v6ApiDir, "dist")) {
                include("**/*.js")
            }
            into(v6ModuleDir)
        }
    }
}

tasks.register("buildJsModule") {
    group = "build"
    dependsOn("buildV6Api", "buildV7Api")
}

// 让编译时自动触发
tasks.named("preBuild") {
    dependsOn("buildJsModule")
}