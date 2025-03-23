package com.stardust.autojs.core.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import com.stardust.autojs.core.web.JsBridge
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
open class JsWebView : WebView {
    //val events = EventEmitter()
    val jsBridge = JsBridge(this)

    init {
        val settings = settings
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.loadWithOverviewMode = true
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.domStorageEnabled = true
        settings.displayZoomControls = false
        webViewClient = JsBridge.SuperWebViewClient()
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun loadLocalFile(path: String) {
        val webViewClient = webViewClient
        if (webViewClient !is JsBridge.SuperWebViewClient) {
            throw Error("WebViewClient must be JsBridge.SuperWebViewClient")
        }
        val dir = File(path)
        if (dir.isDirectory) {
            webViewClient.setFileAsset("/", dir)
            loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}")
            return
        }
        if (dir.isFile) {
            webViewClient.setFileAsset("/", dir.parentFile!!)
            loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}/${dir.name}")
            return
        }
        throw Error("Invalid path: $path")
    }

    fun injectionJsBridge() {
        JsBridge.injectionJsBridge(this)
    }
}
