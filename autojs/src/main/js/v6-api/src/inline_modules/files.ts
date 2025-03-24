import { setGlobalAnd$ } from '../utils'
const scope = global
const fs = runtime.files;

const files = Object.create(fs)
files.join = function (base: string, ...paths: string[]) {
    return fs.join(base, ...paths);
}
scope.files = files;
scope.$files = files;
setGlobalAnd$('io', files)

declare global {
    var open: (path: string, mode?: FileMode, encoding?: string, bufferSize?: number) => any
    var files: Autox.Files
    var $files: Autox.Files
}
export type FileMode = 'r' | 'w' | 'a' | 'rw'
scope.open = function (path: string, mode?: FileMode,
    encoding?: string, bufferSize?: number) {
    if (arguments.length == 1) {
        return files.open(path);
    } else if (arguments.length == 2) {
        return files.open(path, mode);
    } else if (arguments.length == 3) {
        return files.open(path, mode, encoding);
    } else if (arguments.length == 4) {
        return files.open(path, mode, encoding, bufferSize);
    }
};