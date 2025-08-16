package org.autojs.autojs.ui.main

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DrawerState
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.viewpager2.widget.ViewPager2
import com.aiselp.autox.ui.material3.BottomBar
import com.aiselp.autox.ui.material3.DrawerPage
import com.aiselp.autox.ui.material3.MainTopAppBar
import com.aiselp.autox.ui.material3.components.DialogController
import com.aiselp.autox.ui.material3.components.UpdateDialog
import com.aiselp.autox.ui.material3.components.isIgnoreUpdate
import com.aiselp.autox.ui.material3.theme.AppTheme
import com.stardust.autojs.IndependentScriptService
import com.stardust.autojs.servicecomponents.ScriptServiceConnection
import com.stardust.autojs.util.PermissionUtil
import com.stardust.autojs.util.StoragePermissionResultContract
import com.stardust.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.autojs.autojs.Pref
import org.autojs.autojs.timing.TimedTaskScheduler
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.main.components.DocumentPageMenuButton
import org.autojs.autojs.ui.main.drawer.DrawerViewModel
import org.autojs.autojs.ui.main.scripts.ScriptListFragment
import org.autojs.autojs.ui.main.task.TaskManagerFragmentKt
import org.autojs.autojs.ui.main.web.EditorAppManager
import org.autojs.autojs.ui.widget.fillMaxSize
import org.autojs.autoxjs.R

data class BottomNavigationItem(val icon: Int, val label: String)

class MainActivity : FragmentActivity() {

    private val scriptListFragment by lazy { ScriptListFragment() }
    private val taskManagerFragment by lazy { TaskManagerFragmentKt() }
    private val webViewFragment by lazy { EditorAppManager() }
    private var drawerState: DrawerState? = null
    private val viewPager: ViewPager2 by lazy { ViewPager2(this) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        Log.i("MainActivity", "Pid: ${Process.myPid()}")
        ScriptServiceConnection.GlobalConnection.bind(application)

        if (Pref.isForegroundServiceEnabled()) {
            IndependentScriptService.startForeground(this)
        } else {
            IndependentScriptService.stopForeground(this)
        }

        if (Pref.isFloatingMenuShown()) {
            if (!FloatyWindowManger.showCircularMenu()) Pref.setFloatingMenuShown(false)
        } else {
            FloatyWindowManger.hideCircularMenu()
        }

        setContent {
            val scope = rememberCoroutineScope()
            var lastBackPressedTime = remember { 0L }
            BackHandler {
                if (drawerState?.isOpen == true) {
                    scope.launch(Dispatchers.Main) { drawerState?.close() }
                    return@BackHandler
                }
                if (viewPager.currentItem == 0 && scriptListFragment.onBackPressed()) {
                    return@BackHandler
                }
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressedTime > 2000) {
                    lastBackPressedTime = currentTime
                    scope.launch {
                        getString(R.string.text_press_again_to_exit).toast(this@MainActivity)
                    }
                } else finish()
            }


            RequestExternalStoragePermissions {
                if (it) {
                    scriptListFragment.explorerView.onRefresh()
                    toast(this@MainActivity, "授权成功")
                }
            }
            AppTheme {
                MainPage(
                    activity = this,
                    scriptListFragment = scriptListFragment,
                    taskManagerFragment = taskManagerFragment,
                    webViewFragment = webViewFragment,
                    onDrawerState = {
                        this.drawerState = it
                    },
                    viewPager = viewPager
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TimedTaskScheduler.ensureCheckTaskWorks(application)
    }
}

@Composable
fun MainPage(
    activity: FragmentActivity,
    scriptListFragment: ScriptListFragment,
    taskManagerFragment: TaskManagerFragmentKt,
    webViewFragment: EditorAppManager,
    onDrawerState: (DrawerState) -> Unit,
    viewPager: ViewPager2
) {
    val context = LocalContext.current
    val scaffoldState = rememberScaffoldState()
    onDrawerState(scaffoldState.drawerState)
    val scope = rememberCoroutineScope()

    val bottomBarItems = remember {
        getBottomItems(context)
    }
    var currentPage by remember { mutableIntStateOf(0) }


    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        scaffoldState = scaffoldState,
        drawerGesturesEnabled = scaffoldState.drawerState.isOpen,
        topBar = {
            MainTopAppBar(
                openMenuRequest = { scope.launch { scaffoldState.drawerState.open() } }
            ) {
                if (currentPage == 2)
                    DocumentPageMenuButton { webViewFragment.swipeRefreshWebView.webView }
            }
        },
        bottomBar = {
            BottomBar(bottomBarItems, currentPage, onSelectedChange = { currentPage = it })
        },
        drawerContent = { DrawerPage() },
    ) {
        val model: DrawerViewModel = viewModel()
        val dialogController = remember { DialogController() }
        dialogController.UpdateDialog(autoUpdate = true)
        LaunchedEffect(Unit) {
            model.checkUpdate(
                onUpdate = {
                    val name = model.githubReleaseInfo?.name
                    scope.launch {
                        if (name != null && !isIgnoreUpdate(context, name)) {
                            dialogController.show()
                        }
                    }
                }, toast = false
            )
        }

        AndroidView(
            modifier = Modifier
                .padding(it),
            factory = {
                viewPager.apply {
                    fillMaxSize()
                    adapter = ViewPager2Adapter(
                        activity,
                        scriptListFragment,
                        taskManagerFragment,
                        webViewFragment
                    )
                    isUserInputEnabled = false
                    ViewCompat.setNestedScrollingEnabled(this, true)
                }
            },
            update = { viewPager0 ->
                viewPager0.currentItem = currentPage
            }
        )
    }
}

@Composable
fun RequestExternalStoragePermissions(onPermissionsResult: (allAllow: Boolean) -> Unit) {
    if (PermissionUtil.checkStoragePermission()) {
        return
    }
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val launcher =
            rememberLauncherForActivityResult(contract = StoragePermissionResultContract()) {
                onPermissionsResult(it)
            }
        LaunchedEffect(Unit) {
            delay(100)
            PermissionUtil.showPermissionDialog(context) { launcher.launch(Unit) }
        }
    } else {
        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { it ->
                onPermissionsResult(it.all { it.value })
            }
        LaunchedEffect(Unit) {
            delay(100)
            PermissionUtil.showPermissionDialog(context) {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }
}

private fun getBottomItems(context: Context) = mutableStateListOf(
    BottomNavigationItem(
        R.drawable.ic_home,
        context.getString(R.string.text_home)
    ),
    BottomNavigationItem(
        R.drawable.ic_manage,
        context.getString(R.string.text_management)
    ),
    BottomNavigationItem(
        R.drawable.ic_web,
        context.getString(R.string.text_document)
    )
)


