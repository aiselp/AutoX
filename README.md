# Autox.js v7
<p align="center"> 
  
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/aiselp/AutoX/total)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/aiselp/AutoX)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/aiselp/AutoX/android-test.yml)
![GitHub Release](https://img.shields.io/github/v/release/aiselp/AutoX)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/ca72518c8bd548f9a350d5a15e2ed9ea)](https://app.codacy.com/gh/aiselp/AutoX/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

</p>

[English Document](README_en.md)

## 简介

一个支持无障碍服务的Android平台上的JavaScript 运行环境 和 开发环境，其发展目标是类似JsBox和Workflow。

本项目从[hyb1996](https://github.com/hyb1996/Auto.js) autojs 获得,并命名为Autox.js （autojs 修改版本），
你现在看的是原4.1版本基础上的项目，
后面我们将针对项目本身如何开发、运行的进行介绍，欢迎更多开发者参与这个项目维护升级。[hyb1996](https://github.com/hyb1996/Auto.js)采用的
[Mozilla Public License Version 2.0](https://github.com/hyb1996/NoRootScriptDroid/blob/master/LICENSE.md)
+**非商业性使用**，出于多种因素考虑， 本产品采用 [GPL-V2](https://opensource.org/licenses/GPL-2.0) 许可证，
无论是其他贡献者，还是使用该产品，均需按照 MPL-2.0+非商业性使用 和 GPL-V2 的相关要求使用。

关于两种协议：

* GPL-V2[https://opensource.org/licenses/GPL-2.0](https://opensource.org/license/gpl-2-0/)
* MPL-2 (https://www.mozilla.org/MPL/2.0)

### 现在的Autox.js：

* Autox.js文档： https://autox-doc.vercel.app/
* 开源地址： https://github.com/aiselp/AutoX/
* pc端开发[VS Code 插件](https://marketplace.visualstudio.com/items?itemName=aaroncheng.auto-js-vsce-fixed)
* 官方论坛： [www.autoxjs.com](http://www.autoxjs.com)
* autoxjs[更新日志](CHANGELOG.md)

### Autox.js下载地址：
[releases](https://github.com/aiselp/AutoX/releases)  
如果下载过慢可以右键复制 Release Assets 中APK文件的链接地址，粘贴到 [http://toolwa.com/github/](http://toolwa.com/github/) 等github加速网站下载

#### APK版本说明：
目前仅提供一下两种版本
- arm64-v8a: 64位ARM设备（主流旗舰机）
- mini-arm64-v8a: 移除一些非必要资源版本，可以在使用时按需下载

### 特性

1. 由无障碍服务实现的简单易用的自动操作函数
2. 悬浮窗录制和运行
3. 更专业&强大的选择器API，提供对屏幕上的控件的寻找、遍历、获取信息、操作等。类似于Google的UI测试框架UiAutomator，您也可以把他当做移动版UI测试框架使用
4. 采用JavaScript为脚本语言，并支持代码补全、变量重命名、代码格式化、查找替换等功能，可以作为一个JavaScript IDE使用
5. 支持使用e4x编写界面，并可以将JavaScript打包为apk文件，您可以用它来开发小工具应用
6. 支持使用Root权限以提供更强大的屏幕点击、滑动、录制功能和运行shell命令。录制录制可产生js文件或二进制文件，录制动作的回放比较流畅
7. 提供截取屏幕、保存截图、图片找色、找图等函数
8. 可作为Tasker插件使用，结合Tasker可胜任日常工作流
9. 带有界面分析工具，类似Android Studio的LayoutInspector，可以分析界面层次和范围、获取界面上的控件信息的

本软件与按键精灵等软件不同，主要区别是：

1. Auto.js主要以自动化、工作流为目标，更多地是方便日常生活工作，例如启动游戏时自动屏蔽通知、一键与特定联系人微信视频（知乎上出现过该问题，老人难以进行复杂的操作和子女进行微信视频）等
2. Auto.js兼容性更好。以坐标为基础的按键精灵、脚本精灵很容易出现分辨率问题，而以控件为基础的Auto.js则没有这个问题
3. Auto.js执行大部分任务不需要root权限。只有需要精确坐标点击、滑动的相关函数才需要root权限
4. Auto.js可以提供界面编写等功能，不仅仅是作为一个脚本软件而存在

### v7版本新增功能特性🎉

- [x] 全新基于Material Design 3的ui界面
- [x] 支持[Shizuku](https://shizuku.rikka.app/introduction/)功能并且能够运行嵌入式脚本，从而能够动态调试基于Shizuku的api而无需频繁构建调试apk来测试Shizuku功能
- [x] 引入新的[nodejs引擎](https://github.com/caoccao/Javet?tab=readme-ov-file)，支持运行大量来自npm软件包，同时能够与java交互
- [x] 迁移大量模块使用ts编写并添加类型申明，支持使用ts编写脚本并获得更全面的类型提示
- [x] 全新基于Vue3和Jetpack Compose的ui框架，让你可以使用vue3编写数据响应式的Material Design 3界面
- [ ] 新一代基于nodejs的api(简称v7 api)，提供大量非阻塞式的功能模块(完善中)
- [x] 完善app打包功能和签名管理，支持打包nodejs引擎脚本，支持特殊权限请求配置
- [x] [Rhino](https://github.com/mozilla/rhino/)升级至v1.8.0稳定版，支持更多es6+语法

### 示例
可在[这里](https://github.com/aiselp/AutoX/tree/setup-v7/app/src/main/assets/sample)查看一些示例，或者直接在应用内查看和运行。


### 编译相关：
环境要求:`java`版本为17

命令说明：在项目根目录下运行命令，如果使用 Windows powerShell < 7.0，请使用包含 ";" 的命令

**从7.0版本开始，构建之前，需要运行以下命令编译js模块，确保你已经安装了nodejs 20+**

```shell
./gradlew autojs:buildJsModule
```
仅需运行一次，若更改了模块代码需再次运行获得更新
##### 构建文档

```shell
./gradlew app:buildDocs
````
仅需运行一次，若更改了文档需再次运行获得更新
##### 本地安装调试版本到设备：
```shell
./gradlew app:buildDebugTemplateApp && ./gradlew app:assembleV7Debug && ./gradlew app:installV7Debug
#或
./gradlew app:buildDebugTemplateApp ; ./gradlew app:assembleV7Debug ; ./gradlew app:installV7Debug
```
生成的调试版本APK文件在 app/build/outputs/apk/v6/debug 下，使用默认签名

##### 本地编译发布版本：
```shell
./gradlew app:buildTemplateApp && ./gradlew app:assembleV7
#或
./gradlew app:buildTemplateApp ; ./gradlew app:assembleV7
```
生成的是未签名的APK文件，在 app/build/outputs/apk/v6/release 下，需要签名后才能安装

##### 本地 Android Studio 运行调试版本到设备：
先运行以下命令：

```shell
./gradlew app:buildDebugTemplateApp
```

再点击 Android Studio 运行按钮

##### 本地 Android Studio 编译发布版本并签名：
先运行以下命令：

```shell
./gradlew app:buildTemplateApp
```

再点击 Android Studio 菜单 "Build" -> "Generate Signed Bundle /APK..." -> 勾选"APK" -> "Next" -> 选择或新建证书 -> "Next" -> 选择"v7Release" -> "Finish"
生成的APK文件，在 app/v7/release 下

### 测试
目前为autojs模块中添加了部分脚本功能测试，如需运行测试请参考以下步骤

1. 准备一台安卓设备并使用adb连接到电脑
2. 使用最新版`Android Studio`完成一次模块构建`./gradlew autojs:assemble`
3. 打开`autojs/src/androidTest`目录下的测试类
4. 点击类名旁边的运行按钮开始测试
5. 随后可能因设备而异需要在手机上点击允许测试apk安装
