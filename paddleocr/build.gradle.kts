import java.net.HttpURLConnection
import java.net.URL

plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    compileSdk = versions.compile

    defaultConfig {
        minSdk = versions.mini
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf(
                    "-std=c++11",
                    "-frtti",
                    "-fexceptions",
                    "-Wno-format"
                )
                arguments(
                    "-DANDROID_PLATFORM=android-23",
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ARM_NEON=TRUE"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.10.2"
        }
    }

    ndkVersion = "21.4.7075529"

    compileOptions {
        sourceCompatibility = versions.javaVersion
        targetCompatibility = versions.javaVersion
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

    sourceSets {
        named("main") {
            jniLibs.srcDirs("PaddleLite/cxx/libs")
        }
    }
    namespace = "org.autojs.autoxjs.paddleocr"
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
repositories {
    mavenCentral()
}


val cachePath = file("${project.rootDir}/.gradle/cache")

// 版本管理
object LibVersions {
    const val opencv = "4.2.0"
    const val paddleLite = "v2.14-rc"
}

// 链接管理
object LibUrls {
    const val openCV =
        "https://paddlelite-demo.bj.bcebos.com/libs/android/opencv-${LibVersions.opencv}-android-sdk.tar.gz"

    fun paddleLite(arch: String) =
        "https://github.com/PaddlePaddle/Paddle-Lite/releases/download/${LibVersions.paddleLite}" +
                "/inference_lite_lib.android.${arch}.clang.c++_shared.with_extra.with_cv.tar.gz"
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

fun downloadFile(url: String, dest: File) {
    dest.parentFile.mkdirs()
    println("Downloading $url")

    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connect()

    connection.inputStream.use { input ->
        dest.outputStream().use { output ->
            val totalBytes = connection.contentLengthLong
            val buffer = ByteArray(1024 * 8)
            var bytesRead: Int
            var downloaded = 0L
            var lastProgressUpdate = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                
                // 每100ms更新一次进度
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate >= 100 || downloaded == totalBytes) {
                    lastProgressUpdate = now
                    val progressBar = if (totalBytes > 0) {
                        val progress = (downloaded * 20 / totalBytes).toInt()
                        "[" + "=".repeat(progress) + " ".repeat(20 - progress) + "]"
                    } else ""
                    
                    print("\r${formatFileSize(downloaded)}")
                    if (totalBytes > 0) {
                        print(" / ${formatFileSize(totalBytes)} $progressBar ${downloaded * 100 / totalBytes}%")
                    }
                }
            }
            println("\nDownload completed")
        }
    }
}

// 下载OpenCv
tasks.register("downloadOpenCV") {
    group = "download"
    description = "Download OpenCV SDK"

    doLast {
        val destDir = file("OpenCV")
        if (destDir.exists()) return@doLast

        val cacheFile = cachePath.resolve("opencv/${LibVersions.opencv}.tar.gz")
        if (!cacheFile.exists()) downloadFile(LibUrls.openCV, cacheFile)

        copy {
            from(tarTree(cacheFile))
            into(destDir)
        }
        println("OpenCV installed")
    }
}

// 更新Paddle 预测库
tasks.register("downloadPaddleLite") {
    group = "download"
    description = "Download PaddleLite"

    doLast {
        val destDir = file("PaddleLite")
        listOf("armv7" to "armeabi-v7a", "armv8" to "arm64-v8a").forEach { (arch, abiDir) ->
            println("\nProcessing $arch architecture...")
            val cacheFile = cachePath.resolve("paddlelite/${LibVersions.paddleLite}/$arch.tar.gz")
            if (!cacheFile.exists()) downloadFile(LibUrls.paddleLite(arch), cacheFile)
            val tempDir = file("${cacheFile.parent}/temp_$arch")
            copy {
                from(tarTree(cacheFile))
                into(tempDir)
            }
            val sourceDir = tempDir.listFiles { it: File ->
                it.isDirectory && it.name.contains("inference_lite_lib")
            }?.first() ?: error("Invalid PaddleLite archive")
            copy {
                from("${sourceDir}/cxx/include")
                into("${destDir}/cxx/include")
            }
            copy {
                from("${sourceDir}/cxx/lib/libpaddle_light_api_shared.so")
                into("${destDir}/cxx/libs/$abiDir")
            }
            tempDir.deleteRecursively()
            println("PaddleLite $arch installed")
        }
    }
}

// 添加到构建依赖
tasks.named("preBuild") { 
    dependsOn("downloadOpenCV", )
}