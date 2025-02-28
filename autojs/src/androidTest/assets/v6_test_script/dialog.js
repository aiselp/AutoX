
log("已弹出 alert 对话框")
alert("alert")

log("已弹出 confirm 对话框")
let r = confirm("confirm dialog","点击按钮继续")
log("return: " + r)
sleep(2000)

log("已弹出 rawInput 对话框")
var body = rawInput("rawInput测试", "请输入任意内容");
log("return: " + body)
sleep(2000)

log("已弹出 select 对话框")
var options = ["选项A", "选项B", "选项C", "选项D"];
var i = dialogs.select("请选择一个选项", options);
log("return: " + i)
sleep(2000)


log("测试完成")
sleep(1000)