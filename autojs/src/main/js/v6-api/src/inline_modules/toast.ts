import { setGlobal } from "@/utils";

const _runtime = runtime as any
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

/**
 * 以气泡显示信息 message 几秒。(具体时间取决于安卓系统，一般都是 2 秒)。
 * 注意，信息的显示是"异步"执行的。
 * @param message 要显示的信息
 */
export function toast(text: string) {
    _runtime.toast(text);
}

/** 设置自定义 Toast 的默认配置，后续 toast.show() 未指定的字段沿用该配置。 */
toast.init = function (config: CustomToastConfig) {
    _runtime.toastX.init(config);
}
/**
     * 以自定义样式显示消息（队列化）。
     * @param message 要显示的信息
     * @param config 本次显示的配置，未指定的字段沿用 toast.init() 设置的默认值
 */
toast.show = function (message: string, config?: CustomToastConfig) {
    if (arguments.length >= 2) {
        _runtime.toastX.show(message, config);
    } else {
        _runtime.toastX.show(message);
    }
}
/**
 * 相当于 toast(message); log(message)。
 * 显示信息 message 并在控制台中输出。
 * @param message 要显示的信息
 */
export function toastLog(text: string) {
    _runtime.toast(text);
    global.log(text);
}

type _toast = typeof toast
type _toastLog = typeof toastLog

declare global {
    /**
        * 以气泡显示信息 message 几秒。(具体时间取决于安卓系统，一般都是 2 秒)。
        * 注意，信息的显示是"异步"执行的。
        * @param message 要显示的信息
    */
    var toast: _toast
    /**
        * 相当于 toast(message); log(message)。
        * 显示信息 message 并在控制台中输出。
        * @param message 要显示的信息
    */
    var toastLog: _toastLog
}

setGlobal({
    toast, toastLog
})
