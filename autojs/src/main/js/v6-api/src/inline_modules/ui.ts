import { exitIfError } from "@/utils"
import './ui_jsx'

export type XML = {
    toXMLString(): string
}


export interface Ui {
    [key: string]: any
    view?: android.View
    run(a: () => void): void
    post(a: () => void, delay?: number): void
    layout(content: XML | string): void
    layoutFile(file: string): void
    inflate(xml: XML | string, parent: any, attachToParent: boolean): any
    registerWidget(name: string, widget: () => any): any
    setContentView(view: android.View): void
    findById: (id: string) => android.View | null
    isUiThread(): boolean
    finish: () => void
    statusBarColor: (color: string | number) => void
    findByStringId: (view: android.View, id: string) => android.View | null
    findView: (id: string) => android.View | null
    __inflate__: (ctx: android.Context, xml: XML | string, parent: android.View, attachToParent: boolean) => any
}

declare var activity: any
declare var threads: any
const isUiThread = function (): boolean {
    const Looper = android.os.Looper;
    return Looper.myLooper() == Looper.getMainLooper();
}

const statusBarColor = function (color: string | number) {
    if (typeof (color) == 'string') {
        color = android.graphics.Color.parseColor(color);
    }
    if (android.os.Build.VERSION.SDK_INT >= 21) {
        ui.run(function () {
            activity.getWindow().setStatusBarColor(color);
        });
    }
}

const finish = function () {
    ui.run(function () {
        activity.finish();
    });
}

const __inflate__ = function (ctx: android.Context, xml: XML | string,
    parent: android.View, attachToParent: boolean) {
    if (typeof (xml) !== 'string') {
        xml = xml.toXMLString();
    }
    parent = parent || null;
    attachToParent = !!attachToParent;
    return runtime.ui.layoutInflater.inflate(ctx, xml.toString(), parent, attachToParent);
}


const findView = function (id: string): android.View | null {
    return ui.findById(id);
}

const run = function (action: () => void) {
    if (ui.isUiThread()) {
        return action();
    }
    var err = null;
    var result;
    var disposable = threads.disposable();
    runtime.getUiHandler().post(function () {
        try {
            result = action();
            disposable.setAndNotify(true);
        } catch (e) {
            err = e;
            disposable.setAndNotify(true);
        }
    });
    disposable.blockedGet();
    if (err) {
        throw err;
    }
    return result;
}

const post = function (action: () => void, delay?: number) {
    if (delay == undefined) {
        runtime.getUiHandler().post(wrapUiAction(action));
    } else {
        runtime.getUiHandler().postDelayed(wrapUiAction(action), delay);
    }
}
class Widget {
    __attrs__: any = {}
    renderInternal() {
        return this.render();
    }
    render() { return eval('< />') }
    defineAttr(attrName: string, getter: unknown, setter: any) {
        var attrAlias = attrName;
        var applier: any = null;
        if (typeof (getter) == 'string') {
            attrAlias = getter
            if (arguments.length >= 3) {
                applier = setter;
            }
        } else if (typeof (getter) == 'function' && typeof (setter) != 'function') {
            applier = getter;
        }
        if (!(typeof (getter) == 'function' && typeof (setter) == 'function')) {
            const self = this as any;
            getter = () => {
                return self[attrAlias];
            };
            setter = (view: unknown, attrName: unknown, value: unknown, setter: unknown) => {
                self[attrAlias] = value;
                if (applier) applier(view, attrName, value, setter);
            };
        }
        this.__attrs__[attrName] = {
            getter: getter,
            setter: setter
        };
    };
    hasAttr(attrName: string) {
        return Object.prototype.hasOwnProperty.call(this.__attrs__, attrName);
    };
    setAttr(view: unknown, attrName: string, value: unknown, setter: unknown) {
        this.__attrs__[attrName].setter(view, attrName, value, setter);
    };
    getAttr(view: unknown, attrName: string, getter: unknown) {
        return this.__attrs__[attrName].getter(view, attrName, getter);
    };
    notifyViewCreated(view: unknown) {
        if (typeof (this.onViewCreated) == 'function') {
            this.onViewCreated(view);
        }
    };
    onViewCreated(_view: unknown) { }
    notifyAfterInflation(view: unknown) {
        if (typeof (this.onFinishInflation) == 'function') {
            this.onFinishInflation(view);
        }
    }
    onFinishInflation(_view: unknown) { }
}

