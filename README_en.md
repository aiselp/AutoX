# Auto.js and AutoX.js
[中文文档](README.md)
## Introduction

A JavaScript runtime and development environment on the Android platform that supports barrier-free services. Its development goal is similar to JsBox and Workflow.

~~Since the [original author](https://github.com/hyb1996) no longer maintains the Auto.js project
I plan to continue to maintain the project on the original basis. This project is based on [autojs](https://github.com/hyb1996/Auto.js) and the original project is named Autox.js.
You are now looking at the project based on the original 4.1 version. Later, I will introduce how to develop and run the project itself. More developers are welcome to participate in the maintenance and upgrade of this project.
The latest [Autox.js address](https://github.com/kkevsekk1/AutoX), there are many original project paths in the document,
I do not intend to replace the original project if it is not deleted, to show respect for the original author. This document contains encryption-related content that may conflict with the actual operation.
If you want to encrypt the code you write to protect intellectual property, please refer to the project https://github.com/kkevsekk1/webpack-autojs
I will gradually improve and update the program code to keep it consistent as much as possible.~~

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
- universal: universal version (don't care about the size of the installation package/don't bother to choose, just use this version, including the following 2 CPU architectures so)
- armeabi-v7a: 32-bit ARM device (preferred for backup machine)
- arm64-v8a: 64-bit ARM device (mainstream flagship machine)

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

### Examples
You can view some examples [here](https://github.com/aiselp/AutoX/tree/setup-v7/app/src/main/assets/sample), or view and run them directly in the app.

### Architecture diagram

To be supplemented, but is anyone really interested in this? Welcome to contact me for communication

## About License

##### This product adopts the [GPL-V2](https://opensource.org/licenses/GPL-2.0) license

##### Due to historical reasons, it must also follow the [autojs project](https://github.com/hyb1996/Auto.js) agreement:

Based on [Mozilla Public License Version 2.0](https://github.com/hyb1996/NoRootScriptDroid/blob/master/LICENSE.md) and with the following terms:

* **Non-commercial use** — The source code and binary products of this project and its derivatives may not be used for any commercial and profit-making purposes

#### Can this Autox.js adopt GPL-V2?

It should be easy to understand about GPL-V2, the famous linux adopts this license. However, many articles about MPL-2.0 are still based on the MPL-1.1 version, which has caused trouble for many domestic developers.
This is a relatively standard [translation](https://github.com/rachelzhang1/MPL2.0_zh-CN/blob/93d2feec60d8b0b5a54a1843c866994af4610d4f/Mozilla_Public_License_2.0_Simplified_Chinese_Reference.txt)
If you are interested, you can study it.

#### Code contributors should note:

No one in the original text declares that the license is MPL2.0. New files or modifications (limited to your own modifications) of the code use GPL-V2, and relevant statements need to be made.

``` java
// SPDX-License-Identifier: GPL-2.0
// Claim your copyright
```

#### Others using Autox.js, please pay attention to in-depth development

* If you use code with GPL-2.0 declaration or compiled binary. You need to open source all your code.

* If you only use MPL-2.0 stuff, you need to open source the relevant code you modified.

#### Let's talk about open source and business aside from this product

* Open source does not mean arbitrary use, and open source does not mean prohibiting commercial use!

* Open source things can be commercial, but you need to open source according to regulations!

* Commercial products can be open source, such as redhat!

* If you do not use open source products according to the open source agreement, you can understand the source of openwrt and the domestic infringement cases in recent years!

#### About js scripts developed by others, run on it. Is it necessary to follow GPL-2.0 for open source?

* That is your freedom, not restricted by this agreement, just like running software in Linux

#### Can this product or autojs product be used for commercial purposes?

* Whether this product can be used for commercial purposes depends on the original autojs, because many functions and code copyrights are currently owned by autojs.

* Whether autojs can be used for commercial purposes depends on your understanding of the accompanying "**non-commercial use**" and its legal benefits.

* Anyway, this product will not use autojs for commercial purposes.

### Compilation related:
Environment requirements: `jdk` version 17 or above

Command description: Run the command in the project root directory. If you use Windows powerShell < 7.0, please use the command containing ";"

Starting from version 7.0, before building, you need to run the following command to compile the js module. Make sure you have installed nodejs 20+
```shell
./gradlew autojs:buildJsModule
```
##### Building Documentation
```shell
./gradlew app:buildDocs
````
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