package com.aiselp.autojs.codeeditor

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiselp.autojs.codeeditor.web.EditorAppManager
import com.aiselp.autox.ui.material3.theme.AppTheme
import com.stardust.autojs.core.console.ConsoleImpl
import com.stardust.autojs.core.console.ConsoleView
import com.stardust.autojs.core.console.LogEntry
import com.stardust.autojs.servicecomponents.BinderConsoleListener
import com.stardust.autojs.servicecomponents.BinderScriptListener
import com.stardust.autojs.servicecomponents.EngineController
import com.stardust.autojs.servicecomponents.TaskInfo
import com.stardust.util.UiHandler
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


class EditActivity : AppCompatActivity() {
    private lateinit var editorAppManager: EditorAppManager
    private val consoleImpl = ConsoleImpl(UiHandler(this))
    private var logDisposable: Disposable? = null
    private val consoleView by lazy {
        ConsoleView(this).apply {
            findViewById<View>(R.id.input_container).visibility = View.GONE
            setConsole(consoleImpl)
            logDisposable = EngineController.registerGlobalConsoleListener(object :
                BinderConsoleListener {
                override fun onPrintln(log: LogEntry) {
                    consoleImpl.println(log.level, log.content)
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val editorModel by viewModels<EditorModel>()
        editorAppManager = EditorAppManager(this, editorModel)
        editorAppManager.openedFile = intent.getStringExtra(EXTRA_PATH)

        setContent {
            val rootView = LocalView.current
            LaunchedEffect(Unit) {
                setKeyboardEvent(rootView)
            }
            AppTheme {
                editorAppManager.loadDialog.Dialog()
                editorAppManager.assetDownloadDialog.Dialog()
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(factory = {
                        editorAppManager.webView
                    })
                    LogSheet()
                }
            }
        }

        onBackPressedDispatcher.addCallback {
            moveTaskToBack(false)
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "EditActivity onDestroy")
        super.onDestroy()
        logDisposable?.dispose()
        editorAppManager.destroy()
    }

    private fun setKeyboardEvent(rootView: View) {
        val rootHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            metrics.bounds.bottom
        } else {
            windowManager.defaultDisplay.height
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect().also {
                rootView.getWindowVisibleDisplayFrame(it)
            }
            val resultBottom = rect.bottom
            if (rootHeight - resultBottom > 200) {
                editorAppManager.onKeyboardDidShow()
            } else {
                editorAppManager.onKeyboardDidHide()
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path != null) {
            editorAppManager.openFile(path)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LogSheet() {
        val model = viewModel(EditorModel::class)
        if (!model.showLog) return
        val context = LocalContext.current
        ModalBottomSheet(
            onDismissRequest = { model.showLog = false },
            sheetState = rememberModalBottomSheetState(false),
            dragHandle = null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.lastScriptFile?.name ?: "",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = {
                    if (!model.running) {
                        model.rerun()
                    }
                }, enabled = model.lastScriptFile != null) {
                    if (model.running) {
                        val infiniteTransition = rememberInfiniteTransition(label = "")
                        val r by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                            ), label = "running"
                        )
                        Icon(
                            modifier = Modifier.rotate(r),
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = Color(0xFF161145)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF3F51B5)
                        )
                    }
                }

                IconButton(onClick = { consoleImpl.clear() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color(0xFF462566)
                    )
                }

                IconButton(onClick = { model.openLogActivity(context) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_logcat),
                        contentDescription = null,
                        tint = Color(0xFF155465)
                    )
                }

            }
            Column(
                modifier = Modifier
                    .height(500.dp)
                    .fillMaxWidth()
            ) {
                AndroidView(factory = { consoleView }, modifier = Modifier.fillMaxSize())
            }
        }
    }

    class EditorModel : ViewModel() {
        var showLog by mutableStateOf(false)
        var running by mutableStateOf(false)
        var lastScriptFile: File? by mutableStateOf(null)

        val listener = object : BinderScriptListener {
            override fun onStart(taskInfo: TaskInfo) {
                viewModelScope.launch(Dispatchers.Main) { running = true }
            }

            override fun onSuccess(taskInfo: TaskInfo) {
                viewModelScope.launch(Dispatchers.Main) { running = false }
            }

            override fun onException(taskInfo: TaskInfo, e: Throwable) {
                viewModelScope.launch(Dispatchers.Main) { running = false }
            }

        }

        fun openLogActivity(context: Context) {
            val clazz = Class.forName("org.autojs.autojs.ui.log.LogActivityKt")
            context.startActivity(Intent(context, clazz))
        }

        fun rerun() {
            val file = lastScriptFile ?: return
            running = true
            EngineController.runScript(file, listener)
        }
    }

    companion object {
        private const val EXTRA_PATH = "path";
        const val TAG = "EditActivity"
        fun editFile(context: Context, path: File) {
            val intent = Intent(context, EditActivity::class.java)
                .setFlags(FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_PATH, path.path)
            context.startActivity(intent)
        }
    }
}