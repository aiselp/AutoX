# AutoX.js v7
<p align="center"> 
  
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/aiselp/AutoX/total)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/aiselp/AutoX)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/aiselp/AutoX/android-test.yml)
![GitHub Release](https://img.shields.io/github/v/release/aiselp/AutoX)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/ca72518c8bd548f9a350d5a15e2ed9ea)](https://app.codacy.com/gh/aiselp/AutoX/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

</p>

[中文文档](README.md)
## Introduction

A JavaScript runtime and development environment on the Android platform that supports barrier-free services. Its development goal is similar to JsBox and Workflow.


This project is obtained from [hyb1996](https://github.com/hyb1996/Auto.js) autojs and named Autox.js (autojs modified version).
You are now looking at the project based on the original 4.1 version.
Later, we will introduce how to develop and run the project itself. More developers are welcome to participate in the maintenance and upgrade of this project. [hyb1996](https://github.com/hyb1996/Auto.js) adopts
[Mozilla Public License Version 2.0](https://github.com/hyb1996/NoRootScriptDroid/blob/master/LICENSE.md)
+**Non-commercial use**. For various reasons, this product adopts the [GPL-V2](https://opensource.org/licenses/GPL-2.0) license.
Whether it is other contributors or the use of this product, they must comply with the relevant requirements of MPL-2.0+Non-commercial use and GPL-V2.

About the two agreements:

* GPL-V2[https://opensource.org/licenses/GPL-2.0](https://opensource.org/license/gpl-2-0/)

* MPL-2 (https://www.mozilla.org/MPL/2.0)

### Current Autox.js:

* Autox.js documentation: https://autox-doc.vercel.app/

* Open source address: https://github.com/aiselp/AutoX/

* PC-side development [VS Code plug-in](https://marketplace.visualstudio.com/items?itemName=aaroncheng.auto-js-vsce-fixed)

* Official forum: [www.autoxjs.com](http://www.autoxjs.com)

* autoxjs [update log](CHANGELOG.md)

### Autox.js download address:
[releases](https://github.com/aiselp/AutoX/releases)
If the download is too slow, you can right-click and copy the link address of the APK file in Release Assets, and paste it to [http://toolwa.com/github/](http://toolwa.com/github/) and other github acceleration websites to download

#### APK version description:
Currently, only the following two versions are available:
- arm64-v8a: 64-bit ARM devices (mainstream flagship devices)
- mini-arm64-v8a: Removes some non-essential resource versions and can be downloaded on demand.

### Features

1. Simple and easy-to-use automatic operation function implemented by accessibility service
2. Floating window recording and running
3. More professional & powerful selector API, providing search, traversal, information acquisition, operation, etc. of controls on the screen. Similar to Google's UI testing framework UiAutomator, you can also use it as a mobile UI testing framework
4. Use JavaScript as the scripting language, and support code completion, variable renaming, code formatting, search and replace and other functions, can be used as a JavaScript IDE
5. Support the use of e4x to write interfaces, and can package JavaScript into apk files, you can use it to develop small tool applications
6. Support the use of Root permissions to provide more powerful screen clicks, sliding, recording functions and running shell commands. Recording can generate js files or binary files, and the playback of recorded actions is relatively smooth
7. Provides functions such as screenshot, screenshot saving, image color search, image search, etc.
8. Can be used as a Tasker plug-in, combined with Tasker, it can handle daily workflows
9. With interface analysis tools, similar to Android Studio's LayoutInspector, it can analyze the interface hierarchy and range, and obtain the control information on the interface

This software is different from software such as Keyboard Wizard. The main differences are:

1. Auto.js mainly aims at automation and workflow, and is more convenient for daily life and work, such as automatically blocking notifications when starting a game, and one-click WeChat video with specific contacts (this problem has appeared on Zhihu, and it is difficult for the elderly to perform complex operations and WeChat video with their children), etc.
2. Auto.js has better compatibility. Coordinate-based Keyboard Wizard and Script Wizard are prone to resolution problems, while control-based Auto.js does not have this problem
3. Auto.js does not require root permissions to perform most tasks. Only functions related to clicks and slides that require precise coordinates require root permissions
4. Auto.js can provide functions such as interface writing, and is not just a scripting software

### New features in v7 🎉

- [x] Brand new UI based on Material Design 3
- [x] Support for [Shizuku](https://shizuku.rikka.app/introduction/) and the ability to run embedded scripts, allowing dynamic debugging of Shizuku-based APIs without frequently building debug APKs to test Shizuku functionality
- [x] Introducing a new [NodeJS engine](https://github.com/caoccao/Javet?tab=readme-ov-file), supporting the execution of a large number of NPM packages and interoperability with Java
- [x] Migrating numerous modules to TS and adding type declarations, supporting scripts written in TS and providing more comprehensive type hints
- [x] A brand new UI framework based on Vue3 and Jetpack Compose, allowing you to write data-responsive Material Design 3 interfaces with Vue3
- [ ] A new generation of NodeJS-based APIs (referred to as the v7 API), providing a large number of non-blocking functional modules (working on)
- [x]  Improved app packaging and signature management, support for packaging Node.js engine scripts, and support for special permission request configuration.
- [x] [Rhino](https://github.com/mozilla/rhino/) upgraded to stable version 1.8.0, supporting more ES6+ syntax.

### Examples
You can view some examples [here](https://github.com/aiselp/AutoX/tree/setup-v7/app/src/main/assets/sample), or view and run them directly in the app.

### Compilation related:
Environment Requirements: Java version 17

Command Instructions: Run the command in the project root directory. If using Windows PowerShell (less than 7.0), use the command with a ";" prefix.

**Starting with version 7.0, you need to run the following command to compile the JS module before building. Make sure you have Node.js version 20 or higher installed.**
```shell
./gradlew autojs:buildJsModule
```
Only needs to be run once. If the module code is changed, it needs to be run again to get the update.
##### Building Documentation
```shell
./gradlew app:buildDocs
````
Only needs to be run once. If the document is changed, it needs to be run again to get the update.
##### Locally install the debug version to the device:
```shell
./gradlew app:buildDebugTemplateApp && ./gradlew app:assembleV7Debug && ./gradlew app:installV7Debug
#or
./gradlew app:buildDebugTemplateApp ; ./gradlew app:assembleV7Debug ; ./gradlew app:installV7Debug
```
The generated debug version APK file is in app/build/outputs/apk/v6/debug , use the default signature

##### Locally compile the release version:
```shell
./gradlew app:buildTemplateApp && ./gradlew app:assembleV7
#or
./gradlew app:buildTemplateApp ; ./gradlew app:assembleV7
```
The generated APK file is unsigned, under app/build/outputs/apk/v6/release, and needs to be signed before installation

##### Local Android Studio runs the debug version to the device:
First run the following command:

```shell
./gradlew app:buildDebugTemplateApp
```

Then click the Android Studio run button

##### Local Android Studio compiles the release version and signs it:
First run the following command:

```shell
./gradlew app:buildTemplateApp
```

Then click the Android Studio menu "Build" -> "Generate Signed Bundle /APK..." -> Check "APK" -> "Next" -> Select or create a new certificate -> "Next" -> Select "v7Release" -> "Finish"
The generated APK file is under app/v7/release

### Testing
We've added some script functionality tests to the autojs module. To run the tests, please refer to the following steps.

1. Prepare an Android device and connect it to your computer using adb.
2. Use the latest version of Android Studio to build the module using `./gradlew autojs:assemble`.
3. Open the test class in the `autojs/src/androidTest` directory.
4. Click the Run button next to the class name to start the test.
5. Depending on your device, you may need to click Allow test APK installation on your phone.
