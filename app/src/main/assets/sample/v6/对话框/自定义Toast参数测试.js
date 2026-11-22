"ui";

// 自定义 Toast 参数测试面板
// toast.init(config) / toast.show(msg[, config]) 的全参数测试。
// 每个按钮只测对应项；底部「测试全部」按顺序自动跑完。
// 需要「显示在其他应用上层」权限。
// icon 使用项目内 app 图标：app/src/main/res/mipmap-mdpi/ic_launcher.png

var G = android.view.Gravity;

var APP_ICON_B64 = [
    "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"
].join("");
var APP_ICON_URI = "data:image/png;base64," + APP_ICON_B64;

function setStatus(s) {
    log(s);
    try {
        ui.run(function () {
            ui.status.setText(s);
        });
    } catch (e) {}
}

function resetBuiltin() {
    toast.init({
        log: false,
        duration: 2000,
        gravity: G.BOTTOM,
        xOffset: 0,
        yOffset: 150,
        alpha: 1,
        bg: colors.parseColor("#DD333333"),
        textColor: colors.parseColor("#FFFFFF"),
        textSize: 16,
        radius: 8,
        padding: 8,
        icon: null,
        override: false
    });
}

function setupRed() {
    resetBuiltin();
    toast.init({
        bg: colors.parseColor("#E53935"),
        textColor: colors.parseColor("#FFFFFF"),
        textSize: 22,
        radius: 2,
        duration: 3500
    });
}

// ---------- 各项测试：点击按钮只执行对应一项 ----------
function test0() {
    setStatus("0 默认 show：底部、深灰底白字、2秒、圆角8dp");
    resetBuiltin();
    toast.show("0 默认样式（reset 后 show）");
}

function test1() {
    setStatus("1 toast(msg)：应为系统原生小黑条，不受 init 影响");
    toast("1 系统 Toast");
}

function test1b() {
    setStatus("1b init 前默认：应与内置默认一致");
    resetBuiltin();
    toast.show("1b init 前的默认样式");
}

function test2() {
    setStatus("2 init：红底、白字、22sp、圆角2dp、3500ms");
    setupRed();
    toast.show("2 init 后的默认样式");
}

function test3() {
    setStatus("3 show 单次 bg=绿，其余沿用 init（仍是22sp方角、约3.5秒）");
    setupRed();
    toast.show("3 单次覆盖 bg=绿", {
        bg: colors.parseColor("#43A047")
    });
}

function test4() {
    setStatus("4 duration=500ms：应明显很短");
    setupRed();
    toast.show("4 短时长 500ms", { duration: 500 });
}

function test5() {
    setStatus("5 duration=5000ms：应明显很长");
    setupRed();
    toast.show("5 长时长 5000ms", { duration: 5000 });
}

function test6() {
    setStatus("6 gravity=TOP、yOffset=200：应出现在屏幕上方");
    setupRed();
    toast.show("6 顶部 + yOffset 200", {
        gravity: G.TOP,
        yOffset: 200,
        duration: 2500
    });
}

function test7() {
    setStatus("7 gravity=CENTER、x/y=0：应居中");
    setupRed();
    toast.show("7 居中", {
        gravity: G.CENTER,
        xOffset: 0,
        yOffset: 0,
        duration: 2500
    });
}

function test8() {
    setStatus("8 alpha=0.3：应明显半透明");
    setupRed();
    toast.show("8 半透明蓝", {
        gravity: G.CENTER,
        alpha: 0.3,
        bg: colors.parseColor("#1976D2"),
        duration: 2500
    });
}

function test9() {
    setStatus("9 padding=24：文字四周留白应明显变大");
    setupRed();
    toast.show("9 大内边距", {
        gravity: G.CENTER,
        padding: 24,
        bg: colors.parseColor("#6A1B9A"),
        duration: 2500
    });
}

function test10() {
    setStatus("10 textColor=黄色：文字应变黄");
    setupRed();
    toast.show("10 黄色文字", {
        gravity: G.CENTER,
        bg: colors.parseColor("#212121"),
        textColor: colors.parseColor("#FFEB3B"),
        duration: 2500
    });
}

function test11() {
    setStatus("11 icon=app 图标 base64：文字左侧应出现 AutoX 图标");
    log("APP_ICON_B64 长度=" + APP_ICON_B64.length);
    setupRed();
    toast.show("11 app 图标 base64：左侧应有图标", {
        gravity: G.CENTER,
        icon: APP_ICON_B64,
        bg: colors.parseColor("#37474F"),
        duration: 3000
    });
}

function test12() {
    setStatus("12 icon=data URI 前缀：左侧应同样出现 AutoX 图标");
    log("APP_ICON_URI 前缀=" + APP_ICON_URI.substring(0, Math.min(30, APP_ICON_URI.length)));
    setupRed();
    toast.show("12 data URI 前缀图标", {
        gravity: G.CENTER,
        icon: APP_ICON_URI,
        bg: colors.parseColor("#37474F"),
        duration: 3000
    });
}

function test12b() {
    setStatus("12b 先显示图标，再用 icon:null 清空：第二条左侧不应有图标");
    setupRed();
    toast.show("12b-1 先显示 app 图标", {
        gravity: G.CENTER,
        icon: APP_ICON_B64,
        bg: colors.parseColor("#37474F"),
        duration: 3000
    });
    toast.show("12b-2 icon:null 应清空图标", {
        gravity: G.CENTER,
        icon: null,
        bg: colors.parseColor("#37474F"),
        duration: 3000
    });
}

