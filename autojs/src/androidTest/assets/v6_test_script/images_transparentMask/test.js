var img = images.read("./img.png")
var templ = images.read("./templ.png");

var p = images.findImage(img, templ,{
    transparentMask: true
});

if(p){
    log(p);
}else{
    throw Error("没找到");
}

var p2 = images.findImage(img, templ,{
    transparentMask: false
});

if(p2){
    throw Error("找到了");
}

img.recycle()
templ.recycle()

var img2 = images.read("./img2.jpg")
var templ2 = images.read("./templ2.png");

var s = images.matchTemplate(img2, templ2,{
    transparentMask: true
});

if (s.matches.length  === 5) {
    log(s);
}else {
    throw Error("匹配数量不对");
}

var s2 = images.matchTemplate(img2, templ2,{
    //transparentMask: true
});
if (s2.matches.length  > 0) {
    throw Error("不应该匹配到");
}

img2.recycle()
templ2.recycle()