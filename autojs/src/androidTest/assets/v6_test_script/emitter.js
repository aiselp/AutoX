
let mainTh = threads.currentThread()

let e = events.emitter(mainTh)
let t1, t2
e.on('t1', () => {
    t1 = mainTh === threads.currentThread()
})


let th2 = threads.start(() => {
    e.emit('t1')
    let e2 = events.emitter(threads.currentThread())
    e2.on('t2', () => {
        t2 = th2 === threads.currentThread()
    })
    threads.start(() => {
        e2.emit('t2')
    })
    setTimeout(() => { }, 500)
})

setTimeout(() => {
    console.assert(t1 === true)
    console.assert(t2 === true)
}, 1000)