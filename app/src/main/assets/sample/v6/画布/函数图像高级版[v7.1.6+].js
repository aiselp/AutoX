"ui";
//ui布局为一块画布和一些函数调整控件
ui.layout(
    <vertical>
        <linear>
            <input id="fx" textSize="16sp" text="x*x+3*x-4" layout_weight="1" />
            <button id="ok" w="50dp" />
        </linear>
        <linear>
            <button id="left" text="←" layout_weight="1" />
            <button id="right" text="→" layout_weight="1" />
            <button id="up" text="↓" layout_weight="1" />
            <button id="down" text="↑" layout_weight="1" />
            <button id="zoom_in" text="+" layout_weight="1" />
            <button id="zoom_out" text="-" layout_weight="1" />
        </linear>
        <canvas id="board" w="*" h="*" />
    </vertical>
);

function updateCanvas() {
    ui.board.updateCanvas()
}

//函数表达式
var f = "x*x+3*x-4";
//绘制区间
var minX = -5;
var maxX = 5;
var minY;
var h = 1;
var w = 1;

//画笔
var paint = new Paint();
paint.setStrokeWidth(2);
function draw(canvas) {
    w = canvas.getWidth();
    h = canvas.getHeight();
    //绘制背景
    canvas.drawARGB(0xff, 0xff, 0xff, 0xff);
    if (minY == undefined) {
        minY = -(maxX - minX) * h / w / 2;
    }
    //计算y轴区间上限
    var maxY = minY + (maxX - minX) * h / w;
    //设置画笔颜色为黑色
    paint.setARGB(0xff, 0, 0, 0)
    //绘制两个坐标轴
    var x0 = parseInt(- minX / (maxX - minX) * w);
    canvas.drawLine(x0, 0, x0, h, paint);
    var y0 = parseInt(h + minY / (maxY - minY) * h);
    canvas.drawLine(0, y0, w, y0, paint);
    //设置画笔颜色为红色
    paint.setARGB(0xff, 0xff, 0, 0)
    //绘制图像
    for (var i = 0; i < w; i++) {
        var x = minX + i / w * (maxX - minX);
        var y = eval(f);
        var j = h - (y - minY) / (maxY - minY) * h;
        canvas.drawPoint(i, j, paint);
    }
}
ui.board.on("draw", draw);

ui.ok.click(() => {
    f = String(ui.fx.text());
    updateCanvas()
});

ui.left.click(() => {
    var d = maxX - minX;
    maxX -= d / 10;
    minX -= d / 10;
    updateCanvas()
});

ui.right.click(() => {
    var d = maxX - minX;
    maxX += d / 10;
    minX += d / 10;
    updateCanvas()
});

ui.up.click(() => {
    var d = maxX - minX;
    minY += d / 8;
    updateCanvas()
});

ui.down.click(() => {
    var d = maxX - minX;
    minY -= d / 8;
    updateCanvas()
});

ui.zoom_in.click(() => {
    var d = maxX - minX;
    var a = (maxX + minX) / 2;
    maxX = a + d;
    minX = a - d;

    minY *= (maxX - minY) / d * h / w;
    updateCanvas()
});

ui.zoom_out.click(() => {
    var d = maxX - minX;
    maxX -= d / 2;
    minX += d / 2;
    updateCanvas()
});