var ui: Ui = {
    __widgets__: {}, __inflate__, Widget,
    run, post, isUiThread, finish, statusBarColor, findView,
    layout: function (xml) {
        if (typeof (activity) == 'undefined') {
            throw new Error("需要在ui模式下运行才能使用该函数");
        }
        runtime.ui.layoutInflater.setContext(activity);
        var view = runtime.ui.layoutInflater.inflate(xml, activity.window.decorView, false);
        ui.setContentView(view);
    },
    layoutFile: function (file: string) {
        ui.layout(files.read(file));
    },
    inflate: function (xml: XML | string, parent: any, attachToParent: boolean) {
        if (typeof (xml) !== 'string') {
            xml = xml.toXMLString();
        }
        parent = parent || null;
        attachToParent = !!attachToParent;
        let ctx;
        if (typeof (activity) == 'undefined') {
            ctx = new android.view.ContextThemeWrapper(context, com.stardust.autojs.R.style.ScriptTheme);
        } else {
            ctx = activity;
        }
        runtime.ui.layoutInflater.setContext(ctx);
        return runtime.ui.layoutInflater.inflate(xml.toString(), parent, attachToParent);
    },
    registerWidget: function (name: string, widget: () => any) {
        if (typeof (widget) !== 'function') {
            throw new TypeError('widget should be a class-like function');
        }
        ui.__widgets__[name] = widget;
    },
    setContentView: function (view: android.View) {
        ui.view = view;
        ui.run(function () {
            activity.setContentView(view);
        });
    },
    findById: function (id: string) {
        if (!ui.view)
            return null;
        return ui.findByStringId(ui.view, id);
    },
    findByStringId: function (view: android.View, id: string): android.View {
        return com.stardust.autojs.core.ui.JsViewHelper.findViewByStringId(view, id);
    },
    h: (function () {
        return eval(`
            function h(tag) {
  let attrs = {};
  let children = [];
  if (typeof arguments[1] === "object") {
    attrs = arguments[1]||{};
    children = Array.prototype.slice.call(arguments, 2);
  } else {
    children = Array.prototype.slice.call(arguments, 1);
  }
  // 创建 XML 标签
  let xmlElement = new XML('<' + tag + ' />');
  // 设置属性
  for (let [key, value] of Object.entries(attrs)) {
    xmlElement.@[key] = value;
  }

  // 添加子元素
  children.forEach((child) => {
    if (typeof child === "string") {
      xmlElement.appendChild(child); // 处理文本节点
    } else {
      xmlElement.appendChild(child); // 处理 XML 对象
    }
  });
  return xmlElement;
}
            `)
    })()
};
ui.__defineGetter__("emitter", () => activity ? activity.getEventEmitter() : null);


