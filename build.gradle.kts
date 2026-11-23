initVersions(file("project-versions.json"))
// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        mavenLocal()
        //首选国外镜像加快github CI
        google()
        mavenCentral()
        maven("https://www.jitpack.io")
        maven("https://maven.aliyun.com/repository/central")
        google { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral { url = uri("https://maven.aliyun.com/repository/public") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.2.1")
    }
}

allprojects {
    repositories {
        mavenLocal()
        //首选国外镜像加快github CI
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven("https://maven.aliyun.com/repository/central")
        google { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral { url = uri("https://maven.aliyun.com/repository/public") }
    }

}

tasks.register<Delete>("clean").configure {
    delete(rootProject.buildDir)
}
