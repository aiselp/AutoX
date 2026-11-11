package com.aiselp.autojs.codeeditor.web

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import com.aiselp.autojs.codeeditor.EditActivity
import com.aiselp.autojs.codeeditor.dialogs.AssetDownloadDialog
import com.aiselp.autojs.codeeditor.dialogs.LoadDialog
import com.aiselp.autojs.codeeditor.plugins.AppController
import com.aiselp.autojs.codeeditor.plugins.FileSystem
import com.stardust.io.Zip
import com.stardust.pio.PFiles
import com.stardust.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorAppManager(val context: Activity, val editorModel: EditActivity.EditorModel) {
    companion object {
        const val TAG = "EditorAppManager"
        const val WEB_DIST_PATH = "codeeditor/dist.zip"
        const val VERSION_FILE = "codeeditor/version.txt"
        const val WEB_PUBLIC_PATH = "editorWeb/"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    val webView = createWebView(context)
    private val jsBridge = JsBridge(webView)
    private val pluginManager = PluginManager(jsBridge, coroutineScope)
    var openedFile: String? = null

    private val call = (context as ComponentActivity).registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { data -> importAsset(data) }
    val loadDialog = LoadDialog()
    val assetDownloadDialog = object : AssetDownloadDialog() {
        override fun onNeutralClick() {
            call.launch(arrayOf("application/zip"))
        }
    }

    init {
        webView.webViewClient =
            FileAssetWebViewClient(File(context.filesDir, "$WEB_PUBLIC_PATH/dist"))
        installPlugin()
        jsBridge.registerHandler("app.init", JsBridge.Handle { _, _ ->
            pluginManager.onWebInit()
            val file = openedFile
            if (file != null) {
                openFile(file)
            }
        })
        coroutineScope.launch {
            if (checkAssets()) {
                withContext(Dispatchers.Main) { loadDialog.show() }
                initWebResources()
                launchPage()
            } else {
                if (File(File(context.filesDir, WEB_PUBLIC_PATH), "dist").isDirectory) {
                    withContext(Dispatchers.Main) { loadDialog.show() }
                    launchPage()
                } else
                    withContext(Dispatchers.Main) {
                        assetDownloadDialog.show()
                    }
            }
        }
    }

    private fun installPlugin() {
        pluginManager.registerPlugin(FileSystem.TAG, FileSystem(context))
        pluginManager.registerPlugin(
            AppController.TAG,
            AppController(context, editorModel, coroutineScope)
        )
    }

    private fun importAsset(data: Uri?) {
        if (data == null) return
        val stream = context.contentResolver.openInputStream(data)
        if (stream == null) {
            toast(context, "导入失败")
            return
        }
        assetDownloadDialog.dismiss()
        loadDialog.show()
        coroutineScope.launch(Dispatchers.IO) {
            loadDialog.setContent("正在安装")
            Zip.unzip(stream, File(context.filesDir, WEB_PUBLIC_PATH))
            launchPage()
        }
    }

    private suspend fun launchPage() {
        loadDialog.setContent("启动中")
        delay(500)
        withContext(Dispatchers.Main) {
//                webView.loadUrl("http://192.168.10.10:8010")
            webView.loadUrl("https://${WebViewAssetLoader.DEFAULT_DOMAIN}")
            loadDialog.dismiss()
        }
    }

    private suspend fun initWebResources() {
        val webDir = File(context.filesDir, WEB_PUBLIC_PATH)
        val versionFile = File(webDir, "version.txt")
        if (isUpdate(versionFile)) {
            Log.i(TAG, "skip initWebResources")
            return
        }
        if (PFiles.deleteRecursively(webDir)) {
            loadDialog.setContent("正在更新")
        } else loadDialog.setContent("正在安装")
        Log.i(TAG, "initWebResources")
        webDir.mkdirs()
        withContext(Dispatchers.IO) {
            Zip.unzip(context.assets.open(WEB_DIST_PATH), webDir)
            val versionCode = String(context.assets.open(VERSION_FILE).use { it.readBytes() })
            versionFile.writeText(versionCode)
        }
    }

    private fun checkAssets(): Boolean {
        try {
            context.assets.open(WEB_DIST_PATH).close()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun isUpdate(file: File): Boolean {
        if (!file.isFile) return false
        return try {
            val text = file.readText()
            val versionCode = context.assets.open(VERSION_FILE).use { it.readBytes() }
            String(versionCode) == text
        } catch (_: Exception) {
            false
        }
    }

    fun destroy() {
        webView.destroy()
        coroutineScope.cancel()
    }

    fun openFile(path: String) {
        jsBridge.callHandler("app.openFile", FileSystem.toWebPath(File(path)), null)
    }

    fun onKeyboardDidShow() {
        Log.d(TAG, "onKeyboardDidShow")
        jsBridge.callHandler("app.onKeyboardDidShow", null, null)
    }

    fun onKeyboardDidHide() {
        Log.d(TAG, "onKeyboardDidHide")
        jsBridge.callHandler("app.onKeyboardDidHide", null, null)
    }

    fun onBackButton() {
        Log.d(TAG, "onBackButton")
        jsBridge.callHandler("app.onBackButton", null, null)
    }

    private fun createWebView(context: Context): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
}