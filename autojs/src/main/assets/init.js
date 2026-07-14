var global = this;

runtime.init();

(function () {
    //重定向importClass使得其支持字符串参数
    global.importClass =
        (function () {
            var __importClass__ = importClass;
            return function (pack) {
                if (typeof (pack) == "string") {
                    __importClass__(Packages[pack]);
                } else {
                    __importClass__(pack);
                }
            }
        })();

    //内部函数
    global.__asGlobal__ = function (obj, functions) {
        var len = functions.length;
        for (var i = 0; i < len; i++) {
            var funcName = functions[i];
            var func = obj[funcName]
            if (!func) {
                continue;
            }
            global[funcName] = func.bind(obj);
        }
    }


    // 初始化基础模块
    global.timers = require('__timers__.js')(runtime, global);

    //初始化不依赖环境的模块
    global.util = global.$util = require('__util__.js');
    global.device = runtime.device;
    global.keyboard = Object.create(runtime.keyboard);

    global.process = require('process')
    // global.Promise = require('bluebird');


    //初始化全局函数
    require("__globals__")(runtime, global);

    require("object-observe-lite.min")();
    require("array-observe.min")();
    //初始化一般模块
    (function (scope) {
        var modules = ['console', "RootAutomator"];
        var len = modules.length;
        for (var i = 0; i < len; i++) {
            var m = modules[i];
            let module = require('__' + m + '__')(scope.runtime, scope);
            scope[m] = module;
            if (!m.startsWith('$')) {
                scope['$' + m] = module;
            }
        }
    })(global);

    require("/android_asset/v6modules/init.js")

})();


