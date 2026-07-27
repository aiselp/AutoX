var rtEngines = runtime.engines;

export interface EngineConfig {
    delay?: number;
    loopTimes?: number;
    interval?: number;
    path?: string;
    arguments?: { [key: string]: any };
}
const engines = {
    execScript, execScriptFile, execAutoFile, myEngine, all,
    stopAll: rtEngines.stopAll.bind(rtEngines),
    stopAllAndToast: rtEngines.stopAllAndToast.bind(rtEngines)
};


function execScript(name: string, script: string, config?: EngineConfig) {
    return rtEngines.execScript(name, script, fillConfig(config));
}

function execScriptFile(path: string, config?: EngineConfig) {
    return rtEngines.execScriptFile(path, fillConfig(config));
}
function execAutoFile(path: string, config?: EngineConfig) {
    return rtEngines.execAutoFile(path, fillConfig(config));
}

function myEngine() {
    return rtEngines.myEngine();
}

function all() {
    return rtEngines.all();
}


function fillConfig(c?: EngineConfig) {
    var config = new com.stardust.autojs.execution.ExecutionConfig();
    c = c || {};
    c.path = c.path || files.cwd();
    if (c.path) {
        config.workingDirectory = c.path;
    }
    config.delay = c.delay || 0;
    config.interval = c.interval || 0;
    config.loopTimes = (c.loopTimes === undefined) ? 1 : c.loopTimes;
    if (c.arguments) {
        var args = c.arguments;
        for (var key in args) {
            if (Object.prototype.hasOwnProperty.call(args, key)) {
                config.setArgument(key, args[key]);
            }
        }
    }
    return config;
}

var engine = engines.myEngine();
var execArgv: { [key: string]: any } = {};
var iterator = (engine as any).getTag("execution.config").arguments.entrySet().iterator();
while (iterator.hasNext()) {
    var entry = iterator.next();
    execArgv[entry.getKey()] = entry.getValue();
}
(engine as any).execArgv = execArgv;

export default engines;
