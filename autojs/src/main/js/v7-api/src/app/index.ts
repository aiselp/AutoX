/**
 * @packageDocumentation
 * app模块提供一系列函数，用于使用其他应用、与其他应用交互。
 * 例如发送意图、打开文件、发送邮件等。
 * 同时提供了方便的进阶函数startActivity和sendBroadcast，
 * 用他们可完成app模块没有内置的和其他应用的交互。
 * 
 * 要导入此模块，请使用语句`import * as app from 'app'`，
 * 或者导入本模块的部分函数、属性`import { startActivity, packageName } from 'app';`
 * @example
 * ```js
 * import { startActivity, sendEmail } from 'app'
 * startActivity({
     packageName: 'org.autojs.autoxjs.v7',
     className: 'org.autojs.autojs.ui.settings.SettingsActivity'
   })
 * ```
 */
import { loadClass, android } from '@/java'
import { EmailOptions, IntentOptions, PMOptions } from './types'

const Intent = loadClass("android.content.Intent") as android.IntentConstructor
const Uri = loadClass("android.net.Uri") as any
const { context, app } = Autox
const appUtils = app.getAppUtils()

/** 当前应用的包名。 */
export const packageName: string = context.getPackageName()

/**
 * 根据选项构造一个{@link Intent}
 * @param options 选项
 * @returns 
 */
export function makeIntent(options: IntentOptions) {
    const intent = new Intent()
    if (options.packageName) {
        if (options.className) {
            intent.setClassName(options.packageName, options.className)
        } else {
            intent.package = options.packageName
        }
    }
    if (options.extras) {
        for (const key in options.extras) {
            intent.putExtra(key, options.extras[key])
        }
    }
    if (options.action) {
        if (options.action.indexOf(".") == -1) {
            intent.action = "android.intent.action." + options.action
        } else
            intent.action = options.action
    }
    if (options.data) {
        intent.data = parseUri(options.data)
    }
    if (options.type) {
        intent.setType(options.type)
    }
    if (options.flags) {
        for (const flag of options.flags) {
            if (typeof flag === 'number') {
                intent.addFlags(flag)
            } else {
                intent.addFlags(parseIntentFlag(flag))
            }
        }
    }
    if (options.category) {
        if (Array.isArray(options.category)) {
            for (const category of options.category) {
                intent.addCategory(category)
            }
        } else {
            intent.addCategory(options.category)
        }
    }

    return intent
}
/**
 * 用其他应用查看文件。文件不存在的情况由查看文件的应用处理。
 *  如果找不出可以查看该文件的应用，则抛出`ActivityNotException`。
 * @param file 文件路径
 */
export function viewFile(file: string): void {
    appUtils.viewFile(file)
}
/**
 * 用其他应用编辑文件。文件不存在的情况由编辑文件的应用处理。
 * 如果找不出可以编辑该文件的应用，则抛出`ActivityNotException`。
 * @param file 文件路径
 */
export function editFile(file: string) {
    appUtils.editFile(file)
}
export function getApkInfo(file: string, flags?: number | PMOptions): android.PackageInfo {
    const packageManager = (context as any).packageManager
    if (typeof flags === 'number') {
        return packageManager.getPackageArchiveInfo(file, flags)
    }
    return packageManager.getPackageArchiveInfo(file, 0)
}
/**
 * 获取应用包名对应的已安装的应用的名称。如果该找不到该应用，返回 null。
 * @param packageName 应用包名
 * @returns 
 */
export function getAppName(packageName: string): string | null {
    return appUtils.getAppName(packageName)
}

export function getInstalledPackages(flags?: number | PMOptions) {
    if (typeof flags === 'number') {
        return appUtils.getInstalledPackages(flags)
    }
    return appUtils.getInstalledPackages(0)
}
export const getInstalledApps = getInstalledPackages
/**
 * 获取应用名称对应的已安装的应用的包名，如果该找不到该应用，返回 null 。
 * 如果该名称对应多个应用，则只返回其中某一个的包名。
 * @param targetAppName app名称
 * @returns 
 */
export function getPackageName(targetAppName: string): string | null {
    return appUtils.getPackageName(targetAppName)
}
/**
 * 从一个文件路径创建一个 uri 对象。需要注意的是，在高版本 Android 上，
 * 由于系统限制直接在 Uri 暴露文件的绝对路径，因此返回的 Uri 会是诸如content://...的形式。
 * @param pathOrUri 文件路径，例如"/sdcard/1.txt"
 * @returns `Uri` 一个指向该文件的 Uri 的对象，参见[android.net.Uri](https://developer.android.com/reference/android/net/Uri)
 */
