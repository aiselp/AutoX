plugins {
    // 自动下载项目所需的JDK版本
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}

include(":app", ":automator", ":common", ":autojs", ":inrt", ":apkbuilder")
include(":paddleocr")
include(":codeeditor")
