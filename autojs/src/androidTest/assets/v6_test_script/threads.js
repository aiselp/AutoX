
let a;
threads.start(function(){
    a = 4
});
sleep(300)
console.assert(a === 4)

let b = false;
let th = threads.start(function(){
    sleep(1000)
    b = true
});
sleep(200)
console.assert(th.isAlive() === true)
th.interrupt()
th.join()
console.assert(b === false)

let c;
let th2 = threads.start(function(){
    th2.setTimeout(()=>{
        c = "abc"
    }, 2000)
});

th2.join()
console.assert(c === "abc")