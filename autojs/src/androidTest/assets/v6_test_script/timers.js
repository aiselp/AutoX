

var a = 0

let id = setInterval(() => {
    a++;
    if (a === 5) {
        clearInterval(id);
        log("Timer stopped");
    }
}, 1000)

let b = null

let bid = setTimeout(() => {
    b = true
}, 3000)
setTimeout(() => {
    clearTimeout(bid)
}, 2000)


let c = null

setTimeout(() => {
    c = 800
}, 3000)
