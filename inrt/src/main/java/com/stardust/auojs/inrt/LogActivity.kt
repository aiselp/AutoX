package com.stardust.auojs.inrt

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aiselp.autox.ui.log.ConsoleLogActions
import com.aiselp.autox.ui.log.ConsoleLogController
import com.aiselp.autox.ui.log.ConsoleSelectionTopBar
import com.aiselp.autox.ui.material3.theme.AppTheme
import com.stardust.app.GlobalAppContext
import com.stardust.auojs.inrt.autojs.AutoJs
import com.stardust.auojs.inrt.launch.GlobalProjectLauncher
import com.stardust.autojs.core.console.ConsoleView
import org.autojs.autoxjs.inrt.R


class LogActivity : AppCompatActivity() {
    private lateinit var consoleView: ConsoleView
    private lateinit var logController: ConsoleLogController
    private val consoleImpl by lazy { AutoJs.instance.globalConsole }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consoleView = ConsoleView(this)
        consoleView.setConsole(consoleImpl)
        consoleView.findViewById<View>(R.id.input_container).visibility = View.GONE
        logController = ConsoleLogController(this, consoleImpl, consoleView)
        setContent {
            AppTheme {
                BackHandler(enabled = logController.selectionMode) {
                    logController.exitSelectionMode()
                }
                Scaffold(
                    topBar = {
                        if (logController.selectionMode) {
                            ConsoleSelectionTopBar(logController)
                        } else {
                            TopAppBar(
                                title = { Text(text = GlobalAppContext.appName) },
                                actions = { OptionsMenu() })
                        }
                    },
                    floatingActionButton = {
                        if (!logController.selectionMode) {
                            FloatingActionButton(
                                onClick = { consoleImpl.clear() },
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        HorizontalDivider()
                        AndroidView(factory = { consoleView }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        if (intent.getBooleanExtra(EXTRA_LAUNCH_SCRIPT, false)) {
            GlobalProjectLauncher.launch(this)
        }
    }

    @Composable
    private fun OptionsMenu() {
        Row {
            IconButton(onClick = {
                GlobalProjectLauncher.stop()
                GlobalProjectLauncher.launch(this@LogActivity)
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_rerun),
                    contentDescription = stringResource(R.string.text_rerun)
                )
            }
            IconButton(onClick = {
                GlobalProjectLauncher.stop()
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_stop),
                    contentDescription = stringResource(R.string.text_stop_run)
                )
            }
            IconButton(onClick = { consoleImpl.clear() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete1),
                    contentDescription = stringResource(R.string.text_clear_logcat)
                )
            }
            IconButton(onClick = {
                startActivity(Intent(this@LogActivity, SettingsActivity::class.java))
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.text_settings)
                )
            }
            // 顶栏上图标已经不少，复制全部放进更多菜单里
            ConsoleLogActions(logController, showCopyAllButton = false)
        }
    }

    companion object {
        const val EXTRA_LAUNCH_SCRIPT = "launch_script"
    }
}
