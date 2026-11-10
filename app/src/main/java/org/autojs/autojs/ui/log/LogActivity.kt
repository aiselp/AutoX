package org.autojs.autojs.ui.log

import android.os.Bundle
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aiselp.autox.ui.log.ConsoleLogActions
import com.aiselp.autox.ui.log.ConsoleLogController
import com.aiselp.autox.ui.log.ConsoleSelectionTopBar
import com.aiselp.autox.ui.material3.components.BackTopAppBar
import com.aiselp.autox.ui.material3.theme.AppTheme
import com.stardust.autojs.core.console.ConsoleImpl
import com.stardust.autojs.core.console.ConsoleView
import com.stardust.autojs.servicecomponents.ScriptServiceConnection
import org.autojs.autoxjs.R


open class LogActivity : AppCompatActivity() {
    private lateinit var consoleView: ConsoleView
    private lateinit var logController: ConsoleLogController
    private val consoleImpl: ConsoleImpl by lazy { ScriptServiceConnection.GlobalConnection.consoleImpl }

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
                            BackTopAppBar(
                                title = stringResource(R.string.text_log),
                                actions = { ConsoleLogActions(logController) }
                            )
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
                                    contentDescription = stringResource(R.string.text_clear)
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

    }

}
