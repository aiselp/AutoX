var img = images.read("./img.png")
var templ = images.read("./templ.png");

var p = images.matchTemplate(img, templ,{
    transparentMask: true
});

if(p.first()){
    log(p);
}else{
    throw Error("没找到");
}

var p2 = images.matchTemplate(img, templ,{
    transparentMask: false
});

if(p2.first()){
    throw Error("找到了");
}

img.recycle()
templ.recycle()