runtime.ui.bindingContext = global;
var layoutInflater = runtime.ui.layoutInflater;
layoutInflater.setLayoutInflaterDelegate({
    beforeConvertXml: function () {
        return null;
    },
    afterConvertXml: function (_context: any, xml: string) {
        return xml;
    },
    afterInflation: function (context: unknown, result: unknown) {
        return result;
    },
    beforeInflation: function () {
        return null;
    },
    beforeInflateView: function () {
        return null;
    },
    afterInflateView: function (context: any, view: any) {
        const widget = view.widget;
        if (widget && context.get("root") != widget) {
            widget.notifyAfterInflation(view);
        }
        return view;
    },
    beforeCreateView: function (context: unknown, node: unknown, viewName: string, parent: android.View) {
        if (Object.prototype.hasOwnProperty.call(ui.__widgets__, viewName)) {
            var Widget = ui.__widgets__[viewName];
            var widget = new Widget();
            var ctx = layoutInflater.newInflateContext();
            ctx.put("root", widget);
            ctx.put("widget", widget);
            var view = __inflate__(ctx, widget.renderInternal(), parent, false);
            return view;
        };
        return null;
    },
    afterCreateView: function (context: any, view: any, node: unknown, viewName: unknown, parent: unknown, attrs: unknown) {
        if (view.getClass().getName() == "com.stardust.autojs.core.ui.widget.JsListView" ||
            view.getClass().getName() == "com.stardust.autojs.core.ui.widget.JsGridView") {
            initListView(view);
        }
        var widget = context.get("widget");
        if (widget != null) {
            widget.view = view;
            view.widget = widget;
            const viewAttrs = com.stardust.autojs.core.ui.ViewExtras.getViewAttributes(view, layoutInflater.resourceParser);
            viewAttrs.setViewAttributeDelegate({
                has: function (name: string) {
                    return widget.hasAttr(name);
                },
                get: function (view: unknown, name: unknown, getter: unknown) {
                    return widget.getAttr(view, name, getter);
                },
                set: function (view: unknown, name: unknown, value: unknown, setter: unknown) {
                    widget.setAttr(view, name, value, setter);
                }
            });
            widget.notifyViewCreated(view);
        }
        return view;
    },
    beforeApplyAttributes: function () {
        return false;
    },
    afterApplyAttributes: function (context: any) {
        context.remove("widget");
    },
    beforeInflateChildren: function () {
        return false;
    },
    afterInflateChildren: function () { },
    afterApplyPendingAttributesOfChildren: function () { },
    beforeApplyPendingAttributesOfChildren: function () {
        return false;
    },
    beforeApplyAttribute: function (context: any, inflater: any, view: any, ns: any,
        attrName: string, value: any, parent: unknown, attrs: unknown) {
        var isDynamic = layoutInflater.isDynamicValue(value);
        if ((isDynamic && layoutInflater.getInflateFlags() == layoutInflater.FLAG_IGNORES_DYNAMIC_ATTRS)
            || (!isDynamic && layoutInflater.getInflateFlags() == layoutInflater.FLAG_JUST_DYNAMIC_ATTRS)) {
            return true;
        }
        value = bind(value);
        const widget = context.get("widget");
        if (widget != null && widget.hasAttr(attrName)) {
            widget.setAttr(view, attrName, value, (view: unknown, attrName: unknown, value: unknown) => {
                inflater.setAttr(view, ns, attrName, value, parent, attrs);
            });
        } else {
            inflater.setAttr(view, ns, attrName, value, parent, attrs);
        }
        this.afterApplyAttribute(context, inflater, view, ns, attrName, value, parent, attrs);
        return true;
    },
    afterApplyAttribute: function () { }
});

function bind(value: string) {
    var ctx = runtime.ui.bindingContext;
    if (ctx == null)
        return;
    var i = -1;
    while ((i = value.indexOf("{{", i + 1)) >= 0) {
        var j = value.indexOf("}}", i + 1);
        if (j < 0)
            return value;
        value = value.substring(0, i) +
            evalInContext(value.substring(i + 2, j), ctx) +
            value.substring(j + 2);
        i = j + 1;
    }
    return value;
}

function evalInContext(expr: string, ctx: any) {
    return exitIfError(function () {
        return runtime.evalInContext(expr, ctx);
    });
}


function initListView(list: any) {
    list.setDataSourceAdapter({
        getItemCount: function (data: unknown[]) {
            return data.length;
        },
        getItem: function (data: unknown[], i: number) {
            return data[i];
        },
        setDataSource: function (data: unknown) {
            var adapter = list.getAdapter();
            (Array as any).observe(data, function (changes: any[]) {
                changes.forEach(change => {
                    if (change.type == 'splice') {
                        if (change.removed && change.removed.length > 0) {
                            adapter.notifyItemRangeRemoved(change.index, change.removed.length);
                        }
                        if (change.addedCount > 0) {
                            adapter.notifyItemRangeInserted(change.index, change.addedCount);
                        }
                    } else if (change.type == 'update') {
                        try {
                            adapter.notifyItemChanged(parseInt(change.name));
                        } catch (e) {/** */ }
                    }
                });
            });
        }
    });
}

function wrapUiAction(action: () => void, defReturnValue?: unknown) {
    if (typeof (activity) != 'undefined') {
        return function () { return action(); };
    }
    return function () {
        return exitIfError(action, defReturnValue);
    }
}




var proxy = runtime.ui;
proxy.__proxy__ = {
    set: function (name: string, value: unknown) {
        ui[name] = value;
    },
    get: function (name: string) {
        if (!ui[name] && ui.view) {
            const v = ui.findById(name);
            if (v) {
                return v;
            }
        }
        return ui[name];
    }
};


export default proxy;