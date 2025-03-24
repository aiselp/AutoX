

const s = runtime.shizuku;
export default function shizuku(cmd: string) {
    return s.runShizukuShellCommand(cmd)
}

shizuku.isAlive = function isAlive(): boolean {
    return s.isShizukuAlive()
}

shizuku.openAccessibility = function openAccessibility() {
    s.openAccessibility()
}

shizuku.runRhinoScriptFile = function runRhinoScriptFile(path: string) {
    const data = s.runRhinoScriptFile(path);
    if (!data) {
        return data;
    } else return JSON.parse(data);
}

shizuku.runRhinoScript = function runRhinoScript(script: string) {
    const data = s.runRhinoScript(script);
    if (!data) {
        return data;
    } else return JSON.parse(data);
}

