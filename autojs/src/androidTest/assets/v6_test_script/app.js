
let gl = ['launchPackage', 'launch', 'launchApp', 'getPackageName', 'getAppName', 'openAppSetting']

for (let i = 0; i < gl.length; i++) {
    console.assert(typeof global[gl[i]] === 'function')
}

console.assert(typeof app.versionCode === 'number')
// console.assert(typeof app.versionName === 'string')
console.assert(typeof app.autojs.versionCode === 'number')
console.assert(typeof app.autojs.versionName === 'string')

let appFun = ['launchApp', 'launch', 'launchPackage', 'getPackageName', 'getAppName', 'openAppSetting',
    'viewFile', 'editFile', 'uninstall', 'openUrl', 'sendEmail', 'startActivity', 'intent', 'sendBroadcast',
    'startService', 'intentToShell', 'parseUri', 'getUriForFile'
]

for (let i = 0; i < appFun.length; i++) {
    console.assert(typeof app[appFun[i]] === 'function')
}