function test13() {
    setStatus("13 log=true：控制台应同时打印这条文字");
    setupRed();
    toast.show("13 log=true 应同时输出到控制台", {
        gravity: G.CENTER,
        log: true,
        duration: 2000
    });
}

function test14() {
    setStatus("14 队列：连发 3 条，应依次各显示约 2 秒");
    setupRed();
    toast.show("14-1 队列第一条", { gravity: G.CENTER, duration: 2000 });
    toast.show("14-2 队列第二条", { gravity: G.CENTER, duration: 2000 });
    toast.show("14-3 队列第三条", { gravity: G.CENTER, duration: 2000 });
}

function test15() {
    setStatus("15 override：先发普通 toast，再 override 应清掉前面所有 toast");
    setupRed();
    toast.show("15-0 普通 toast，应被清掉", { gravity: G.CENTER, duration: 5000 });
    toast.show("15-1 应被后一条清掉", { gravity: G.CENTER, override: true, duration: 5000 });
    toast.show("15-2 只应看到这一条", { gravity: G.CENTER, override: true, duration: 3000 });
}

function test16() {
    setStatus("16 init 累积：先 init duration=3000，再 init bg=蓝，应蓝底约 3 秒");
    resetBuiltin();
    toast.init({ duration: 3000, gravity: G.CENTER });
    toast.init({ bg: colors.parseColor("#1565C0") });
    toast.show("16 蓝底约3秒（验证 init 累积覆盖）");
}

function test99() {
    setStatus("99 恢复内置默认样式");
    resetBuiltin();
    toast.show("99 已恢复内置默认样式");
}

// ---------- UI ----------
ui.layout(
    <vertical padding="8">
        <text id="status" text="点任意按钮测试对应项；底部按钮自动跑全部。" textSize="14sp" textColor="#333333" padding="8" w="*"/>
        <ScrollView layout_width="match_parent" layout_height="0dp" layout_weight="1">
            <vertical>
                <button id="t0" text="0  默认 show（内置默认）" w="*"/>
                <button id="t1" text="1  系统 toast(msg)" w="*"/>
                <button id="t1b" text="1b init 前默认" w="*"/>
                <button id="t2" text="2  init 设置默认样式" w="*"/>
                <button id="t3" text="3  show 单次配置覆盖" w="*"/>
                <button id="t4" text="4  duration 500ms" w="*"/>
                <button id="t5" text="5  duration 5000ms" w="*"/>
                <button id="t6" text="6  位置 TOP + yOffset" w="*"/>
                <button id="t7" text="7  位置 CENTER" w="*"/>
                <button id="t8" text="8  alpha 半透明" w="*"/>
                <button id="t9" text="9  padding 大内边距" w="*"/>
                <button id="t10" text="10 textColor 黄色文字" w="*"/>
                <button id="t11" text="11 icon = app 图标 base64" w="*"/>
                <button id="t12" text="12 icon = data URI 前缀" w="*"/>
                <button id="t12b" text="12b icon:null 清空图标" w="*"/>
                <button id="t13" text="13 log=true 控制台输出" w="*"/>
                <button id="t14" text="14 队列（3 条依次显示）" w="*"/>
                <button id="t15" text="15 override（只显示最后一条）" w="*"/>
                <button id="t16" text="16 init 累积覆盖" w="*"/>
                <button id="t99" text="99 恢复内置默认" w="*"/>
            </vertical>
        </ScrollView>
        <button id="run_all" text="测试全部（自动依次执行）" w="*" style="Widget.AppCompat.Button.Colored"/>
    </vertical>
);

ui.t0.click(test0);
ui.t1.click(test1);
ui.t1b.click(test1b);
ui.t2.click(test2);
ui.t3.click(test3);
ui.t4.click(test4);
ui.t5.click(test5);
ui.t6.click(test6);
ui.t7.click(test7);
ui.t8.click(test8);
ui.t9.click(test9);
ui.t10.click(test10);
ui.t11.click(test11);
ui.t12.click(test12);
ui.t12b.click(test12b);
ui.t13.click(test13);
ui.t14.click(test14);
ui.t15.click(test15);
ui.t16.click(test16);
ui.t99.click(test99);

var allTests = [
    ["0", test0, 2600],
    ["1", test1, 2600],
    ["1b", test1b, 2600],
    ["2", test2, 4000],
    ["3", test3, 4000],
    ["4", test4, 1200],
    ["5", test5, 5600],
    ["6", test6, 3200],
    ["7", test7, 3200],
    ["8", test8, 3200],
    ["9", test9, 3200],
    ["10", test10, 3200],
    ["11", test11, 3500],
    ["12", test12, 3500],
    ["12b", test12b, 7000],
    ["13", test13, 2800],
    ["14", test14, 7500],
    ["15", test15, 4000],
    ["16", test16, 4000],
    ["99", test99, 2600]
];

var runningAll = false;
ui.run_all.click(function () {
    if (runningAll) {
        setStatus("正在自动测试中，请等本轮结束");
        return;
    }
    runningAll = true;
    setStatus("开始自动测试全部，约 1 分钟");
    threads.start(function () {
        for (var i = 0; i < allTests.length; i++) {
            var item = allTests[i];
            setStatus("自动 " + (i + 1) + "/" + allTests.length + "：测试 " + item[0]);
            item[1]();
            sleep(item[2]);
        }
        runningAll = false;
        setStatus("全部测试结束");
    });
});
