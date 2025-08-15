
const javaImages = runtime.images;



export function registerAsyncCapture(listener: (image: Autox.Image) => void) {
    const disposable = javaImages.registerAsyncCapture(listener)
    const id = setInterval(() => { }, 1000)

    return function cancel() {
        disposable.dispose();
        clearInterval(id);
    }
}