import { setGlobal, setGlobalAnd$ } from './utils'
import _shizuku from './shizuku'
import _base64 from './base64'
import _shell from './shell'
import './inline_modules/files'
import media, { Media } from './inline_modules/media'
import * as _ui from './ui'
import { proxy as ui_proxy } from './ui'
import _selector from './inline_modules/selector'
import _threads from './therads'
import _floaty from './inline_modules/floaty'
import _images from './images'
import _automator from './inline_modules/automator'
import _app from './app'
import _storages from './inline_modules/storages'
import _engines from './inline_modules/engines'
import _http from './http'
import _dialogs from './dialogs'
import _$cypto from './inline_modules/$crypto'
import _$zip from './inline_modules/$zip'
import _events from './inline_modules/events'
import _paddle from './inline_modules/paddle'
import _plugins from './inline_modules/plugins'
import _sensors from './inline_modules/sensors'
import * as _web from './inline_modules/web'


declare global {
    var shizuku: typeof _shizuku
    var media: Media
    const ui: Omit<typeof _ui, 'proxy'>
    var shell: typeof _shell
    var base64: typeof _base64
    var selector: typeof _selector
    var threads: typeof _threads
    var floaty: typeof _floaty
    var images: typeof _images
    var automator: typeof _automator
    var app: typeof _app
    var storages: typeof _storages
    var engines: typeof _engines
    var http: typeof _http
    var dialogs: typeof _dialogs
    var $crypto: typeof _$cypto
    var $zip: typeof _$zip
    var events: typeof _events
    var paddle: typeof _paddle
    var plugins: typeof _plugins
    var sensors: typeof _sensors
    var web: typeof _web
}

setGlobalAnd$({
    selector: _selector,
    ui: ui_proxy,
    base64: _base64,
    shell: _shell,
    media: media,
    threads: _threads,
    floaty: _floaty,
    images: _images,
    automator: _automator,
    app: _app,
    storages: _storages,
    engines: _engines,
    http: _http,
    dialogs: _dialogs,
    events: _events,
    paddle: _paddle,
    plugins: _plugins,
    sensors: _sensors,
    web: _web
})

setGlobal('shizuku', _shizuku)
setGlobal('$crypto', _$cypto)
setGlobal('$zip', _$zip)

setGlobal('KeyEvent', android.view.KeyEvent);
setGlobal('Paint', android.graphics.Paint);
setGlobal('Canvas', com.stardust.autojs.core.graphics.ScriptCanvas)
setGlobal('Image', com.stardust.autojs.core.image.ImageWrapper)
setGlobal('OkHttpClient', Packages["okhttp3"].OkHttpClient)
setGlobal('Intent', android.content.Intent)