export function getUriForFile(pathOrUri: string): android.Uri {
    return appUtils.getUriForFile(pathOrUri)
}
/**
 * 通过应用包名启动应用。如果该包名对应的应用不存在，则返回 false；否则返回 true。
 * @param packageName 应用包名
 */
export function launch(packageName: string): boolean {
    return appUtils.launchPackage(packageName)
}
/**
 * 通过应用名称启动应用。如果该名称对应的应用不存在，
 * 则返回 false; 否则返回 true。如果该名称对应多个应用，则只启动其中某一个。
 * @param targetAppName 应用名称
 */
export function launchApp(targetAppName: string): boolean {
    return appUtils.launchApp(targetAppName)
}
/**
 * 打开应用的详情页(设置页)。如果找不到该应用，返回 false; 否则返回 true。
 * @param packageName 应用包名 
 */
export function openAppSettings(packageName: string): boolean {
    return appUtils.openAppSetting(packageName)
}
/**
 * 用浏览器打开网站 url。
 * 如果没有安装浏览器应用，则抛出ActivityNotException。
 * @param url 网站的 Url，如果不以"http://"或"https://"开头则默认是"http://"。
 */
export function openUrl(url: string): void {
    appUtils.openUrl(url)
}
/**
 * 解析 uri 字符串并返回相应的 Uri 对象。即使 Uri 格式错误，该函数也会返回一个 Uri 对象，
 * 但之后如果访问该对象的 scheme, path 等值可能因解析失败而返回null。
 * 
 * 需要注意的是，在高版本 Android 上，由于系统限制直接在 Uri 暴露文件的绝对路径，
 * 因此如果 uri 字符串是文件file://...，返回的 Uri 会是诸如content://...的形式。
 * @param uri uri字符串
 * @returns 一个指向该文件的 Uri 的对象，参见[android.net.Uri](https://developer.android.com/reference/android/net/Uri)
 */
export function parseUri(uri: string): android.Uri {
    if (uri.startsWith("file://")) {
        return getUriForFile(uri);
    }
    return android.net.Uri.parse(uri)
}
/**
 * 根据选项构造一个 Intent，并发送该广播。
 * @param options Intent构造选项，参见{@link makeIntent}
 */
export function sendBroadcast(options: IntentOptions) {
    return context.sendBroadcast(makeIntent(options))
}
/**
 * 根据选项options调用邮箱应用发送邮件。这些选项均是可选的。
 * 如果没有安装邮箱应用，则抛出ActivityNotException。
 * @param options 发送邮件的参数
 */
export function sendEmail(options: EmailOptions): void {
    const intent = new Intent()
    intent.data = Uri.parse("mailto:")
    if (options.email) {
        intent.putExtra(Intent.EXTRA_EMAIL, toArray(options.email));
    }
    if (options.cc) {
        intent.putExtra(Intent.EXTRA_CC, toArray(options.cc));
    }
    if (options.bcc) {
        intent.putExtra(Intent.EXTRA_BCC, toArray(options.bcc));
    }
    if (options.subject) {
        intent.putExtra(Intent.EXTRA_SUBJECT, options.subject);
    }
    if (options.text) {
        intent.putExtra(Intent.EXTRA_TEXT, options.text);
    }
    if (options.attachment) {
        intent.putExtra(Intent.EXTRA_STREAM, parseUri(options.attachment));
    }
    intent.setType("message/rfc822");
    context.startActivity(Intent.createChooser(intent, "发送邮件")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
}
/**
 * 根据选项构造一个 Intent，并启动该 Activity。
 * @param target Intent构造选项，参见{@link makeIntent}
 */
export function startActivity(target: IntentOptions) {
    const intent = makeIntent(target)
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
/**
 * 根据选项构造一个 Intent，并启动该服务。
 * @param target Intent构造选项，参见{@link makeIntent}
 */
export function startService(target: IntentOptions) {
    const intent = makeIntent(target)
}
/**
 * 卸载应用。执行后会会弹出卸载应用的提示框。
 * 如果该包名的应用未安装，由应用卸载程序处理，可能弹出"未找到应用"的提示。
 * @example
 * ```js
 * import { uninstall } from 'app
 * //卸载QQ
 * uninstall("com.tencent.mobileqq");
 * ```
 * @param packageName 应用包名
 */
export function uninstall(packageName: string): void {
    appUtils.uninstall(packageName)
}

function toArray(value: string | string[]): string[] {
    if (value instanceof Array) {
        return value
    } else return [value]
}
function parseIntentFlag(flag: string) {
    if (typeof (flag) == 'string') {
        return android.content.Intent["FLAG_" + flag.toUpperCase()];
    }
    return flag;
}

export * from './types'