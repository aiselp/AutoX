const __runtime__ = runtime
const scope = global

declare global {
    var KeyCode: (keyCode: number) => void
    var Home: () => void
    var Back: () => void
    var Power: () => void
    var Up: () => void
    var Down: () => void
    var Left: () => void
    var Right: () => void
    var OK: () => void
    var VolumeUp: () => void
    var VolumeDown: () => void
    var Menu: () => void
    var Camera: () => void
    var Text: (text: string) => void
    var SetScreenMetrics: (w: number, h: number) => void
    var Tap: (x: number, y: number) => void
    var Swipe: (x1: number, y1: number, x2: number, y2: number, duration: number) => void
    var Screencap: (path: string) => void
    var Input: (text: string) => void
    var Shell: (root: boolean) => Autox.Shell2
}
const KeyCode = function (keyCode: number) {
    __runtime__.shell.exec("input keyevent " + keyCode, true);
}

scope.SetScreenMetrics = function (w: number, h: number) {
    //__runtime__.getRootShell().SetScreenMetrics(w, h);
}

scope.Tap = function (x: number, y: number) {
    __runtime__.shell.exec(`input tap ${x} ${y}`, true);
}

scope.Swipe = function (x1: number, y1: number, x2: number, y2: number, duration: number) {
    if (arguments.length == 5) {
        __runtime__.shell.exec(`input swipe ${x1} ${y1} ${x2} ${y2} ${duration}`, true);
    } else {
        __runtime__.shell.exec(`input swipe ${x1} ${y1} ${x2} ${y2}`, true);
    }
}

scope.Screencap = function (path: string) {
    __runtime__.shell.exec(`screencap -p '${path}'`, true);
}

scope.KeyCode = KeyCode

scope.Home = function () {
    return KeyCode(3);
}

scope.Back = function () {
    return KeyCode(4);
}

scope.Power = function () {
    return KeyCode(26);
}

scope.Up = function () {
    return KeyCode(19);
}

scope.Down = function () {
    return KeyCode(20);
}

scope.Left = function () {
    return KeyCode(21);
}

scope.Right = function () {
    return KeyCode(22);
}

scope.OK = function () {
    return KeyCode(23);
}

scope.VolumeUp = function () {
    return KeyCode(24);
}

scope.VolumeDown = function () {
    return KeyCode(25);
}

scope.Menu = function () {
    return KeyCode(1);
}

scope.Camera = function () {
    return KeyCode(27);
}

scope.Text = function (text: string) {
    __runtime__.shell.exec(`input text '${text}'`, true);
}

scope.Input = scope.Text;

scope.Shell = function (root: boolean) {
    return __runtime__.shell.createShell(!!root);
}

export default function (cmd: string, root: boolean) {
    return __runtime__.shell.exec(cmd, !!root);
};