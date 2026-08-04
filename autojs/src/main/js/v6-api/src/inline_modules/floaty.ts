import { XML } from "@/ui";

function window(xml: XML | string) {
    if (typeof (xml) !== 'string') {
        xml = xml.toXMLString();
    }
    let window;
    ui.run(() => {
        window = wrap(runtime.floaty.window(function (context: android.Context, parent: unknown) {
            runtime.ui.layoutInflater.setContext(context);
            return runtime.ui.layoutInflater.inflate(xml.toString(), parent, true);
        }));
    })
    return window;
}

function rawWindow(xml: XML | string) {
    if (typeof (xml) !== 'string') {
        xml = xml.toXMLString();
    }
    let window;
    ui.run(() => {
        window = wrap(runtime.floaty.rawWindow(function (context: android.Context, parent: unknown) {
            runtime.ui.layoutInflater.setContext(context);
            return runtime.ui.layoutInflater.inflate(xml.toString(), parent, true);
        }));
    })
    return window;
}

function wrap(window: any) {
    var proxyObject = new com.stardust.autojs.rhino.ProxyJavaObject(global, window, window.getClass());
    var viewCache = {};
    proxyObject.__proxy__ = {
        set: function (name: string, value: unknown) {
            window[name] = value;
        },
        get: function (name: string) {
            var value = window[name];
            if (typeof (value) == 'undefined') {
                if (!value) {
                    value = window.findView(name);
                }
                if (!value) {
                    value = undefined;
                }
            }
            return value;
        }
    };
    return proxyObject;
}

var closeAll = runtime.floaty.closeAll.bind(runtime.floaty);

var checkPermission = runtime.floaty.checkPermission.bind(runtime.floaty);

var requestPermission = runtime.floaty.requestPermission.bind(runtime.floaty);

var floaty = {
    window: window,
    rawWindow: rawWindow,
    closeAll: closeAll,
    checkPermission,
    requestPermission
};
export default floaty;