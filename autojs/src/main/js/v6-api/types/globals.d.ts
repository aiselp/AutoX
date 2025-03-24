

declare var global = globalThis

declare var android, java, com, Packages
/**
 * @param message 要显示的信息
 */
declare function toast(message: string): void
/**
 * @param message 要显示的信息
 */
declare function toastLog(message: string): void

/**
 * 一个 android.content.Context 对象。
 */
declare var context: android.Context

declare var runtime: Autox.Runtime