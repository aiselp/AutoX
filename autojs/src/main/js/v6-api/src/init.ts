import { setGlobal, setGlobalAnd$ } from './utils'
import shizuku from './shizuku'
import base64 from './base64'
import shell from './shell'
import './inline_modules/files'
import media, { Media } from './inline_modules/media'

declare global {
    var shizuku: (cmd: string) => Autox.ShellResult
    var media: Media
}
setGlobal('shizuku', shizuku)
setGlobalAnd$('base64', base64)
setGlobalAnd$('shell', shell)
setGlobalAnd$("media", media)

setGlobal('KeyEvent', android.view.KeyEvent);
setGlobal('Paint', android.graphics.Paint);
setGlobal('Canvas', com.stardust.autojs.core.graphics.ScriptCanvas)
setGlobal('Image', com.stardust.autojs.core.image.ImageWrapper)
setGlobal('OkHttpClient', Packages["okhttp3"].OkHttpClient)
setGlobal('Intent', android.content.Intent)

export { shizuku }