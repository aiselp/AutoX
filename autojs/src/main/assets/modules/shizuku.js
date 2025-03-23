
let __runtime = runtime
let __context = context
let shizuku = runtime.shizuku

exports = module.exports = function(cmd){
    return shizuku.runShizukuShellCommand(cmd);
}
/*
exports.createShell = function() {

}*/

exports.isAlive = function(){
    return shizuku.isShizukuAlive();
}

exports.openAccessibility = function(){
    shizuku.openAccessibility();
}

exports.runRhinoScriptFile = function(path){
    let data =  shizuku.runRhinoScriptFile(path);
    if (!data){
        return data;
    }else return JSON.parse(data);
}

exports.runRhinoScript = function(script){
    let data = __runtime.shizuku.runRhinoScript(script);
    if (!data){
       return data;
    }else return JSON.parse(data);
}