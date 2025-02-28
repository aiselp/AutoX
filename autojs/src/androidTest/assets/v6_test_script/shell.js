

let r = shell("echo f4")
console.assert(r.code === 0, r.error)
console.assert(r.result === "f4")

let a = "";
let sh = new Shell()
sh.setCallback({
    onOutput:(out)=>{
        a += out
    }
})

sh.exec("echo abc")
sh.execAndWaitFor("echo 123")
sh.exitAndWaitFor()

console.assert(a === "abc123", "shell out error: " + a)
