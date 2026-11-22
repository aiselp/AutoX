

declare var global = globalThis

declare var android, java, com, Packages, org
/**
 * 一个 android.content.Context 对象。
 */
declare var context: android.Context

declare var runtime: Autox.Runtime & RuntimeModule


declare var util: any


declare var android, java, com, Packages, org
/**
 * 暂停运行 n 毫秒的时间。
 * 1 秒等于 1000 毫秒。
 * @param n 毫秒数
 */
declare function sleep(n: number): void;

/**
 * 返回最近一次监测到的正在运行的应用的包名。
 * 一般可以认为就是当前正在运行的应用的包名。
 * 此函数依赖于无障碍服务，如果服务未启动，则抛出异常并提示用户启动。
 */
declare function currentPackage(): string;

/**
 * 返回最近一次监测到的正在运行的 Activity 的名称。
 * 一般可以认为就是当前正在运行的 Activity 的名称。
 * 此函数依赖于无障碍服务，如果服务未启动，则抛出异常并提示用户启动。
 */
declare function currentActivity(): string;

/**
 * 设置剪贴板内容。
 * 此剪贴板即系统剪贴板，在一般应用的输入框中"粘贴"既可使用。
 * @param text 文本
 */
declare function setClip(text: string): void;

/**
 * 返回系统剪贴板的内容。
 */
declare function getClip(): string;

/**
 * 以气泡显示信息 message 几秒。(具体时间取决于安卓系统，一般都是 2 秒)。
 * 注意，信息的显示是"异步"执行的。
 * @param message 要显示的信息
 */
declare function toast(message: string): void;

/**
 * 自定义 Toast 的可选配置。
 * bg / textColor 为 Android COLOR 整数（如 colors.parseColor("#DD333333")），
 * icon 为 base64 图片（可带 data:image/...;base64, 前缀）。
 */
interface CustomToastConfig {
    /** 是否同时输出到控制台，默认 false */
    log?: boolean;
    /** 显示时长（毫秒），默认 2000 */
    duration?: number;
    /** 重力方向（android.view.Gravity 常量），默认 Gravity.BOTTOM */
    gravity?: number;
    /** x 偏移（px），默认 0 */
    xOffset?: number;
    /** y 偏移（px），默认 150 */
    yOffset?: number;
    /** 透明度 0~1，默认 1 */
    alpha?: number;
    /** 背景色（COLOR 整数），默认 #DD333333 */
    bg?: number;
    /** 文字颜色（COLOR 整数），默认白色 */
    textColor?: number;
    /** 文字大小（sp），默认 16 */
    textSize?: number;
    /** 圆角半径（dp），默认 8 */
    radius?: number;
    /** 内边距（dp），默认 8 */
    padding?: number;
    /** 图标的 base64 字符串，默认无 */
    icon?: string;
    /** 是否立即覆盖当前 Toast（否则排队），默认 false */
    override?: boolean;
}

declare namespace toast {
    /** 设置自定义 Toast 的默认配置，后续 toast.show() 未指定的字段沿用该配置。 */
    function init(config: CustomToastConfig): void;
    /**
     * 以自定义样式显示消息（队列化）。
     * @param message 要显示的信息
     * @param config 本次显示的配置，未指定的字段沿用 toast.init() 设置的默认值
     */
    function show(message: string, config?: CustomToastConfig): void;
}

/**
 * 相当于 toast(message); log(message)。
 * 显示信息 message 并在控制台中输出。
 * @param message 要显示的信息
 */
declare function toastLog(message: string): void;

/**
 * 等待指定的 Activity 出现。
 * @param activity Activity 名称
 * @param period 轮询等待间隔（毫秒），默认为 200
 */
declare function waitForActivity(activity: string, period?: number): void;

/**
 * 等待指定的应用出现。
 * 例如 waitForPackage("com.tencent.mm") 为等待当前界面为微信。
 * @param packageName 包名
 * @param period 轮询等待间隔（毫秒），默认为 200
 */
declare function waitForPackage(packageName: string, period?: number): void;

/**
 * 立即停止脚本运行。
 * 立即停止是通过抛出 ScriptInterrupttedException 来实现的。
 */
declare function exit(err?: any): void;

/**
 * 返回一个在 [min...max] 之间的随机整数。
 * 例如 random(0, 2) 可能产生 0, 1, 2。
 * @param min 随机数产生的区间下界
 * @param max 随机数产生的区间上界
 */
declare function random(min: number, max: number): number;

/**
 * 返回在 [0, 1) 的随机浮点数。
 */
declare function random(): number;

/**
 * 表示此脚本需要 Android API 版本达到指定版本才能运行。
 * 调用该函数时会判断运行脚本的设备系统的版本号，如果没有达到要求则抛出异常。
 * @param api Android 版本号 (例如 19 表示 Android 4.4, 24 表示 Android 7.0)
 */
declare function requiresApi(api: number): void;

/**
 * 表示此脚本需要 Auto.js 版本达到指定版本才能运行。
 * 调用该函数时会判断运行脚本的 Auto.js 的版本号，如果没有达到要求则抛出异常。
 * @param version Auto.js 的版本或版本号。可以是整数(如 250)或字符串(如 "3.0.0 Beta")
 */
declare function requiresAutojsVersion(version: string | number): void;

// ==========================================
// Runtime 模块
// ==========================================

interface RuntimeModule {
    /**
     * 动态申请安卓的权限。
     * 目前 Auto.js 只能额外申请两个权限：access_fine_location (GPS), record_audio (录音)。
     * @param permissions 权限的字符串数组
     */
    requestPermissions(permissions: string[]): void;

    /**
     * 加载目标 jar 文件，加载成功后将可以使用该 Jar 文件的类。
     * @param path jar 文件路径
     */
    loadJar(path: string): void;

    /**
     * 加载目标 dex 文件，加载成功后将可以使用该 dex 文件的类。
     * 因为加载 jar 实际上是把 jar 转换为 dex 再加载的，因此加载 dex 文件会比 jar 文件快得多。
     * @param path dex 文件路径
     */
    loadDex(path: string): void;
}
// ==========================================
// 全局变量
// ==========================================

/**
 * 全局变量。一个 android.content.Context 对象。
 * 注意该对象为 ApplicationContext，因此不能用于界面、对话框等的创建。
 */
declare const context: android.Context;

/**
 * 引入 Java 类 (Rhino/Auto.js 特有全局函数，文档示例中出现)
 * @param clazz Java 类全名字符串
 * 例如 importClass("java.util.File");
 * 必须在顶层作用域调用此函数 (不能在函数内部调用)。
 */
declare function importClass(clazz: any): void;



// ==========================================
// CommonJS / Node.js 兼容变量
// ==========================================

/**
 * 引入模块
 * @param module 模块路径
 * @returns 模块导出对象
 * 在使用TypeScript时建议使用 import,export 语法导入模块
 */
declare function require(module: string): any;

/**
 * 当前模块对象
 * 在使用TypeScript时建议使用 import,export 语法导入模块
 */
declare var module: {
    exports: any;
};

/**
 * 模块导出对象
 */
declare var exports: any;